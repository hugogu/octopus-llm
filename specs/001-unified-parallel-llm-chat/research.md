# Research: Unified Parallel LLM Chat

**Phase**: 0 — Research
**Feature**: 001-unified-parallel-llm-chat
**Date**: 2026-06-09

---

## Decision 1: LLM Provider SDK Strategy

**Decision**: Use provider SDKs where available; fall back to OpenAI-compatible HTTP for
providers that expose OpenAI-style APIs; use Spring WebClient for providers with custom
REST APIs.

| Provider | SDK / Approach | Notes |
|----------|---------------|-------|
| OpenAI | `com.openai:openai-java` (official, v2.x) | Supports streaming, async, all models |
| Anthropic Claude | `com.anthropic:anthropic-java` (official) | Supports streaming, multi-modal |
| Moonshot (Kimi) | OpenAI SDK with `baseUrl = https://api.moonshot.cn/v1` | 100% OpenAI-compatible |
| DeepSeek | OpenAI SDK with `baseUrl = https://api.deepseek.com` | 100% OpenAI-compatible |
| GLM (Zhipu AI) | OpenAI SDK with `baseUrl = https://open.bigmodel.cn/api/paas/v4` | Current chat-completions flow works through the OpenAI-compatible API |
| MiniMax | Spring `WebClient` + custom REST client | No standard Java SDK |

**Rationale**: Re-using the OpenAI SDK for compatible providers eliminates duplicate HTTP
client code. Only Anthropic and MiniMax require dedicated non-OpenAI-compatible adapters.

**Alternatives considered**:
- LangChain4j: Would unify the adapter layer but adds a heavy framework dependency and lags
  behind provider SDK releases. Rejected in favour of direct SDK usage and a thin `LlmAdapter`
  interface we own.
- Spring AI: Similar concerns — opinionated about message formats and lags provider updates.
  Rejected for the same reason.

---

## Decision 2: Streaming Architecture

**Decision**: The `POST /turns` endpoint returns `Content-Type: text/event-stream` directly.
The backend holds a `Flux<SseEvent>` that merges concurrent provider streams using Reactor's
`Flux.merge()`. Each SSE event carries a `modelId` field so the frontend routes tokens to the
correct panel.

**SSE event types (canonical — all artifacts must use these exact names):**

```
data: {"event":"turn_created","turnId":"<uuid>","sequenceNum":1}
data: {"event":"capability_notice","modelId":"deepseek-chat","notice":"Image input not supported — text only sent"}
data: {"event":"token","modelId":"gpt-4o","delta":"Hello"}
data: {"event":"model_complete","modelId":"gpt-4o","inputTokens":12,"outputTokens":47,"latencyMs":1340}
data: {"event":"model_error","modelId":"moonshot-v1-8k","error":"API key invalid"}
data: {"event":"all_complete"}
```

**Rationale**: A single streaming POST avoids a two-step (create + poll) pattern that requires
either polling or a pub/sub broker. For single-server Docker Compose deployment, an in-process
Reactor `Flux` is sufficient and has no external dependencies.

**Alternatives considered**:
- Two-step (POST returns turnId, then GET /stream): Cleaner REST semantics but requires a
  shared state store (Redis or in-memory map) for multi-request streaming. Deferred to when
  the platform scales beyond single-server.
- WebSocket: Bidirectional, but adds connection management complexity. Rejected for MVP since
  the chat is request-response, not bidirectional.

---

## Decision 3: Concurrent Provider Dispatch

**Decision**: Use Kotlin Coroutines in the service layer (`coroutineScope { async { } }` +
`awaitAll()`); wrap individual provider reactive streams (`Flux`) with Kotlin's
`Flow` interop (`Flow.asPublisher()`). The HTTP layer (Spring WebFlux controller) merges
per-provider `Flux<SseEvent>` instances using `Flux.merge()`.

**Rationale**: Kotlin Coroutines provide readable concurrent code in the service layer while
Reactor `Flux.merge()` provides the non-blocking merge semantics needed for concurrent SSE
streaming.

**JPA/JDBC blocking mitigation**: Spring Data JPA uses blocking JDBC, which must not run on
the WebFlux event-loop thread. All repository calls (reads and writes) MUST be wrapped in
`subscribeOn(Schedulers.boundedElastic())` (Reactor) or `withContext(Dispatchers.IO)` (Kotlin
Coroutines) to offload to a dedicated thread pool. The LLM provider HTTP calls and SSE
event emission remain fully non-blocking. R2DBC is the correct long-term solution but adds
significant complexity for the initial MVP; the bounded-elastic offload is the chosen tradeoff.

**Alternatives considered**:
- `CompletableFuture` + `allOf`: Works but less idiomatic in Kotlin; no structured concurrency.
- `ExecutorService` thread pool: Blocking threads, does not compose with WebFlux.
- R2DBC instead of JPA: Fully reactive DB access, but requires replacing the ORM layer and
  adds migration complexity. Deferred to a future iteration.

---

## Decision 4: API Key Encryption

**Decision**: AES-256-GCM with a 12-byte random IV generated per write. The application-level
master key is sourced from environment variable `ENCRYPTION_MASTER_KEY` (base64-encoded 32
bytes). Encrypted blob and IV are stored separately in `BYTEA` columns in PostgreSQL.

```
encrypt(plaintext, masterKey):
  iv = SecureRandom.generateSeed(12)
  cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(ENCRYPT, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, iv))
  return {iv: iv, ciphertext: cipher.doFinal(plaintext.toByteArray())}
```

**Rationale**: GCM provides authenticated encryption (detects tampering). Per-key IV ensures
two identical keys produce different ciphertexts (prevents frequency analysis). The 128-bit
auth tag catches accidental corruption.

**Alternatives considered**:
- HashiCorp Vault: Adds operational complexity for single-server deployment. Can be added
  later as an upgrade path.
- JPA AttributeConverter with transparent encryption: Convenient but makes the encryption
  logic invisible to code reviewers. Rejected in favour of explicit service-level encryption.

---

## Decision 5: Capability Matrix Storage

**Decision**: JSONB column `capability_matrix` on the `model_definitions` table. Parsed into
a Kotlin data class `CapabilityMatrix` at the service layer. Unknown keys in JSONB are ignored
(future extensibility without schema migration).

**Capability Matrix schema (canonical fields):**

```json
{
  "input_modalities": ["text", "image"],
  "output_modalities": ["text"],
  "context_length_tokens": 128000,
  "supports_streaming": true,
  "supports_function_calling": true,
  "supports_system_prompt": true,
  "supports_video_input": false
}
```

**Rationale**: JSONB gives schema-free extensibility for new capability dimensions without a
migration. Strongly-typed Kotlin data class at the service layer gives compile-time safety for
known dimensions while the `extras: Map<String, Any>` field captures unknowns.

**Alternatives considered**:
- Separate `model_capabilities` rows table: Normalised but verbose for queries and hard to
  add new dimensions without table changes.
- Enum bitmask column: Very fast for simple boolean capabilities but cannot carry numeric
  values (e.g., context length).

---

## Decision 6: Model Catalogue Management

**Decision**: Initial model definitions are seeded via Flyway migration SQL (V009). The
`model_definitions` table is the runtime source of truth. Adding a new model after initial
deployment requires only a SQL INSERT — either via a new Flyway migration file (for tracked
changes) or via `docker compose exec db psql ...` (for hotfix/operational updates). Neither
operation requires rebuilding or redeploying application code.

**This satisfies FR-022** ("updatable without an application code change"): a model can be
added to a running Docker Compose stack by executing a single `INSERT INTO model_definitions`
against the PostgreSQL container. The application reads from the table at runtime and will
serve the new model on the next request without restart (provided the table is consulted at
request time, not cached at startup).

**Rationale**: Migration-driven seeding is consistent with Constitution Principle IV and
provides a full audit trail. Direct SQL for operational updates preserves the "no code change"
requirement while avoiding the overhead of creating a deployment artifact for each new model.

**Alternatives considered**:
- YAML/JSON config file loaded at startup: Bypasses the migration audit trail; also requires
  an app restart to pick up changes. Rejected.
- Admin API for model management: Correct long-term approach but out of scope for this feature.

---

## Resolved Unknowns

All Technical Context items resolved. No NEEDS CLARIFICATION items remain.
