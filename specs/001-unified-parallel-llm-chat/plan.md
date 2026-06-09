# Implementation Plan: Unified Parallel LLM Chat

**Branch**: `001-unified-parallel-llm-chat` | **Date**: 2026-06-09 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-unified-parallel-llm-chat/spec.md`

## Summary

This feature delivers three foundational capabilities: (1) user registration with email
verification and login, (2) LLM model configuration — where users store encrypted API keys
and enable individual models from the platform catalogue — and (3) a parallel chat interface
that dispatches prompts concurrently to all selected models and streams each model's response
in real time via SSE. The LLM abstraction layer treats **models** (not provider brands) as the
primary concept; a Capability Matrix per model governs input/output modality routing. Supported
providers: OpenAI, Anthropic Claude, Moonshot (Kimi), DeepSeek, Zhipu AI (GLM), MiniMax.

## Technical Context

**Language/Runtime**: Kotlin 2.x on JVM, Java 21; TypeScript 5.x with strict mode
**Primary Dependencies**:
- Backend: Spring Boot 3.x (WebFlux), Spring Security 6, Flyway, Spring Data JPA
- LLM SDKs: `openai-java` (OpenAI / Moonshot / DeepSeek), `anthropic-java`,
  `zhipuai-sdk-java-v4`, Spring WebClient (MiniMax)
- Frontend: Next.js 15 (App Router), React 19
**Storage**: PostgreSQL 16; JSONB for Capability Matrix and attachment metadata
**Testing**: JUnit 5 + MockK (backend unit); Testcontainers + PostgreSQL (integration);
Vitest + React Testing Library (frontend)
**Target Platform**: Linux AMD64 (Docker Compose server); ARM64 (local dev)
**Project Type**: Full-stack web application — separate `backend/` and `frontend/` directories
**Performance Goals**: First response token visible < 3 s; parallel dispatch overhead < 200 ms
**Constraints**:
- API keys AES-256-GCM encrypted at rest; never in logs or API responses
- All schema changes via Flyway; frontend consumes REST API only (no DB access)
- Model catalogue updates require only a SQL INSERT — no application code change or rebuild
- JWT auth with `jti` claim; `revoked_tokens` table enables immediate logout
- Spring Data JPA (blocking JDBC) is used; all DB calls MUST run on `Schedulers.boundedElastic()`
  to avoid blocking the WebFlux event loop; LLM calls and SSE emission remain non-blocking
**Scale/Scope**: Single-server Docker Compose; ~100 concurrent users initial target

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Verification |
|-----------|--------|--------------|
| I. Provider-Agnostic Abstraction | ✅ PASS | `LlmAdapter` interface; per-provider adapters; new providers require only a new adapter class and a catalogue seed migration |
| II. API-First Design | ✅ PASS | Next.js frontend consumes REST API; all routes in `lib/api/`; no direct DB queries from frontend |
| III. Concurrent Execution | ✅ PASS | `Flux.merge()` dispatches all providers concurrently; each provider's `Flux<SseEvent>` is independent |
| IV. Data Integrity | ✅ PASS | Flyway V001–V009; `chat_turns` rows immutable after insert; no DDL against live DB |
| V. Observability | ✅ PASS | Structured log event per provider call (modelId, latency, tokens, error) on every `LlmAdapter` call |
| VI. Security | ✅ PASS | AES-256-GCM with per-key IV; `encrypted_key` + `key_iv` stored in BYTEA; key never returned by any API endpoint |
| VII. Simplicity | ✅ PASS | In-process `Flux` streaming (no broker); Docker Compose single-server; no distributed locks |

**Post-design re-evaluation**: All principles satisfied. No violations.

## Project Structure

### Documentation (this feature)

```text
specs/001-unified-parallel-llm-chat/
├── plan.md              ← this file
├── spec.md              ← feature specification
├── research.md          ← Phase 0: SDK choices, streaming arch, encryption strategy
├── data-model.md        ← Phase 1: 8 tables + Capability Matrix JSONB schema
├── quickstart.md        ← Phase 1: 5 validation scenarios
├── contracts/
│   ├── auth-api.md      ← POST /register, /verify-email, /login, /logout
│   ├── models-api.md    ← GET /models, GET /models/{id}
│   ├── user-config-api.md ← API key CRUD + model config endpoints
│   └── chat-api.md      ← POST /turns (SSE) + session CRUD
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
backend/
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    ├── main/
    │   ├── kotlin/com/octopusllm/
    │   │   ├── OctopusLlmApplication.kt
    │   │   ├── auth/
    │   │   │   ├── AuthController.kt
    │   │   │   ├── AuthService.kt
    │   │   │   ├── UserRepository.kt
    │   │   │   ├── EmailVerificationRepository.kt
    │   │   │   └── EmailService.kt
    │   │   ├── llm/
    │   │   │   ├── LlmAdapter.kt                   ← unified interface
    │   │   │   ├── CapabilityMatrix.kt              ← data class (from JSONB)
    │   │   │   ├── LlmRequest.kt                   ← canonical request model
    │   │   │   ├── LlmStreamEvent.kt                ← sealed class: Token/Complete/Error
    │   │   │   ├── ConcurrentLlmOrchestrator.kt    ← Flux.merge() parallel dispatch
    │   │   │   └── adapter/
    │   │   │       ├── OpenAiCompatAdapter.kt       ← serves OpenAI, Moonshot, DeepSeek
    │   │   │       ├── AnthropicAdapter.kt
    │   │   │       ├── ZhipuAdapter.kt
    │   │   │       └── MiniMaxAdapter.kt
    │   │   ├── model/
    │   │   │   ├── ModelDefinition.kt               ← JPA entity
    │   │   │   ├── ModelDefinitionRepository.kt
    │   │   │   └── ModelCatalogueController.kt
    │   │   ├── userconfig/
    │   │   │   ├── ProviderApiKey.kt                ← JPA entity (encrypted)
    │   │   │   ├── ProviderApiKeyRepository.kt
    │   │   │   ├── UserModelConfig.kt               ← JPA entity
    │   │   │   ├── UserModelConfigRepository.kt
    │   │   │   ├── UserConfigController.kt
    │   │   │   ├── UserConfigService.kt
    │   │   │   └── ApiKeyEncryptionService.kt       ← AES-256-GCM
    │   │   └── chat/
    │   │       ├── ChatSession.kt
    │   │       ├── ChatTurn.kt                      ← immutable after insert
    │   │       ├── ProviderResponse.kt
    │   │       ├── ChatSessionRepository.kt
    │   │       ├── ChatTurnRepository.kt
    │   │       ├── ProviderResponseRepository.kt
    │   │       ├── ChatController.kt                ← SSE streaming endpoint
    │   │       └── ChatService.kt
    │   └── resources/
    │       ├── application.yml
    │       ├── application-docker.yml
    │       └── db/migration/
    │           ├── V001__create_users.sql
    │           ├── V002__create_email_verifications.sql
    │           ├── V003__create_provider_api_keys.sql
    │           ├── V004__create_model_definitions.sql
    │           ├── V005__create_user_model_configs.sql
    │           ├── V006__create_chat_sessions.sql
    │           ├── V007__create_chat_turns.sql
    │           ├── V008__create_provider_responses.sql
    │           └── V009__seed_model_catalogue.sql
    └── test/
        └── kotlin/com/octopusllm/
            ├── auth/
            │   ├── AuthControllerTest.kt            ← integration (Testcontainers)
            │   └── AuthServiceTest.kt               ← unit
            ├── llm/
            │   ├── ConcurrentLlmOrchestratorTest.kt ← unit (mock adapters)
            │   └── CapabilityRoutingTest.kt          ← unit (attachment routing)
            └── chat/
                └── ChatControllerTest.kt            ← integration (SSE stream)

frontend/
├── package.json
├── tsconfig.json
├── next.config.ts
├── app/
│   ├── (auth)/
│   │   ├── register/page.tsx
│   │   └── login/page.tsx
│   ├── (app)/
│   │   ├── layout.tsx                              ← auth-gated layout
│   │   ├── chat/
│   │   │   ├── page.tsx                            ← new chat
│   │   │   └── [sessionId]/page.tsx                ← session history
│   │   └── settings/models/page.tsx
│   └── layout.tsx
├── components/
│   ├── auth/
│   │   ├── RegisterForm.tsx                        ← "use client"
│   │   └── LoginForm.tsx                           ← "use client"
│   ├── chat/
│   │   ├── ChatInput.tsx                           ← "use client"
│   │   ├── ModelSelectorPanel.tsx                  ← "use client"
│   │   ├── ParallelResponseGrid.tsx                ← "use client" (SSE consumer)
│   │   └── ModelResponsePanel.tsx                  ← "use client"
│   └── models/
│       ├── ModelCard.tsx
│       ├── CapabilityBadge.tsx
│       └── ApiKeyForm.tsx                          ← "use client"
└── lib/
    ├── api/
    │   ├── auth.ts
    │   ├── models.ts
    │   ├── userConfig.ts
    │   └── chat.ts                                 ← SSE stream client
    └── types/api.ts

docker-compose.yml
.env.example
```

**Structure Decision**: Web application (Option 2). Spring Boot/WebFlux backend in `backend/`;
Next.js App Router frontend in `frontend/`. Docker Compose in the repository root orchestrates
both plus PostgreSQL.
