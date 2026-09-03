# Research: Anonymous Chat and Model Access Management

## Decision 1: Use dedicated public endpoints

**Decision**: Add explicit public model and anonymous-chat endpoints under `/api/v2/anonymous/**`. Keep `/api/v2/chat/**`, configured-model endpoints, account history, media, tools, and share endpoints authenticated.

**Rationale**:

- The existing chat service creates `chat_sessions`, `chat_turns`, and `provider_responses` owned by a user, so making its authentication nullable would mix two persistence models and weaken authorization boundaries.
- The existing configured-model DTOs are designed for authenticated users and can contain connection-related fields that must never be public.
- Exact `permitAll` paths make the security boundary reviewable and avoid accidentally exposing other chat operations.

**Alternatives rejected**:

- Treating an anonymous visitor as a sentinel user would create server history and complicate isolation, retention, and sharing.
- Reusing the authenticated chat route with an optional principal would make persistence and media/tool behavior easy to bypass accidentally.

## Decision 2: Store an independent allowlist flag on configured models

**Decision**: Add `configured_models.is_anonymous_allowed BOOLEAN NOT NULL DEFAULT FALSE`. Anonymous eligibility is the conjunction of:

```text
connection.is_builtin = true
AND configured_model.is_enabled = true
AND configured_model.is_anonymous_allowed = true
```

**Rationale**:

- The configured-model UUID remains the operational identity and existing `is_enabled` keeps its authenticated display meaning.
- Built-in connection ownership is the existing administrator-controlled boundary; an administrator-owned personal/BYOK model must not become public merely because its owner is an administrator.
- Keeping normal display and anonymous access independent supports the requested bulk show/hide and allow/revoke operations without hidden side effects.

The public DTO contains only the UUID, provider model ID, display name, protocol, and safe capability metadata. It omits base URLs, keys, custom parameters, ownership, and private connection details. Public responses use `Cache-Control: no-store` so revocations are not made durable by an intermediary.

## Decision 3: Anonymous execution is ephemeral and text-only in the first release

**Decision**: A public turn receives a bounded client conversation context and streams provider responses, but does not create a server session, turn, response, media record, tool invocation, or share target. The first release accepts text prompts and user/assistant history only; attachments and server-side tools remain authenticated-only.

**Rationale**:

- Existing media records require an authenticated owner, and tool invocations are tied to persisted sessions/turns.
- The provider adapter registry, connection endpoint validation, capability filtering, concurrent orchestrator, and normalized SSE event model can be shared through a narrow `LlmTurnRunner` seam.
- The server rechecks the model allowlist immediately before dispatch, so revocation affects new turns even if a browser has a stale catalogue.

The browser is responsible for local continuity. It sends only user/assistant messages as untrusted user content, with strict prompt/history byte and turn limits. It does not send system prompts, provider configuration, API keys, or tool state.

## Decision 4: Protect public execution with database-backed, stateless limits

**Decision**: Introduce dedicated anonymous defaults for request rate, active streams, prompt/history size, selected-model count, and provider execution duration. Derive the client key from the trusted client IP and the existing anonymous HMAC secret; never persist the raw IP. Reuse the existing atomic fixed-window throttle pattern for request rate and add expiring slot rows for active-stream concurrency.

**Rationale**:

- The current auth throttle is request-count based and does not provide anonymous concurrency or provider-duration protection.
- JVM-local semaphores would fail open or behave inconsistently across horizontally scaled instances.
- A small `(client_key_hash, slot_no)` lease table can be claimed with an atomic expiry-aware update/insert and released on stream termination; expiry recovers slots after crashes without a distributed lock.

The guard runs before SSE headers are committed. Exceeded limits return non-sensitive `429`/`400` responses. Provider execution uses a hard deadline and emits a safe stream error if the deadline is reached. The allowlist check always runs independently of the limits.

The defaults are operational configuration, not a new administrator quota UI. Initial defaults should be conservative and covered by tests; exact values belong in application configuration and deployment documentation rather than in the API contract.

## Decision 5: Synchronize local conversations through an authenticated, idempotent import

**Decision**: After the existing registration flow logs the newly created user in, the frontend posts a batch of local conversations to `/api/v2/anonymous/conversations/sync`. Each conversation has a browser-generated stable UUID and a canonical payload SHA-256 digest. The backend uses `(user_id, source_conversation_id)` as the idempotency key and processes each conversation in its own transaction.

**Rationale**:

- The backend cannot read browser `localStorage`; synchronization must happen after login in the frontend.
- A dedicated identity table avoids overloading globally unique `chat_turns.client_request_id` and makes retry behavior explicit.
- A transaction per conversation prevents one malformed or unsupported conversation from rolling back the whole registration migration.

Same identity plus the same digest returns `already_imported` and the existing session ID. Same identity plus a different digest returns a conflict. The frontend removes local data only for `imported` or `already_imported` results with a session ID; all other results remain retryable.

The import copies supported prompts and immutable response snapshots and never invokes a provider. It does not grant access to a model. A response state or attachment unsupported by the authenticated schema produces a visible `skipped` result and leaves the source conversation local. Automatic synchronization is only part of the registration flow; normal login does not silently merge browser data into an unrelated account.

## Decision 6: Freeze bulk-operation scope before execution

**Decision**: Add a cross-connection `GET /api/v2/admin/models` and a two-step bulk resource: preview a selection, then execute the preview by operation ID. Selection supports explicit IDs or a filter plus exclusions. The preview materializes target snapshots, so the confirmation count and execution scope are identical.

**Rationale**:

- The current admin model UI is nested under individual connections and has no cross-connection selection.
- Recomputing a filter during execution could silently include or exclude models after the administrator confirmed it.
- Per-item outcomes and a stored operation summary make partial failures visible and retryable without putting hundreds of IDs into an audit metadata field.

The operation is bounded (initially a configurable maximum of 1,000 targets), idempotent, and safe for repeated execute requests through `Idempotency-Key`. `ALLOW_ANONYMOUS` and `REVOKE_ANONYMOUS` only change the new flag; `SHOW` and `HIDE` only change `is_enabled`; `DELETE` removes the configured model but retains response history because provider responses are snapshots without a configured-model foreign key. The operation records safe model snapshots before deletion and writes an audit summary for the administrator.

## Decision 7: Keep frontend routing and sharing boundaries explicit

**Decision**: Move the chat entry route out of the authentication-redirecting `(app)` layout, then select authenticated server sessions or anonymous local conversations after client auth state is known. Keep account/admin routes protected and continue using the existing same-origin Next proxy.

Anonymous conversation actions do not render share controls or call share APIs. A successfully imported conversation has a server session ID and can then use the existing authenticated sharing rules. Local-only state has no server ID and cannot produce a share URL.
