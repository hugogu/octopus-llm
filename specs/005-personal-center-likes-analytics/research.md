# Phase 0 Research: Personal Center, Response Likes & Usage Analytics

All Technical Context items were resolvable from the existing codebase and the resolved
clarifications; there are no open `NEEDS CLARIFICATION` markers. The notable decisions follow.

## D1 — Analytics: extend the immutable `provider_responses` table; do NOT add a duplicated statistics table

**Decision**: Treat the existing `provider_responses` table as the immutable, append-once,
one-row-per-(turn,model) **statistics source of truth**. It already captures: `user` (via
`turn → session.user_id`), `latency_ms`, `model_id` + `model_display_name`, `input_tokens` /
`output_tokens`, `status` (`complete`/`error`), `connection_label`, `protocol`, and `created_at`
(see `V008` + `V015` + `V017`). The only genuinely missing dimensions are **client IP**, a stable
**connection_id** (only a label snapshot exists today), and **cost** (no pricing data exists).

Resolve the gaps with the least surface:
- **Client IP** is a *request-level* attribute → add `client_ip INET` to `chat_turns`, captured from
  the `ServerWebExchange` when a turn is submitted.
- **connection_id** snapshot → add nullable `connection_id UUID` to `provider_responses` (a plain
  snapshot column, **no** FK cascade, so deleting a connection never mutates immutable history).
- **Cost** is **derived at read time** = `tokens × per-model price` using new nullable pricing
  columns on `model_definitions`; display `—` when price is unknown.
- The analytics dashboard is served by **read-only aggregation queries** (`GROUP BY` model / session)
  over `provider_responses` joined to `chat_turns`/`chat_sessions`, scoped to the caller's `user_id`.

**Rationale**: Constitution IV (immutable sessions — a new mutable/duplicated write path would risk
divergence), V (analytics read-only, off the hot write path, IP excluded from aggregate), and VII
(YAGNI/simplicity — every response *already* produces exactly one immutable record, satisfying
FR-019/FR-020 without a second table to keep in sync). The spec's Assumption "a dedicated statistics
record is introduced because the existing per-response storage does not capture IP/connection/cost"
is satisfied by extending that immutable record with the two missing snapshot columns rather than
copying every field into a parallel table.

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
- `anonymous_response_likes (response_id, visitor_token, created_at)` with
  `UNIQUE (response_id, visitor_token)` → best-effort-deduped anonymous count (FR-014/016). The
  `visitor_token` is an opaque client-generated UUID (cookie/localStorage), **not linkable** to any
  account (FR-015). Count = `COUNT(*)`.

**Rationale**: A UNIQUE constraint gives idempotency/dedup without locks (Constitution VII) and
without mutating `provider_responses` (Constitution IV). Counting rows avoids a hot counter column
and the lost-update problem. Anonymous tokens carry no identity (Constitution VI).

**Alternatives considered**:
- *Single integer counter column on `provider_responses`* — rejected: mutates the immutable table and
  invites concurrent lost updates.
- *Redis counter* — rejected (YAGNI / no new infra; Postgres handles this volume).

## D3 — Session sharing (opaque, revocable, no expiry)

**Decision**: `session_shares (id, session_id, token, created_at, revoked_at)` where `token` is a
high-entropy opaque string (UNIQUE), `revoked_at` NULL = active. No expiry column (clarified:
revoke-only). Public endpoint `GET /api/v2/shared/{token}` returns the session, turns, and per-model
responses through an **anonymous-safe DTO** (no `user_id`, no IP, no named-like breakdown — only the
anonymous like count and the caller's own anonymous-liked state). `ON DELETE CASCADE` from
`chat_sessions` so deleting the conversation kills the link (edge case + FR-017).

**Rationale**: Constitution VI mandates opaque share tokens with no identity. Revoke-only lifecycle is
the clarified requirement and the simplest correct model (no scheduled expiry job).

**Alternatives considered**: signed/expiring JWT share tokens — rejected: encodes claims and adds
expiry the user explicitly declined; an opaque DB-checked token is simpler and revocable.

## D4 — Authenticated password change + invalidate all other sessions

**Decision**: Add `POST /api/v1/auth/password-change` (current + new password, authenticated). On
success: update the hash and set a per-user `sessions_valid_from = now()` on `users`.
`JwtTokenService.validate` additionally rejects any token whose `iat` (issued-at) is **before**
`user.sessions_valid_from`. The fresh token minted by the next login has `iat ≥ sessions_valid_from`,
so all previously issued tokens (other devices) become invalid (FR-002, edge case).

**Rationale**: A single timestamp invalidates an unbounded set of tokens with no jti enumeration and
no distributed lock (Constitution VII), and complements the existing per-jti `revoked_tokens`
(explicit single-session logout). jjwt already exposes `iat`/claims.

**Alternatives considered**:
- *Insert every active jti into `revoked_tokens`* — rejected: requires tracking all live jtis per
  user (not stored) and unbounded writes.
- *Rotate a per-user signing key* — rejected: over-engineered for this need.

## D5 — Model pricing for cost estimates

**Decision**: Add nullable `input_price_per_mtok NUMERIC`, `output_price_per_mtok NUMERIC`,
`price_currency VARCHAR(3)` to `model_definitions`. Seed where confidently known; leave NULL
otherwise. Cost = `(input_tokens/1e6)*input_price + (output_tokens/1e6)*output_price`, computed in the
analytics read path; surface `—` and exclude from cost sums when price or tokens are NULL.

**Rationale**: Matches the clarified "token-primary + estimated cost where pricing known; no
billing-grade catalog" decision. Pricing lives next to the model definition it describes; nullable
keeps it best-effort. No new entity needed (Constitution VII).

**Alternatives considered**: a separate `model_pricing` table with effective-dated rows — deferred
(YAGNI): single current price per model is sufficient for an *estimate*; historical re-pricing is out
of scope.

## D6 — Email-verification resend + rate limiting

**Decision**: Add `POST /api/v1/auth/verify-email/resend` (authenticated). Reuse the existing
`EmailService` / `EmailVerification` flow. Enforce a best-effort cooldown (e.g. reject if the user's
most recent verification was issued within N seconds) keyed off the existing verification record's
timestamp. `GET /api/v2/me` returns `emailVerified` so the UI can show status (FR-003/007).

**Rationale**: Reuses existing verification machinery; a timestamp-based cooldown is lock-free and
sufficient to prevent trivial abuse (Constitution VII; spec edge case).

## D7 — Like control placement & response identity in the frontend

**Decision**: Render the like button inside `ModelResponsePanel`'s header (next to `CopyButton`). A
like targets a persisted `provider_response`, so the control is enabled only once a response is
`complete`/`error` and has a server `responseId`. Extend the chat session GET DTO and the
`turn_created`/completion flow so each response carries its `responseId` plus
`{ likeCount, likedByMe }`. Live, not-yet-persisted streaming responses show the control in a
disabled/pending state until persisted.

**Rationale**: Likes are per `provider_response` (D2); the existing panel already renders per-model
state and is the natural, consistent home (Constitution VIII). No new layout invented.

## D8 — Frontend Personal Center shell & navigation (Connected)

**Decision**: Add `AccountShell` (mirroring `AdminShell`: warm gradient canvas, eyebrow+title header,
pill tab bar, back-to-chat link) with tabs Profile / Security / Analytics under `(app)/account`. Add a
nav entry into the account hub from the chat surface (alongside the existing model-settings/admin
links), with active-section state. The model-management link points at the existing
`/settings/models`. The public shared view at `(app)/share/[token]` reuses the read-only response
presentation but with the anonymous-safe DTO.

**Rationale**: Constitution VIII (Connected — no URL-only pages; reuse the design system). Mirroring
`AdminShell` guarantees visual consistency with minimal new code.
