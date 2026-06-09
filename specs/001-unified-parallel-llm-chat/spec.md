# Feature Specification: Unified Parallel LLM Chat

**Feature Branch**: `001-unified-parallel-llm-chat`
**Created**: 2026-06-09
**Status**: Draft
**Input**: User registration, LLM model configuration, and unified chat page with real-time parallel multi-model responses; unified LLM abstraction with Capability Matrix.

## Overview

This feature delivers the foundational user experience of the Octopus LLM platform: a user
registers, configures their API keys for one or more LLM models, and sends prompts through a
single chat interface that dispatches to all selected models concurrently and streams each
response back in real time. The core design treats individual **models** (not provider brands)
as the primary concept, with a Capability Matrix declaring what each model can accept and
produce. This enables the platform to expose a unified capability superset (text, images, video)
while gracefully hiding or disabling unsupported capabilities per model.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Account Registration & Login (Priority: P1)

A new visitor wants to try the platform. They register with their email address and a password,
receive a verification email, confirm their account, and then log in to access the platform.
A returning user can log in directly.

**Why this priority**: Without an account, no personalized API key storage or usage tracking is
possible. This is a hard prerequisite for all other features.

**Independent Test**: A fresh environment where only the authentication service and user store
exist. Registration, email verification, and login flows can be fully exercised with no LLM
connectivity needed.

**Acceptance Scenarios**:

1. **Given** a visitor on the registration page, **When** they submit a valid email and strong
   password, **Then** the system creates an unverified account, sends a verification email, and
   shows a "check your inbox" confirmation.
2. **Given** a user who received the verification email, **When** they click the verification
   link, **Then** their account is marked verified and they are redirected to the login page.
3. **Given** a verified user, **When** they submit correct credentials on the login page,
   **Then** they receive an authenticated session and are directed to the dashboard.
4. **Given** a login attempt with invalid credentials, **When** submitted, **Then** a generic
   error message is shown (no hint about which field is wrong) and the attempt is rate-limited.
5. **Given** an already-authenticated user, **When** they visit the login page, **Then** they
   are redirected to the dashboard without re-authentication.

---

### User Story 2 — LLM Model Configuration (Priority: P1)

An authenticated user wants to add their personal API keys so the platform can call LLM models
on their behalf. They can add keys for multiple providers, associate them with specific models,
and designate which models are active. The platform shows each model's Capability Matrix so the
user understands what the model can do before activating it.

**Why this priority**: Parallel chat is meaningless without at least one configured model. Model
configuration must exist before the chat feature is usable.

**Independent Test**: With a working account, a user can navigate to settings, add an API key
for one provider, see the associated models listed with their Capability Matrix, enable a model,
and verify the model appears as selectable in the chat interface. No actual LLM call is made.

**Acceptance Scenarios**:

1. **Given** an authenticated user on the model configuration page, **When** they select a
   provider from the catalogue and enter a valid API key, **Then** the platform validates the
   key format and saves it encrypted; the models available under that provider are listed with
   their Capability Matrix.
2. **Given** a configured provider with models listed, **When** the user enables a model,
   **Then** that model becomes available in the parallel chat selection.
3. **Given** an API key that fails provider validation, **When** the user tries to save it,
   **Then** a clear error is displayed and no key is stored.
4. **Given** a user with multiple configured models, **When** they disable or remove a model,
   **Then** it is immediately excluded from future parallel calls and no longer visible in chat.
5. **Given** a model with image-input capability in its Capability Matrix, **When** viewed by
   the user, **Then** the platform displays an image icon indicating image support; models
   without image capability show no such indicator.

---

### User Story 3 — Parallel Chat with Real-time Streaming (Priority: P1)

An authenticated user with at least two configured models opens the chat interface, selects
which models to include in the current session, types a text prompt, and submits it. The
platform dispatches the prompt concurrently to all selected models and streams each model's
response tokens into its own column/panel as they arrive — without waiting for the slowest
model to finish.

**Why this priority**: This is the platform's core value proposition. All other features support
or extend this capability.

**Independent Test**: With two or more configured models, submit a simple text prompt and
observe that response panels for each model begin populating independently and simultaneously.
Verify that a slow model does not block a fast model from displaying its response.

**Acceptance Scenarios**:

1. **Given** a user with 3 configured models and all selected, **When** they submit a text
   prompt, **Then** all 3 response panels begin streaming tokens within 2 seconds of submission,
   each independently.
2. **Given** an ongoing parallel call where one model responds faster than others, **When**
   that model completes, **Then** its panel shows a "complete" indicator while other panels
   continue streaming — no panel is blocked.
3. **Given** a model that returns an error during a parallel call, **When** the error occurs,
   **Then** that model's panel shows a clear error state; other models' responses are
   unaffected and continue streaming.
4. **Given** a user who selects only a subset of their configured models before submitting,
   **When** the prompt is dispatched, **Then** only the selected models are called; deselected
   models show no response panel.
5. **Given** a user who has submitted a prompt and all models have responded, **When** the
   conversation continues with a follow-up prompt, **Then** the prior exchange is included as
   context and all selected models respond to the follow-up concurrently.

---

### User Story 4 — Multi-modal Input (Priority: P2)

An authenticated user wants to include an image in their prompt to compare how different models
interpret it. The chat interface detects which selected models support image input (via
Capability Matrix) and enables image attachment. Models that do not support image input receive
only the text portion of the prompt; the platform makes this behavior explicit to the user.

**Why this priority**: Multi-modal is a key differentiator for comparison but is not required
for the MVP text-only experience. Text comparison alone delivers value; image adds depth.

**Independent Test**: With at least one image-capable model configured, the user attaches an
image and a text prompt. The image-capable model's response references image content; a
text-only model's panel shows a notice that image input was omitted.

**Acceptance Scenarios**:

1. **Given** a user with at least one image-input-capable model selected, **When** they click
   the attachment button, **Then** the file picker allows selection of common image formats
   (JPEG, PNG, WebP, GIF).
2. **Given** a selected model that does NOT support image input, **When** the user attaches an
   image, **Then** that model's panel displays a notice: "Image input not supported — text only
   sent" and the model receives only the text portion.
3. **Given** a user who attaches an image and submits the prompt, **When** an image-capable
   model processes it, **Then** the response is streamed as normal with no degradation to
   text-only model responses in the same session.
4. **Given** a model whose Capability Matrix includes video-input, **When** the user has that
   model selected, **Then** the attachment UI also accepts common video formats; the Capability
   Matrix badge for that model shows "video" support.

---

### User Story 5 — Capability Matrix Visibility (Priority: P2)

A user browsing the model catalogue or configuring models wants a clear view of what each
model can do: input modalities (text, image, video), output modalities, context window size,
function-calling support, and streaming availability. This information is shown consistently
in both the configuration view and the chat interface.

**Why this priority**: Without Capability Matrix visibility, users cannot make informed
decisions about which models to compare for a given prompt type. Supports US2 and US4.

**Independent Test**: Navigate to the model catalogue without configuring any keys. All listed
models display their Capability Matrix. Verify that a known image-capable model shows the image
badge and a text-only model does not.

**Acceptance Scenarios**:

1. **Given** the model catalogue, **When** viewed by any user (authenticated or not),
   **Then** every listed model shows its Capability Matrix including: input modalities, output
   modalities, max context tokens, streaming support, and function-calling support.
2. **Given** the chat interface with models selected, **When** a user hovers over or expands a
   model's panel header, **Then** that model's Capability Matrix is shown inline.
3. **Given** a model whose Capability Matrix changes (e.g., a provider adds image support),
   **When** the platform's model catalogue is updated, **Then** the change is reflected without
   requiring user reconfiguration.

---

### Edge Cases

- What happens when ALL selected models fail during a parallel call? The user sees a full-page
  error state with per-model failure details and a retry option.
- What happens if the user's API key is revoked between sessions? The next parallel call to
  that model fails with a clear "API key invalid" message; other models are unaffected.
- What happens with very long responses that exceed display area? The panel scrolls
  independently; a "jump to latest" button appears when the user has scrolled up.
- What happens if the user submits an empty prompt? Submission is blocked with an inline
  validation message.
- What happens with a prompt attachment that exceeds the maximum supported file size? The
  upload is rejected before submission with a clear size limit message.
- What happens when a model has the same capability listed twice in the Matrix (bad data)?
  The platform deduplicates and shows each capability once.

---

## Requirements *(mandatory)*

### Functional Requirements

**User & Authentication**

- **FR-001**: The system MUST allow visitors to register an account using a valid email address
  and password meeting minimum strength requirements.
- **FR-002**: The system MUST send an email verification message upon registration and require
  verification before the account is fully active.
- **FR-003**: The system MUST allow verified users to log in with email and password.
- **FR-004**: The system MUST rate-limit failed login attempts to prevent brute-force attacks.
- **FR-005**: The system MUST allow authenticated users to log out, invalidating their session.

**Model Configuration**

- **FR-006**: The system MUST maintain a platform-managed catalogue of supported LLM providers
  and their available models; this catalogue is the authoritative source of model metadata.
- **FR-007**: The system MUST define a Capability Matrix for each model in the catalogue,
  declaring at minimum: input modalities (text, image, video), output modalities (text, image),
  maximum context tokens, streaming support (yes/no), and function-calling support (yes/no).
- **FR-008**: Authenticated users MUST be able to store one or more API keys, each scoped to a
  specific provider, encrypted at rest.
- **FR-009**: When a user stores an API key, the system MUST validate the key's format before
  saving; key correctness with the remote provider is validated lazily (on first use).
- **FR-010**: Users MUST be able to enable or disable individual models without deleting their
  API key.
- **FR-011**: Users MUST be able to delete stored API keys; deletion MUST immediately revoke
  all models associated with that key from the user's active configuration.
- **FR-012**: API keys MUST NOT appear in API responses, logs, or analytics at any time.

**Parallel Chat & Capability Routing**

- **FR-013**: The chat interface MUST allow users to select a subset of their enabled models
  for each session or call.
- **FR-014**: When a user submits a prompt, the system MUST dispatch it concurrently to all
  selected models; sequential dispatch is prohibited.
- **FR-015**: The system MUST stream each model's response tokens to the client as they are
  received, independently of other models' progress.
- **FR-016**: For each model in a parallel call, the system MUST route only the input
  modalities declared in that model's Capability Matrix; unsupported modalities MUST be silently
  dropped for that model and the omission MUST be surfaced to the user in that model's panel.
- **FR-017**: A failure of one model's response MUST NOT delay or cancel responses from other
  models in the same parallel call.
- **FR-018**: The system MUST display each model's response in a distinct, labelled panel that
  shows streaming progress, completion status, or error state.
- **FR-019**: The conversation history (prior turns) MUST be sent as context with each
  follow-up prompt to all selected models.

**Capability Matrix & Model Catalogue**

- **FR-020**: The Capability Matrix MUST be defined per model (not per provider brand); a
  provider's brand name is metadata on the model, not the primary identifier.
- **FR-021**: The Capability Matrix MUST be extensible: adding new capability dimensions MUST
  NOT require changes to existing model adapters that do not support the new dimension
  (capability defaults to "unsupported" if absent).
- **FR-022**: The model catalogue MUST be updatable by platform operators without an
  application code change — inserting a new row into `model_definitions` via a Flyway
  migration or direct SQL is sufficient; no rebuild or redeployment of application code
  is required to add a new model.

### Key Entities

- **User**: Platform account with email, verification status, authentication credentials.
- **ProviderApiKey**: Encrypted API key belonging to a user, scoped to a provider brand.
- **ModelDefinition**: Platform-managed record for a specific model version (e.g.,
  "gpt-4o-2024-11-20"), including provider brand, display name, and Capability Matrix.
- **CapabilityMatrix**: Structured declaration attached to a ModelDefinition listing supported
  input modalities, output modalities, context limit, streaming flag, function-calling flag,
  and any future dimensions.
- **UserModelConfig**: Join between a User and a ModelDefinition, storing activation status
  and referencing the applicable ProviderApiKey.
- **ChatSession**: An ordered sequence of turns belonging to a user.
- **ChatTurn**: A single user prompt plus the set of ProviderResponses generated for it.
- **ProviderResponse**: One model's response (streamed tokens, final status, latency, token
  counts) for a given ChatTurn.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user can complete registration, email verification, and first login in
  under 3 minutes with no assistance.
- **SC-002**: A user can add their first API key and activate a model in under 2 minutes after
  logging in for the first time.
- **SC-003**: After a user submits a prompt, the first token from the fastest responding model
  appears in under 3 seconds (assuming provider latency is within normal range).
- **SC-004**: Slow models do NOT delay fast models: the fastest model's first token arrives no
  later than if it were called alone (within a 200ms tolerance for platform overhead).
- **SC-005**: A failure of any single model's response has zero impact on the response
  completion of all other models in the same parallel call.
- **SC-006**: Users can configure and activate at least 6 different model providers (Kimi,
  DeepSeek, MiniMax, GLM, OpenAI, Anthropic Claude) without encountering provider-specific UI
  or logic outside the provider's own adapter.
- **SC-007**: The Capability Matrix for any given model is visible to the user in under one
  screen interaction (no more than one click/tap from the model list).
- **SC-008**: An image submitted alongside a prompt is correctly routed only to models whose
  Capability Matrix declares image-input support; text-only models receive only the text
  portion and this is communicated to the user in the same view.
- **SC-009**: All user API keys are stored encrypted; no plaintext key appears in any log,
  database query output, or API response under any circumstances.
- **SC-010**: Adding a new LLM provider to the platform catalogue requires changes only to
  a single new adapter module and a configuration entry — zero changes to orchestration,
  routing, or UI code.

---

## Assumptions

- Email delivery service is available in the deployment environment (SMTP or third-party).
- The platform operator pre-populates the model catalogue with at least the six named providers
  before any user can configure models; users cannot add arbitrary model definitions.
- Video input in the Capability Matrix is declared and routed correctly, but the MVP does not
  require testing with actual video files if no configured provider supports it at launch.
- Multi-turn conversation context is sent as a flat list of prior turns; advanced context
  management (summarization, truncation) is out of scope for this feature.
- The platform does not manage provider billing; users are responsible for their own API costs.
- Social login (Google, GitHub) is out of scope for this feature.

## Out of Scope

- **Session bookmarking, sharing links, and export**: Users cannot yet name/bookmark sessions
  for later retrieval across logins or generate shareable URLs. Basic session persistence
  (turns + responses stored in DB for multi-turn context per FR-019) IS in scope and required
  for conversation continuity.
- **Cross-run diff comparison**: Comparing results across multiple re-runs of the same prompt
  is a separate feature. Immutable turn storage (this feature) lays the groundwork.
- Usage and satisfaction analytics / rating system (separate feature).
- Function-calling / tool-use execution (Capability Matrix declares support, but invocation
  is not part of this feature).
- Password reset / "forgot password" flow (separate auth feature).
- Admin panel for managing the model catalogue.
