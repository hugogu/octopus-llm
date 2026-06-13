# Phase 0 Research: Personal Center, Response Likes & Usage Analytics

All Technical Context items were resolvable from the existing codebase and the resolved
clarifications; there are no open `NEEDS CLARIFICATION` markers. The notable decisions follow.

## D1 — Analytics: extend the immutable `provider_responses` table; do NOT add a duplicated statistics table

**Decision**: Treat the existing `provider_responses` table as the immutable, append-once,
one-row-per-(turn,model) **statistics source of truth**. It already captures: `user` (via
`turn → session.user_id`), `latency_ms`, `model_id` + `model_display_name`, `input_tokens` /
`output_tokens`, `status` (`complete`/`error`), `configured_model_id`, `connection_label`, `protocol`,
and `created_at` (see `V008` + `V015` + `V017`). The missing dimensions are **client IP**, a stable
**connection_id**, and the **pricing snapshot** needed for stable historical estimates.

Resolve the gaps with the least surface:
- **Client IP** is a *request-level* attribute → add `client_ip INET` to `chat_turns`, resolved from
  the direct peer or an explicitly trusted proxy chain. Arbitrary forwarding headers are ignored.
- **connection_id** snapshot → add nullable `connection_id UUID` to `provider_responses` (a plain
  snapshot column, **no** FK cascade, so deleting a connection never mutates immutable history).
- **Configured pricing** lives on `configured_models`, the operational model identity after V017
  dropped `model_definitions`. At dispatch, nullable input/output prices and currency are copied onto
  `provider_responses`; cost is derived at read time from those immutable snapshots.
- The analytics dashboard is served by **read-only aggregation queries** (`GROUP BY` model / session)
  over `provider_responses` joined to `chat_turns`/`chat_sessions`, scoped to the caller's `user_id`.
- Public analytics use a separate projection grouped only by `protocol + model_id`; the projection
  excludes personal, session, connection, configured-model, prompt, and response-content fields.
- Refactor terminal stream handling so `complete`/`error` response persistence is awaited before the
  corresponding terminal SSE event is forwarded. Token/reasoning chunks continue streaming
  immediately; the unique `(turn_id, configured_model_id)` constraint and retry of transient database
  failures prevent duplicate/missing terminal records without adding a second statistics write.

**Rationale**: Constitution IV (immutable sessions — a new mutable/duplicated write path would risk
divergence), V (analytics read-only, off the hot write path, IP excluded from aggregate), and VII
(YAGNI/simplicity — every response *already* produces exactly one immutable record, satisfying
FR-019/FR-020 without a second table to keep in sync). The existing response row is the statistics
record; extending its insert-time snapshot keeps historical estimates stable when configured pricing
changes or a configured model is deleted.

**Alternatives considered**:
- *Separate `response_statistics` table mirroring every field* — rejected: duplicates immutable data,
  adds a second write in the response-completion path, and risks drift (Constitution IV/V/VII).
- *Materialized view / rollup table* — deferred (YAGNI): live aggregation meets the <2s/1,000-rows
  target with proper indexes; revisit only if scale demands it.

## D2 — Named likes vs anonymous likes

**Decision**: Two tables.
- `response_likes (response_id, user_id, created_at)` with `UNIQUE (response_id, user_id)` →
  idempotent, toggleable named likes (FR-008/009/010). `ON DELETE CASCADE` from both
  `provider_responses` and `users` (FR-011).
- `anonymous_response_likes (response_id, visitor_key_hash, created_at)` with
  `UNIQUE (response_id, visitor_key_hash)` → best-effort-deduped anonymous count (FR-014/016).
  The public share endpoint issues a random HttpOnly, SameSite cookie when absent. The stored key is
  an HMAC scoped to the share token; the raw cookie is never stored or linked to an account.

**Rationale**: A UNIQUE constraint gives idempotency/dedup without locks (Constitution VII) and
without mutating `provider_responses` (Constitution IV). A server-issued cookie prevents callers from
choosing a fresh token on every request, while the scoped digest avoids storing a reusable identifier.
Counting rows avoids a hot counter column and the lost-update problem.

**Alternatives considered**:
- *Single integer counter column on `provider_responses`* — rejected: mutates the immutable table and
  invites concurrent lost updates.
- *Redis counter* — rejected (YAGNI / no new infra; Postgres handles this volume).

## D3 — Session sharing (opaque, revocable, no expiry)

**Decision**: `session_shares (id, session_id, token, created_at, revoked_at)` where `token` is a
high-entropy opaque string (UNIQUE), `revoked_at` NULL = active. No expiry column (clarified:
revoke-only). Public endpoint `GET /api/v2/shared/{token}` returns the session, turns, and per-model
responses through an **anonymous-safe DTO** (no `user_id`, IP, configured-model UUID, connection
details, or named-like breakdown — only the anonymous like count and caller-like state).
`ON DELETE CASCADE` from
`chat_sessions` so deleting the conversation kills the link (edge case + FR-017).

The public frontend route lives under `(public)/share/[token]`, not `(app)`, because the existing
`(app)` layout redirects requests without `auth_token` to `/login`.

**Rationale**: Constitution VI mandates opaque share tokens with no identity. Revoke-only lifecycle is
the clarified requirement and the simplest correct model (no scheduled expiry job).

**Alternatives considered**: signed/expiring JWT share tokens — rejected: encodes claims and adds
expiry the user explicitly declined; an opaque DB-checked token is simpler and revocable.

## D4 — Authenticated password change + invalidate all previous credentials

**Decision**: Add `POST /api/v2/me/password` (current + new password, authenticated). In one
transaction, update the hash and increment the existing `users.session_epoch`. Return a fresh JWT
carrying the incremented epoch so the current browser stays signed in; every previously issued token
is rejected by the existing `SecurityConfig` epoch check (FR-002).

**Rationale**: `session_epoch` already invalidates an unbounded set of tokens with no jti enumeration
or distributed lock (Constitution VII) and complements per-jti `revoked_tokens` logout. Reusing it
avoids a duplicate invalidation mechanism.

**Alternatives considered**:
- *Add `sessions_valid_from`* — rejected: duplicates the existing epoch mechanism and introduces
  timestamp precision edge cases.
- *Insert every active jti into `revoked_tokens`* — rejected: live jtis are not tracked and writes
  would be unbounded.

## D5 — Configured-model pricing and immutable response snapshots

**Decision**: Add nullable `input_price_per_mtok NUMERIC`, `output_price_per_mtok NUMERIC`, and
`price_currency VARCHAR(3)` to `configured_models`. Manual entry remains optional for user-owned and
administrator-owned built-in configured models. Copy these values to immutable nullable columns on
each `provider_response` at dispatch. Cost =
`(input_tokens/1e6)*input_price + (output_tokens/1e6)*output_price`; surface `—` when required values
are unknown and aggregate totals separately by currency.

**Rationale**: V017 removed `model_definitions` and made configured-model UUID the operational
identity. Pricing therefore belongs to the configured model, while response snapshots prevent later
edits/deletion from rewriting historical estimates. No price catalogue or new entity is needed.

**Alternatives considered**: a separate effective-dated price catalogue — rejected as billing-grade
scope the user did not request. Reading current configured-model pricing during analytics — rejected
because it makes history change and fails after model deletion.

## D6 — Email-verification resend + rate limiting

**Decision**: Add `POST /api/v2/me/email-verification/resend` (authenticated). Reuse the existing
`EmailService` / `EmailVerification` flow. Enforce a best-effort cooldown (e.g. reject if the user's
most recent verification was issued within N seconds) keyed off the existing verification record's
timestamp. `GET /api/v2/me` returns both the existing boolean and a derived
`emailVerificationStatus` (`verified`/`pending`/`unverified`) (FR-003/007).

Registration currently sets `email_verified = true` and sends no verification email. Change it to
create users as unverified, issue a verification row, and send the email. Resend atomically invalidates
prior unused verification rows before issuing the replacement.

Add public `POST /api/v1/auth/password-reset/request`, returning the same `202` response whether the
email exists or not, and reuse the existing `password_resets` + confirm flow. Rate-limit requests by
normalized email and trusted network source without revealing account existence (FR-031). Use a small
PostgreSQL `auth_action_throttles` table keyed by `(action, key_hash, window_started_at)` with atomic
upsert/count so throttling works across horizontally scaled instances without distributed locks.
Password-reset confirmation increments `session_epoch` after updating the password.

**Rationale**: Reuses existing verification machinery; a timestamp-based cooldown is lock-free and
sufficient to prevent trivial abuse (Constitution VII; spec edge case).

## D7 — Like control placement & response identity in the frontend

**Decision**: Render the like button inside `ModelResponsePanel`'s header (next to `CopyButton`). A
like targets a persisted `provider_response`, so the control is enabled only once a response is
`complete`/`error` and has a server `responseId`. Extend the chat session GET DTO and completion/error
SSE events so each response carries its `responseId` plus
`{ likeCount, likedByMe }`. Live, not-yet-persisted streaming responses show the control in a
disabled/pending state until the persisted terminal event arrives.

**Rationale**: Likes are per `provider_response` (D2); the existing panel already renders per-model
state and is the natural, consistent home (Constitution VIII). No new layout invented.

## D8 — Frontend Personal Center shell & navigation (Connected)

**Decision**: Add `AccountShell` (mirroring `AdminShell`: warm gradient canvas, eyebrow+title header,
pill tab bar, back-to-chat link) with tabs Profile / Security / Analytics under `(app)/account`. Add a
nav entry into the account hub from the chat surface (alongside the existing model-settings/admin
links), with active-section state. The model-management link points at the existing
`/settings/models`. Public shared and aggregate views live under `(public)` so they do not inherit the
authenticated `(app)` layout. The shared view reuses the read-only response presentation with the
anonymous-safe DTO.

**Rationale**: Constitution VIII (Connected — no URL-only pages; reuse the design system). Mirroring
`AdminShell` guarantees visual consistency with minimal new code.

## D9 — Public anonymous analytics projection

**Decision**: Add a paginated public model-aggregate endpoint grouped by `protocol + model_id`.
Metrics are response count, average/p95 latency, input/output token totals, success rate, named-like
count, and anonymous-like count. It never selects user/session/IP/connection/configured-model/prompt/
response fields.

**Rationale**: Constitution V requires anonymous aggregate analytics visible to all. A dedicated DTO
and query projection make the privacy boundary auditable and avoid serializing an owner DTO by
accident.
