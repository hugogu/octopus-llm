---
description: "Task list for Unified Parallel LLM Chat"
---

# Tasks: Unified Parallel LLM Chat

**Input**: Design documents from `specs/001-unified-parallel-llm-chat/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**Organization**: Tasks grouped by user story to enable independent implementation and testing
of each story. Tests are included for critical integration paths.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1=Registration/Auth, US2=Model Config, US3=Parallel Chat,
  US4=Multi-modal Input, US5=Capability Matrix Visibility

---

## Phase 1: Setup

**Purpose**: Initialize both projects and shared infrastructure configuration.
All tasks in this phase can run in parallel once the directory structure exists.

- [ ] T001 Create backend/ directory and initialize Gradle Kotlin DSL project (`gradle init`) with `com.octopusllm` as the base package in `backend/`
- [ ] T002 [P] Create frontend/ directory and initialize Next.js 15 App Router project with TypeScript (`npx create-next-app@latest frontend --typescript --app --tailwind --src-dir false`) in `frontend/`
- [ ] T003 Configure `docker-compose.yml` at repo root with services: `db` (postgres:16), `backend` (port 8080), `frontend` (port 3000), `mailhog` (ports 1025/8025); use env_file `.env`
- [ ] T004 [P] Create `.env.example` at repo root with all required variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ENCRYPTION_MASTER_KEY`, `JWT_SECRET`, `JWT_EXPIRY_SECONDS`, `MAIL_HOST`, `MAIL_PORT`, `FRONTEND_URL`
- [ ] T005 Configure `backend/build.gradle.kts` with dependencies: `spring-boot-starter-webflux`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `flyway-core`, `postgresql`, `openai-java:2.x`, `anthropic-java`, `zhipuai-sdk-java-v4`, `kotlin-coroutines-reactor`, `jjwt-api` + impl + jackson, `testcontainers-postgresql`, `mockk`
- [ ] T006 [P] Configure `frontend/package.json` to add dev dependencies: `vitest`, `@testing-library/react`, `@testing-library/user-event`; confirm `next`, `react`, `react-dom`, `typescript` are present
- [ ] T007 [P] Create `backend/src/main/resources/application.yml` with datasource, JPA (ddl-auto: validate), Flyway (enabled: true, locations: classpath:db/migration), server port 8080, JWT config from env vars
- [ ] T008 [P] Create `backend/src/main/resources/application-docker.yml` with DB host pointing to `db` service container name
- [ ] T009 [P] Verify `frontend/tsconfig.json` has `"strict": true`, `"noUncheckedIndexedAccess": true`; adjust if missing
- [ ] T010 [P] Create `frontend/lib/types/api.ts` declaring TypeScript interfaces for all API request/response shapes from `contracts/`

**Checkpoint**: Both projects build (`./gradlew build --dry-run` and `npm run build --dry-run`) before proceeding.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: DB schema, shared domain types, security infrastructure, and LLM abstraction
layer. ALL user story phases depend on this phase being complete.

**⚠️ CRITICAL**: No user story implementation can begin until all Phase 2 tasks are complete.

### Database Migrations

- [ ] T011 Create `backend/src/main/resources/db/migration/V001__create_users.sql`: `id UUID PK`, `email VARCHAR(255) UNIQUE`, `password_hash VARCHAR(255)`, `email_verified BOOLEAN DEFAULT false`, `created_at/updated_at TIMESTAMPTZ`
- [ ] T012 [P] Create `backend/src/main/resources/db/migration/V002__create_email_verifications.sql`: `id UUID PK`, `user_id UUID FK→users(id) ON DELETE CASCADE`, `token VARCHAR(255) UNIQUE`, `expires_at TIMESTAMPTZ`, `used_at TIMESTAMPTZ NULL`, `created_at TIMESTAMPTZ`
- [ ] T013 [P] Create `backend/src/main/resources/db/migration/V003__create_provider_api_keys.sql`: `id UUID PK`, `user_id FK→users(id)`, `provider_id VARCHAR(100)`, `encrypted_key BYTEA`, `key_iv BYTEA`, `label VARCHAR(255) NULL`, timestamps; index `(user_id, provider_id)`
- [ ] T014 [P] Create `backend/src/main/resources/db/migration/V004__create_model_definitions.sql`: `id VARCHAR(100) PK`, `provider_id VARCHAR(100)`, `display_name VARCHAR(255)`, `capability_matrix JSONB NOT NULL`, `is_active BOOLEAN DEFAULT true`, timestamps; index `(provider_id)`
- [ ] T015 [P] Create `backend/src/main/resources/db/migration/V005__create_user_model_configs.sql`: `id UUID PK`, `user_id FK→users(id)`, `model_id FK→model_definitions(id)`, `provider_api_key_id UUID NULL FK→provider_api_keys(id) ON DELETE SET NULL`, `is_enabled BOOLEAN DEFAULT true`, timestamps; unique `(user_id, model_id)`
- [ ] T016 [P] Create `backend/src/main/resources/db/migration/V006__create_chat_sessions.sql`: `id UUID PK`, `user_id FK→users(id)`, `title VARCHAR(500) NULL`, timestamps
- [ ] T017 [P] Create `backend/src/main/resources/db/migration/V007__create_chat_turns.sql`: `id UUID PK`, `session_id FK→chat_sessions(id)`, `sequence_num INTEGER`, `prompt_text TEXT`, `attachments JSONB NULL`, `selected_model_ids TEXT[] NOT NULL`, `client_request_id VARCHAR(100) NULL`, `created_at TIMESTAMPTZ`; unique `(session_id, sequence_num)`; partial unique index on `client_request_id WHERE client_request_id IS NOT NULL` (idempotency key); index `(session_id, sequence_num)`
- [ ] T018 [P] Create `backend/src/main/resources/db/migration/V008__create_provider_responses.sql`: `id UUID PK`, `turn_id FK→chat_turns(id)`, `model_id FK→model_definitions(id)`, `status VARCHAR(50) NOT NULL` (`complete`/`error` only — set at insert, never updated), `response_text TEXT NULL`, `error_message TEXT NULL`, `input_tokens INT NULL`, `output_tokens INT NULL`, `latency_ms INT NOT NULL`, `created_at TIMESTAMPTZ` (= completion time); unique `(turn_id, model_id)`; index `(turn_id)`. Rows are INSERTed once on completion (no `pending`/`streaming` rows, no `completed_at`, no UPDATEs) per data-model immutability rule
- [ ] T019 [P] Create `backend/src/main/resources/db/migration/V009__seed_model_catalogue.sql`: INSERT rows for at least one model per provider (`openai`, `anthropic`, `moonshot`, `deepseek`, `zhipu`, `minimax`) with full `capability_matrix` JSONB as per `data-model.md`
- [ ] T020 [P] Create `backend/src/main/resources/db/migration/V010__create_revoked_tokens.sql`: `jti VARCHAR(255) PK`, `user_id FK→users(id) ON DELETE CASCADE`, `expires_at TIMESTAMPTZ`, `revoked_at TIMESTAMPTZ DEFAULT now()`; index `(expires_at)`

### Security & JWT Infrastructure

- [ ] T021 Create `backend/src/main/kotlin/com/octopusllm/auth/JwtTokenService.kt`: issue JWT (with `jti` UUID, `sub` userId, `exp`), validate signature + expiry, check `jti` against `revoked_tokens` table; wrap all DB reads in `Schedulers.boundedElastic()`
- [ ] T022 Create `backend/src/main/kotlin/com/octopusllm/config/SecurityConfig.kt`: Spring Security WebFlux configuration; JWT bearer token filter using `JwtTokenService`; permit `/api/v1/auth/**`, `/api/v1/models/**`, `/api/v1/health`; require auth for everything else; configure CORS for `FRONTEND_URL` env var

### LLM Abstraction Layer

- [ ] T023 Create `backend/src/main/kotlin/com/octopusllm/llm/LlmAdapter.kt`: Kotlin interface with `fun stream(request: LlmRequest, decryptedApiKey: String): Flux<LlmStreamEvent>`; include `val providerId: String`
- [ ] T024 [P] Create `backend/src/main/kotlin/com/octopusllm/llm/CapabilityMatrix.kt`: data class with fields `inputModalities: List<String>`, `outputModalities: List<String>`, `contextLengthTokens: Int?`, `supportsStreaming: Boolean`, `supportsFunctionCalling: Boolean`, `supportsSystemPrompt: Boolean`, `supportsVideoInput: Boolean`, `extras: Map<String, Any> = emptyMap()` for unknown JSONB keys
- [ ] T025 [P] Create `backend/src/main/kotlin/com/octopusllm/llm/LlmRequest.kt`: data class with `prompt: String`, `history: List<HistoryTurn>`, `attachments: List<Attachment>`; nested data class `Attachment(type: String, data: String, mimeType: String)` and `HistoryTurn(role: String, text: String, attachments: List<Attachment>)`
- [ ] T026 [P] Create `backend/src/main/kotlin/com/octopusllm/llm/LlmStreamEvent.kt`: sealed class with subclasses `Token(modelId: String, delta: String)`, `ModelComplete(modelId: String, inputTokens: Int?, outputTokens: Int?, latencyMs: Long)`, `ModelError(modelId: String, error: String)`, `CapabilityNotice(modelId: String, notice: String)`

### Shared Utilities

- [ ] T027 Create `backend/src/main/kotlin/com/octopusllm/userconfig/ApiKeyEncryptionService.kt`: AES-256-GCM encrypt/decrypt; master key loaded from `ENCRYPTION_MASTER_KEY` env var (base64 32 bytes); generates random 12-byte IV per write; stores/reads IV separately
- [ ] T028 [P] Create `backend/src/main/kotlin/com/octopusllm/HealthController.kt`: `@GetMapping("/api/v1/health")` returning `{"status":"UP"}`
- [ ] T029 [P] Create `backend/src/main/kotlin/com/octopusllm/config/GlobalExceptionHandler.kt`: `@RestControllerAdvice` mapping Spring validation errors, `ResponseStatusException`, and custom exceptions to the standard `{"code","message","details"}` error schema

**Checkpoint**: `./gradlew build` passes; Flyway migrations apply cleanly; `GET /api/v1/health` returns 200 on a running container. All user story work can now begin.

---

## Phase 3: User Story 1 — Account Registration & Login (Priority: P1) 🎯 MVP

**Goal**: Users can register with email verification and log in. Logout immediately invalidates
the JWT via the `revoked_tokens` blocklist.

**Independent Test**: Run quickstart.md Scenario 1 (`register → verify-email → login → logout`
returns 401 on subsequent request with same token).

### JPA Entities & Repositories for User Story 1

- [ ] T030 [US1] Create `backend/src/main/kotlin/com/octopusllm/auth/User.kt`: JPA entity mapping `users` table (UUID id, email, passwordHash, emailVerified, timestamps)
- [ ] T031 [P] [US1] Create `backend/src/main/kotlin/com/octopusllm/auth/EmailVerification.kt`: JPA entity mapping `email_verifications` table
- [ ] T032 [P] [US1] Create `backend/src/main/kotlin/com/octopusllm/auth/RevokedToken.kt`: JPA entity mapping `revoked_tokens` table
- [ ] T033 [P] [US1] Create `backend/src/main/kotlin/com/octopusllm/auth/UserRepository.kt`: Spring Data JPA repository with `findByEmail(email: String)`, `existsByEmail(email: String)`
- [ ] T034 [P] [US1] Create `backend/src/main/kotlin/com/octopusllm/auth/EmailVerificationRepository.kt`: with `findByToken(token: String)`, `deleteByUserIdAndUsedAtIsNull(userId: UUID)`
- [ ] T035 [P] [US1] Create `backend/src/main/kotlin/com/octopusllm/auth/RevokedTokenRepository.kt`: with `existsByJti(jti: String)`, `deleteByExpiresAtBefore(cutoff: Instant)`

### Services & Controller for User Story 1

- [ ] T036 [US1] Create `backend/src/main/kotlin/com/octopusllm/auth/EmailService.kt`: compose and send HTML/text verification email using Spring Mail (`JavaMailSender`); in dev, sends to Mailhog
- [ ] T037 [US1] Create `backend/src/main/kotlin/com/octopusllm/auth/AuthService.kt`: implement `register(email, password)` (bcrypt hash cost ≥12, create unverified user, generate 64-hex token, call EmailService); `verifyEmail(token)` (check expiry + used_at); `login(email, password)` (check verified, bcrypt match, rate-limit, issue JWT via JwtTokenService); `logout(jti, userId, exp)` (insert revoked_tokens row); all DB calls wrapped in `Schedulers.boundedElastic()`
- [ ] T038 [US1] Create `backend/src/main/kotlin/com/octopusllm/auth/AuthController.kt`: WebFlux `@RestController` wiring `POST /api/v1/auth/register`, `POST /api/v1/auth/verify-email`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout` per `contracts/auth-api.md`

### Frontend for User Story 1

- [ ] T039 [P] [US1] Create `frontend/lib/api/auth.ts`: typed `register()`, `verifyEmail()`, `login()` (stores JWT in cookie/localStorage), `logout()` fetch wrappers matching `contracts/auth-api.md`
- [ ] T040 [P] [US1] Create `frontend/components/auth/RegisterForm.tsx` (`"use client"`): controlled form with email + password + confirm-password fields; calls `auth.register()`; shows success message or field-level errors
- [ ] T041 [P] [US1] Create `frontend/components/auth/LoginForm.tsx` (`"use client"`): email + password fields; calls `auth.login()`; redirects to `/chat` on success; shows generic error on 401
- [ ] T042 [US1] Create `frontend/app/(auth)/register/page.tsx`: server component rendering `<RegisterForm />`
- [ ] T043 [P] [US1] Create `frontend/app/(auth)/login/page.tsx`: server component rendering `<LoginForm />`; redirect to `/chat` if already authenticated
- [ ] T044 [US1] Create `frontend/app/(app)/layout.tsx`: auth-gated layout; reads JWT from cookie; redirects to `/login` if absent or expired

### Tests for User Story 1

- [ ] T045 [P] [US1] Create `backend/src/test/kotlin/com/octopusllm/auth/AuthControllerTest.kt`: Testcontainers+PostgreSQL integration tests for register→verify→login→logout flow per quickstart.md Scenario 1

**Checkpoint**: Scenario 1 from quickstart.md passes. User can register, verify email, log in, call an authenticated endpoint, log out, and confirm the token is rejected.

---

## Phase 4: User Story 2 — LLM Model Configuration (Priority: P1)

**Goal**: Authenticated users can view the model catalogue with Capability Matrices, store
encrypted API keys, and enable/disable models.

**Independent Test**: Run quickstart.md Scenario 2 (`view models → add OpenAI key → enable model → list configs`).

### JPA Entities & Repositories for User Story 2

- [ ] T046 [US2] Create `backend/src/main/kotlin/com/octopusllm/model/ModelDefinition.kt`: JPA entity for `model_definitions`; map `capability_matrix` JSONB to `CapabilityMatrix` via Hibernate `@Type(JsonType::class)` (hibernate-types or hypersistence-utils)
- [ ] T047 [P] [US2] Create `backend/src/main/kotlin/com/octopusllm/userconfig/ProviderApiKey.kt`: JPA entity for `provider_api_keys`; `encryptedKey: ByteArray`, `keyIv: ByteArray` fields mapped to BYTEA
- [ ] T048 [P] [US2] Create `backend/src/main/kotlin/com/octopusllm/userconfig/UserModelConfig.kt`: JPA entity for `user_model_configs`; `providerApiKey: ProviderApiKey?` (nullable, mapped to nullable FK)
- [ ] T049 [P] [US2] Create `backend/src/main/kotlin/com/octopusllm/model/ModelDefinitionRepository.kt`: with `findByIsActiveTrue()`, `findByProviderIdAndIsActiveTrue(providerId)`, `findByIdAndIsActiveTrue(id)`
- [ ] T050 [P] [US2] Create `backend/src/main/kotlin/com/octopusllm/userconfig/ProviderApiKeyRepository.kt`: with `findByUserIdAndProviderId(userId, providerId)`, `findByUserId(userId)`
- [ ] T051 [P] [US2] Create `backend/src/main/kotlin/com/octopusllm/userconfig/UserModelConfigRepository.kt`: with `findByUserId(userId)`, `findByUserIdAndModelId(userId, modelId)`, `findByProviderApiKeyId(keyId)`

### Services & Controllers for User Story 2

- [ ] T052 [US2] Create `backend/src/main/kotlin/com/octopusllm/model/ModelCatalogueService.kt`: list active models (with optional `providerId` / `inputModality` filter), get model by id; reads from `model_definitions` on `Schedulers.boundedElastic()`
- [ ] T053 [US2] Create `backend/src/main/kotlin/com/octopusllm/model/ModelCatalogueController.kt`: `GET /api/v1/models` (with query params), `GET /api/v1/models/{modelId}` per `contracts/models-api.md`
- [ ] T054 [US2] Create `backend/src/main/kotlin/com/octopusllm/userconfig/UserConfigService.kt`: `addApiKey(userId, providerId, rawKey, label)` (format-validate, encrypt via ApiKeyEncryptionService, persist); `deleteApiKey(userId, keyId)` (delete row — ON DELETE SET NULL cascades; service also sets `is_enabled=false` on affected configs); `addModelConfig`, `patchModelConfig(enable/disable)`, `deleteModelConfig`; all DB calls on `boundedElastic()`
- [ ] T055 [US2] Create `backend/src/main/kotlin/com/octopusllm/userconfig/UserConfigController.kt`: all `/api/v1/user/api-keys` and `/api/v1/user/model-configs` endpoints per `contracts/user-config-api.md`

### Frontend for User Story 2

- [ ] T056 [P] [US2] Create `frontend/lib/api/models.ts`: typed `listModels(params?)`, `getModel(id)` fetch wrappers
- [ ] T057 [P] [US2] Create `frontend/lib/api/userConfig.ts`: typed wrappers for all `/api/v1/user/` API key and model config endpoints
- [ ] T058 [P] [US2] Create `frontend/components/models/CapabilityBadge.tsx`: renders a single capability chip (icon + label) for one modality or feature flag
- [ ] T059 [P] [US2] Create `frontend/components/models/ModelCard.tsx`: model display name, provider name, list of `<CapabilityBadge>` for all Capability Matrix fields
- [ ] T060 [P] [US2] Create `frontend/components/models/ApiKeyForm.tsx` (`"use client"`): provider dropdown (populated from catalogue), API key input, optional label; calls `userConfig.addApiKey()`; shows saved key metadata on success
- [ ] T061 [US2] Create `frontend/app/(app)/settings/models/page.tsx`: server component; loads models catalogue + user configs server-side; renders `<ModelCard>` list with enable/disable toggles and `<ApiKeyForm>` per provider

**Checkpoint**: Scenario 2 from quickstart.md passes. API key stored, model enabled, model visible in user configs.

---

## Phase 5: User Story 3 — Parallel Chat with Real-time Streaming (Priority: P1)

**Goal**: Authenticated users with ≥2 configured models can submit a text prompt and see
all model responses streaming concurrently in real time.

**Independent Test**: Run quickstart.md Scenario 3 (parallel stream; verify interleaved
`token` events from both models before either `model_complete`; total elapsed ≈ slowest model).

### LLM Adapters (can be implemented in parallel)

- [ ] T062 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/llm/adapter/OpenAiCompatAdapter.kt`: implements `LlmAdapter`; accepts `baseUrl` and `providerId` constructor args; uses `openai-java` `OpenAIClient.chat().completions().streamRaw()` with `stream: true`; maps chunks to `Token` / `ModelComplete` events; handles `OpenAIServiceException` → `ModelError`
- [ ] T063 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/llm/adapter/AnthropicAdapter.kt`: implements `LlmAdapter`; uses `anthropic-java` client; maps streaming message events to `Token` / `ModelComplete`; wraps `APIException` → `ModelError`
- [ ] T064 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/llm/adapter/ZhipuAdapter.kt`: implements `LlmAdapter`; uses `zhipuai-sdk-java-v4` `ChatCompletionRequest` with `stream = true`; maps SSE events to `Token` / `ModelComplete`
- [ ] T065 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/llm/adapter/MiniMaxAdapter.kt`: implements `LlmAdapter`; uses Spring `WebClient` to POST to MiniMax REST API (`https://api.minimax.chat/v1/text/chatcompletion_v2`); parses `text/event-stream` response body as `Token` / `ModelComplete` events

### Orchestrator & Adapter Registry

- [ ] T066 [US3] Create `backend/src/main/kotlin/com/octopusllm/llm/AdapterRegistry.kt`: Spring `@Component` mapping `providerId` strings to `LlmAdapter` instances; register adapters for `openai`, `moonshot` (OpenAiCompatAdapter with Moonshot baseUrl), `deepseek` (OpenAiCompatAdapter with DeepSeek baseUrl), `anthropic`, `zhipu`, `minimax`
- [ ] T067 [US3] Create `backend/src/main/kotlin/com/octopusllm/llm/ConcurrentLlmOrchestrator.kt`: for a list of `(modelId, decryptedApiKey, capabilityMatrix)` tuples and a `LlmRequest`, produces `Flux<SseResponse>` via `Flux.merge()`; prepends `turn_created` event; emits `all_complete` when all provider streams complete or error; each provider stream wrapped in `.onErrorResume { ModelError(...) }`; all `providesId → adapter` lookups via `AdapterRegistry`; no distributed state

### Chat Domain: Entities, Repos, Service, Controller

- [ ] T068 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/chat/ChatSession.kt`: JPA entity for `chat_sessions`
- [ ] T069 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/chat/ChatTurn.kt`: JPA entity for `chat_turns`; `selectedModelIds` mapped to `TEXT[]` via `@Type`; `attachments` mapped to JSONB `List<Map<String,String>>`
- [ ] T070 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/chat/ProviderResponse.kt`: JPA entity for `provider_responses`; status field as String enum `complete`/`error` only (set once at insert; entity is write-once, no update methods)
- [ ] T071 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/chat/ChatSessionRepository.kt`: `findByUserIdOrderByCreatedAtDesc(userId, pageable)`
- [ ] T072 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/chat/ChatTurnRepository.kt`: `findBySessionIdOrderBySequenceNum(sessionId)`
- [ ] T073 [P] [US3] Create `backend/src/main/kotlin/com/octopusllm/chat/ProviderResponseRepository.kt`: `findByTurnId(turnId)`
- [ ] T074 [US3] Create `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`: `createSession`, `listSessions(userId, pageable)`, `getSession(sessionId, userId)` (loads turns + responses); `submitTurn(sessionId, userId, promptText, selectedModelIds, attachments, clientRequestId?)`: (0) if `clientRequestId` is non-null and a turn with it already exists, throw `DuplicateRequestException` (→ 409 with existing turnId); (1) persist ChatTurn (incl. `clientRequestId`) on `boundedElastic()`, (2) decrypt API keys for selected models on `boundedElastic()`, (3) build `LlmRequest` including prior turns as history, (4) invoke `ConcurrentLlmOrchestrator.stream()`, (5) accumulate tokens per model in memory and INSERT one `ProviderResponse` row (status `complete`/`error`) per model on its terminal event via `boundedElastic()` — no intermediate rows, no UPDATEs
- [ ] T075 [US3] Create `backend/src/main/kotlin/com/octopusllm/chat/ChatController.kt`: WebFlux controller; `POST /sessions` (201), `GET /sessions` (200 paginated), `GET /sessions/{id}` (200 with turns+responses); `POST /sessions/{id}/turns` accepts optional `clientRequestId` in body, returns `409 DUPLICATE_REQUEST` (body `{"turnId":...}`) on idempotency conflict, otherwise streams `Flux<ServerSentEvent<String>>` with `Content-Type: text/event-stream` per `contracts/chat-api.md`

### Frontend for User Story 3

- [ ] T076 [P] [US3] Create `frontend/lib/api/chat.ts`: typed wrappers for `createSession`, `listSessions`, `getSession`; `streamTurn(sessionId, body, onEvent)` using `fetch()` + manual `ReadableStream` parsing for SSE events; routes each event by `event.modelId` to caller callback
- [ ] T077 [P] [US3] Create `frontend/components/chat/ModelResponsePanel.tsx` (`"use client"`): accepts `modelId`, `displayName`; renders streamed text as tokens arrive; shows spinner while streaming, checkmark on `model_complete`, error badge on `model_error`; shows latency + token counts after completion
- [ ] T078 [P] [US3] Create `frontend/components/chat/ParallelResponseGrid.tsx` (`"use client"`): receives SSE events from `chat.streamTurn()`; maintains a map of `modelId → accumulated text`; renders one `<ModelResponsePanel>` per selected model in a responsive grid
- [ ] T079 [P] [US3] Create `frontend/components/chat/ModelSelectorPanel.tsx` (`"use client"`): checkbox list of all enabled models from user config; emits selected model IDs to parent
- [ ] T080 [US3] Create `frontend/components/chat/ChatInput.tsx` (`"use client"`): controlled textarea; submit button; disabled while streaming; calls `onSubmit(promptText)` callback
- [ ] T081 [US3] Create `frontend/app/(app)/chat/page.tsx`: new chat page; server component loads user model configs; renders `<ModelSelectorPanel>`, `<ChatInput>`, `<ParallelResponseGrid>`; creates session on first submit via `chat.createSession()` then calls `streamTurn()`
- [ ] T082 [US3] Create `frontend/app/(app)/chat/[sessionId]/page.tsx`: existing session page; loads prior turns from `GET /sessions/{id}`; renders read-only history above live chat components

### Tests for User Story 3

- [ ] T083 [P] [US3] Create `backend/src/test/kotlin/com/octopusllm/llm/ConcurrentLlmOrchestratorTest.kt`: unit tests with mock `LlmAdapter`s verifying: all adapters called simultaneously, `Flux.merge()` interleaves events, single adapter error emits `ModelError` without cancelling others
- [ ] T084 [P] [US3] Create `backend/src/test/kotlin/com/octopusllm/chat/ChatControllerTest.kt`: integration test (Testcontainers + PostgreSQL) for `POST /sessions/{id}/turns`; asserts SSE stream contains `turn_created`, `token` events from both mock adapters, `model_complete` for each, then `all_complete`

**Checkpoint**: Scenario 3 from quickstart.md passes. Two models stream concurrently; fastest model's first token arrives before slowest model completes.

---

## Phase 6: User Story 4 — Multi-modal Input (Priority: P2)

**Goal**: Users can attach images (and video for capable models) to prompts. The system
routes attachments only to capable models and emits `capability_notice` for others.

**Independent Test**: Run quickstart.md Scenario 5. GPT-4o response references image content;
DeepSeek receives `capability_notice` event and responds without image.

- [ ] T085 [US4] Update `backend/src/main/kotlin/com/octopusllm/llm/ConcurrentLlmOrchestrator.kt`: before dispatching to each adapter, filter `request.attachments` to only those modalities in `capabilityMatrix.inputModalities`; for each dropped modality emit `CapabilityNotice(modelId, "X input not supported — text only sent")` as a leading SSE event on that model's stream
- [ ] T086 [P] [US4] Update `backend/src/main/kotlin/com/octopusllm/llm/adapter/OpenAiCompatAdapter.kt`: when `request.attachments` includes image entries, build multi-part `content` array with `{type:"image_url", image_url:{url:"data:<mime>;base64,<data>"}}` content blocks alongside the text part
- [ ] T087 [P] [US4] Update `backend/src/main/kotlin/com/octopusllm/llm/adapter/AnthropicAdapter.kt`: include `{type:"image",source:{type:"base64",media_type:"...",data:"..."}}` content blocks in the Anthropic messages API payload
- [ ] T088 [P] [US4] Update `frontend/components/chat/ChatInput.tsx`: add file attachment button; show attachment button only when at least one selected model has `inputModalities` containing `"image"` or `"video"`; encode selected files as base64; include in `attachments` array passed to `streamTurn()`
- [ ] T089 [US4] Update `frontend/components/chat/ModelResponsePanel.tsx`: render `capability_notice` SSE event as an inline notice banner at the top of the panel before the first token

**Checkpoint**: Scenario 5 from quickstart.md passes. At least one image-capable model returns a response referencing the image; text-only model shows capability notice.

---

## Phase 7: User Story 5 — Capability Matrix Visibility (Priority: P2)

**Goal**: Users can see what each model supports in the model catalogue, the settings page,
and inline in the chat interface.

**Independent Test**: Open `/api/v1/models` in browser; verify every model has a populated
`capabilityMatrix`. Open settings page; verify each model shows capability badges. Open chat;
hover a model panel header and verify full Capability Matrix is shown.

- [ ] T090 [P] [US5] Update `frontend/components/models/ModelCard.tsx`: render ALL Capability Matrix fields as `<CapabilityBadge>` chips — `inputModalities`, `outputModalities`, streaming, functionCalling, contextLength
- [ ] T091 [P] [US5] Update `frontend/components/chat/ModelSelectorPanel.tsx`: show condensed `<CapabilityBadge>` icons next to each model checkbox (input modalities only, for space)
- [ ] T092 [US5] Update `frontend/components/chat/ModelResponsePanel.tsx`: add expandable capability tooltip in the panel header; shows full `CapabilityMatrix` object on hover/click; sourced from the model config preloaded with the session

**Checkpoint**: Model catalogue page shows full Capability Matrix for all models; chat panel headers show capability tooltip.

---

## Final Phase: Polish & Cross-Cutting Concerns

**Purpose**: Integration, correctness gates, Docker verification, and quickstart validation.

- [ ] T093 [P] Create `backend/src/test/kotlin/com/octopusllm/llm/CapabilityRoutingTest.kt`: unit tests verifying that an attachment of type `"image"` is included in `LlmRequest.attachments` only for models with `"image"` in `inputModalities`; dropped + `capability_notice` emitted for others
- [ ] T094 Run `./gradlew build` from `backend/` and fix ALL compilation errors and test failures
- [ ] T095 [P] Run `npx tsc --noEmit` from `frontend/` and fix ALL TypeScript errors
- [ ] T096 Run `docker compose build` from repo root and verify both service images build successfully on local platform
- [ ] T097 Run `docker compose up -d` and perform quickstart.md Scenario 1 (auth flow) against running containers; confirm `201`, email token, `200`, JWT, `401` after logout
- [ ] T098 Run quickstart.md Scenario 3 (parallel chat) against running containers; confirm SSE events interleaved, `all_complete` final
- [ ] T099 Run quickstart.md Scenario 4 (single model failure) against running containers; confirm `model_error` for bad-key model, `model_complete` for good model, `all_complete` final
- [ ] T100 [P] Review and update `specs/001-unified-parallel-llm-chat/checklists/requirements.md`; confirm all acceptance scenarios from spec.md have corresponding implemented tests or quickstart verification steps

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately; T002 and T003-T010 can run in parallel after T001 directory creation
- **Phase 2 (Foundational)**: Requires Phase 1 complete — BLOCKS all user story phases
- **Phase 3 (US1)**: Requires Phase 2 complete — auth entities need DB schema and Security infra
- **Phase 4 (US2)**: Requires Phase 2 complete — can run in parallel with Phase 3 once foundation is done
- **Phase 5 (US3)**: Requires Phase 2 complete + Phase 4 model catalogue (T049/T050/T051) to resolve adapter keys — can start in parallel with Phase 4 for adapter code, but ChatService needs UserModelConfig for key resolution
- **Phase 6 (US4)**: Requires Phase 5 (US3) complete — updates existing orchestrator and adapters
- **Phase 7 (US5)**: Requires Phase 4 (US2) complete — needs ModelCard and ModelResponsePanel from US2/US3
- **Polish**: Requires all desired user story phases complete

### User Story Internal Dependencies

Within each phase: entities before repositories → repositories before services → services before controllers → controllers before frontend API clients → frontend components before pages.

### Parallel Opportunities by Phase

**Phase 2** — All migrations (T011-T020) can run in parallel; LLM types (T023-T026) can run in parallel; T027-T029 can run in parallel.

**Phase 3 (US1)** — T030-T035 (entities + repos) can run in parallel after T011; T036+T037 can run in parallel; T039-T041 can run in parallel; T042+T043 can run in parallel.

**Phase 5 (US3)** — T062-T065 (all four adapters) can run fully in parallel; T068-T073 (entities + repos) can run in parallel; T076-T080 (frontend components) can run in parallel.

---

## Implementation Strategy

### MVP First (User Stories 1–3 only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks everything)
3. Complete Phase 3 (US1): Auth — test independently with quickstart Scenario 1
4. Complete Phase 4 (US2): Model Config — test independently with quickstart Scenario 2
5. Complete Phase 5 (US3): Parallel Chat — test independently with quickstart Scenario 3
6. **STOP and VALIDATE**: Run quickstart Scenarios 1-4; deploy and demo
7. Only proceed to US4/US5 after MVP is stable

### Incremental Delivery

- US1 complete → auth-only build deployable (no LLM yet)
- US2 complete → catalogue + key management deployable
- US3 complete → core parallel chat MVP ✅ demo-ready
- US4 complete → multi-modal capability added
- US5 complete → full Capability Matrix visibility

### Parallel Team Strategy (if applicable)

Once Phase 2 (Foundational) is complete:
- Developer A: Phase 3 (US1 — auth)
- Developer B: Phase 4 (US2 — model config)
- Developer C: Phase 5 adapters (T062-T065) — no DB dependency needed for adapter code

---

## Notes

- `[P]` tasks modify different files and have no dependency on incomplete sibling tasks
- `[USN]` label maps each task to its user story for traceability
- Each story phase includes an independent test checkpoint — stop and validate before moving to the next priority
- All DB writes in the service layer MUST use `Schedulers.boundedElastic()` (see plan.md constraints)
- Do NOT use `latest` image tags in docker-compose.yml — pin versions per constitution Principle VII
- Do NOT add implementation code, migrations, or tests in the same commit — keep changes atomic
