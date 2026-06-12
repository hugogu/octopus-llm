# Implementation Plan: Protocol, Connection, and Configured Model

**Branch**: `003-protocol-connection-model` | **Date**: 2026-06-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-protocol-connection-model/spec.md`

## Summary

Replace provider-centric configuration with:

1. Static protocol definitions and protocol-keyed adapter registration.
2. User-owned connections containing a validated endpoint and encrypted key.
3. UUID-addressed configured models bound to connections.
4. Immutable response attribution snapshots that survive configuration deletion.

Breaking chat and configuration contracts move to `/api/v2`. Existing V016 data is migrated transactionally rather than discarded. Live provider model discovery is removed; a static catalogue remains optional form-assistance data.

## Technical Context

**Language/Version**: Kotlin 2.0.21 on JVM 21; TypeScript 5 with Node.js 24  
**Primary Dependencies**: Spring Boot 3.3.5 WebFlux, Spring Data JPA, Spring Security, Hypersistence JsonType, Next.js 16.2.7 App Router, React 19, Tailwind CSS 4  
**Storage**: PostgreSQL 16 with Flyway; JSONB for capability overrides and custom parameters  
**Testing**: JUnit 5, Reactor Test, MockK, Spring WebTestClient, Testcontainers PostgreSQL, Vitest, Testing Library, Playwright smoke tests  
**Target Platform**: Docker Compose locally; horizontally scaled Linux containers in production  
**Project Type**: Web application with Kotlin backend and Next.js frontend  
**Performance Goals**: Preserve concurrent provider dispatch and real-time SSE; collection endpoints return at most 100 records per page  
**Constraints**: No key material in responses/logs; SSRF-safe outbound endpoints; immutable sessions; no distributed locks  
**Scale/Scope**: Current user-scoped application; migration must preserve all usable V016 configuration and historical chat data

## Constitution Check

*GATE: Must pass before implementation and be re-checked after design changes.*

| Principle | Status | Evidence |
|---|---|---|
| I. Provider-Agnostic Abstraction | PASS | `ProtocolAdapterRegistry` resolves adapters by protocol; orchestrator does not import or enumerate concrete adapters. |
| II. API-First Design | PASS | All UI behavior uses REST APIs; breaking contracts are introduced under `/api/v2`; list APIs are paginated. |
| III. Concurrent Execution & Streaming | PASS | Existing `Flux.merge` dispatch model remains; SSE events use configured-model UUID attribution. |
| IV. Data Integrity & Immutable Sessions | PASS | V017 is a versioned transactional migration; response snapshots remain after configuration deletion. |
| V. Observability & Analytics | PASS | dispatch and completion logs include protocol, configured-model UUID, model ID, latency, tokens, error code, and anonymized user. |
| VI. Security & User Key Privacy | PASS | AES-256-GCM remains; APIs return only `hasKey`; endpoint validation blocks SSRF classes and redirects are revalidated. |
| VII. Simplicity & Horizontal Scalability | PASS | Static definitions plus one registry; no discovery jobs, distributed locks, or cross-instance state. |

No constitution violations require a Complexity Tracking exception.

## Architecture Decisions

### 1. Protocol registration remains extensible

`LlmAdapter` exposes `protocolId`. Spring injects all adapters into `ProtocolAdapterRegistry`, which validates unique IDs and returns an adapter by protocol. `ConcurrentLlmOrchestrator` depends only on the registry.

Adding a protocol requires:

- One adapter implementation.
- One `ProtocolDefinition` configuration entry.
- Optional catalogue entries.

It does not require an orchestrator change.

The initial catalogue includes `kimi-k2.5` and `kimi-k2.6`; catalogue maintenance is independent from protocol adapter registration.

### 2. Protocol capabilities are conservative

Protocol definitions describe only behavior safe for all implementations of that wire format. Model-level capabilities such as image input, reasoning, function calling, video, and context length default to disabled/unknown and are enabled through catalogue or user overrides.

Capability override keys use the existing snake_case JSON contract. Unknown keys are preserved in `extras`; known keys are type-validated before persistence.

### 3. Outbound URL policy prevents SSRF

`ConnectionEndpointPolicy` parses and normalizes URLs using `URI`:

- Production accepts HTTPS only.
- Userinfo and fragments are rejected.
- Hostnames are resolved before save and before each request.
- All resolved addresses must be public unicast addresses.
- Loopback, site-local/private, link-local, multicast, unspecified, carrier-grade NAT, benchmarking, documentation, and cloud metadata ranges are rejected for IPv4 and IPv6.
- Redirect following is disabled by default. If enabled for a protocol, each redirect target is validated again.
- Optional local HTTP is controlled by an explicit development property and limited to loopback hosts.

Validation is defense in depth; deployment should also restrict backend egress where supported.

### 4. API versioning

Affected resources are introduced under:

- `/api/v2/protocols`
- `/api/v2/catalogue`
- `/api/v2/connections`
- `/api/v2/configured-models`
- `/api/v2/chat/sessions`

The v2 turn request uses `selectedConfiguredModelIds`. SSE events add `configuredModelId` and retain `modelId` as the literal provider model ID. The release is a coordinated major-version cutover for affected v1 model, configuration, and chat endpoints; the frontend and backend are deployed together. Unaffected v1 authentication endpoints may remain available.

### 5. Key privacy and rotation

Connection responses expose `hasKey: true`; they never expose masked prefixes/suffixes, encrypted bytes, IVs, or fingerprints derived from the key.

`PUT /api/v2/connections/{id}/key` atomically encrypts and replaces a key while preserving the connection and configured models. Requests and errors never echo the submitted key.

### 6. Pagination

Collection endpoints accept `page` and `size` with:

- `page >= 0`
- default `size = 25`
- maximum `size = 100`
- stable ordering by `sortOrder, createdAt, id` for configured models and `createdAt, id` for other resources
- response envelope `{items, page, size, totalElements, totalPages}`

### 7. Immutable response identity

Configuration UUID is operational identity; model ID is provider input. Each persisted response snapshots:

- `configured_model_id` as a UUID value without a destructive FK
- `model_id`
- `model_display_name`
- `protocol`
- `connection_label`

The uniqueness constraint becomes `(turn_id, configured_model_id)`. Deleting a connection/model does not alter historical snapshots.

`chat_turns` gains `selected_configured_model_ids UUID[]` while retaining `selected_model_ids TEXT[]` as historical model-ID snapshots.

### 8. Ownership integrity

`connections` has `UNIQUE(user_id, id)`. `configured_models` stores `user_id` and uses a composite FK `(user_id, connection_id) -> connections(user_id, id)`, preventing cross-owner rows at the database layer.

Application queries use owner-scoped repository methods rather than loading by ID and checking afterward. Foreign resources return 404 to avoid existence disclosure.

### 9. Transactional V017 migration

V017 creates new structures, migrates data, validates counts, updates historical response attribution columns, and only then removes obsolete tables/constraints.

Provider-to-protocol mapping:

- `openai`, `moonshot`, `deepseek`, `zhipu`, `kimi` -> `openai-compatible`
- `anthropic` -> `anthropic`
- `minimax` -> `minimax`

Effective base URL uses `provider_api_keys.base_url` when present, otherwise the existing provider default encoded in the migration.

Usable `user_model_configs` rows with a key become configured models. Rows without a key are counted in a migration audit table and skipped because they cannot dispatch requests. Existing encrypted key and IV bytes are copied unchanged.

Historical responses are backfilled with model and protocol snapshots before `model_definitions` is removed. The migration test starts from V016 and verifies rollback behavior on failed validation.

## REST Contract Summary

### Protocols and catalogue

- `GET /api/v2/protocols?page=0&size=25`
- `GET /api/v2/catalogue?protocol=openai-compatible&page=0&size=25`

Both are public and paginated.

### Connections

- `GET /api/v2/connections?page=0&size=25`
- `POST /api/v2/connections`
- `PATCH /api/v2/connections/{id}`
- `PUT /api/v2/connections/{id}/key`
- `DELETE /api/v2/connections/{id}`

Connection response fields: `id`, `protocol`, `label`, `baseUrl`, `hasKey`, `modelCount`, `createdAt`, `updatedAt`.

### Configured models

- `GET /api/v2/configured-models?enabled=true&page=0&size=25`
- `POST /api/v2/configured-models`
- `PATCH /api/v2/configured-models/{id}`
- `DELETE /api/v2/configured-models/{id}`

Patch supports `displayName`, `isEnabled`, `capabilityOverrides`, `customParams`, and `sortOrder`. Null values remove individual override/custom-parameter keys; omitted fields remain unchanged.

### Chat

`POST /api/v2/chat/sessions/{sessionId}/turns` accepts:

```json
{
  "promptText": "Compare these approaches",
  "selectedConfiguredModelIds": ["uuid-1", "uuid-2"],
  "clientRequestId": "optional-idempotency-key",
  "attachments": []
}
```

Model-specific SSE events contain both:

```json
{
  "event": "token",
  "configuredModelId": "uuid-1",
  "modelId": "gpt-4o",
  "delta": "..."
}
```

## Project Structure

### Documentation

```text
specs/003-protocol-connection-model/
├── spec.md
├── design.md
├── plan.md
├── data-model.md
├── contracts/
│   └── api-v2.md
├── quickstart.md
└── tasks.md
```

### Source Code

```text
backend/src/main/kotlin/com/octopusllm/
├── model/
│   ├── ProtocolDefinition.kt
│   └── ModelCatalogue.kt
├── connection/
│   ├── Connection.kt
│   ├── ConfiguredModel.kt
│   ├── ConnectionEndpointPolicy.kt
│   ├── ConnectionService.kt
│   └── ConnectionControllerV2.kt
├── llm/
│   ├── LlmAdapter.kt
│   ├── ProtocolAdapterRegistry.kt
│   └── ConcurrentLlmOrchestrator.kt
└── chat/
    ├── ChatControllerV2.kt
    ├── ChatService.kt
    └── ProviderResponse.kt

backend/src/test/kotlin/com/octopusllm/
├── connection/
├── chat/
├── llm/
└── migration/

frontend/src/
├── app/(app)/settings/models/
├── app/(app)/chat/
├── components/settings/connections/
├── components/chat/
└── lib/api/
```

**Structure Decision**: Preserve the existing two-project repository and introduce feature packages inside current backend/frontend source roots.

## Verification Gates

- V016-to-V017 Testcontainers migration test passes, including rollback case.
- Backend `./gradlew build` passes.
- Frontend `npx tsc --noEmit`, Vitest, and production build pass using Node.js 24.
- Contract tests cover pagination, owner isolation, key non-disclosure, key rotation, standard errors, and v2 DTOs.
- Security tests cover private IPs, IPv6, metadata targets, DNS resolution, redirects, and development-only HTTP.
- Chat integration test covers duplicate model IDs across two connections and immutable response snapshots after deletion.
- Docker Compose smoke test covers add connection -> add model -> chat -> delete configuration -> reload historical session.

## Complexity Tracking

No exceptions. The endpoint policy and v2 controllers are required security/versioning boundaries rather than additional service layers.
