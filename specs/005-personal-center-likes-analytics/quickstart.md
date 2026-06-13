# Quickstart: Personal Center, Response Likes & Usage Analytics

End-to-end validation guide. Implementation details live in `tasks.md` (Phase 2) and the code; this
file proves the feature works. See [data-model.md](data-model.md) and [contracts/](contracts/) for
shapes.

## Prerequisites

- Local stack running via Docker Compose (frontend `:3001`, backend `:8080`, Postgres). Check
  `docker ps` / `lsof -i :3001 -i :8080` before starting anything new.
- Flyway migrations `V021`–`V026` applied (backend boot runs them).
- At least one registered, verified user with ≥1 saved conversation containing multi-model responses
  (use existing chat flow to generate some history, including at least one error response).

## Gate commands (must pass before "done")

```bash
# Backend: compilation + unit/integration tests (Testcontainers Postgres)
cd backend && ./gradlew build

# Frontend: strict type-check
cd frontend && npx tsc --noEmit
```

Plus **visual verification** of every new page in the browser (Constitution VIII) — type-check alone
is insufficient.

## Scenario 1 — Personal Center (US1)

1. Log in, open in-app nav → **Personal Center** is visible with active-section state (no URL typing).
2. **Profile**: change display name → inline success; reload `GET /api/v2/me` reflects `displayName`
   and `emailVerificationStatus`.
3. **Security**: change password with correct current + valid new via `POST /api/v2/me/password` →
   `200 password_updated` plus a replacement token that the current browser stores atomically.
   - In a second browser/incognito logged in as the same user beforehand: its next request returns
     `401` (other sessions invalidated — FR-002).
   - The current browser remains signed in with the replacement token.
   - Old password no longer logs in; new password does.
4. **Password reset**: from sign-in, request a reset for an existing and unknown address → both return
   the same `202 accepted`; follow the valid email link once → password changes; reuse/expiry fails.
5. **Email verification**: with an unverified user, click *Resend* → `202 verification_sent`; rapid
   repeat → `429 rate_limited`. Follow the emailed link → status flips to verified.
6. Follow **Manage models** → lands on `/settings/models` via in-app nav. Set optional input/output
   price + currency on a configured model.

**Expected**: every action shows loading + success/error state; no dead clicks (SC-001, SC-002).

## Scenario 2 — Named likes (US2)

1. Open a saved conversation with several model responses.
2. Like one response → liked state + `likeCount` increments within ~1s (`PUT
   /api/v2/responses/{id}/like` → `likedByMe:true`).
3. Reload → like persists (`GET .../sessions/{id}` shows `likedByMe:true`, `likeCount`) (SC-003).
4. Like the same response again (or re-PUT) → count unchanged (idempotent, FR-009).
5. Un-like (`DELETE`) → count decrements, `likedByMe:false`.
6. Delete the conversation → its likes are gone (no orphans; FR-011).

## Scenario 3 — Sharing + anonymous likes (US3)

1. As owner, create a share link (`POST .../sessions/{id}/shares`) → opaque `token` + `shareUrl`.
   Confirm the token/URL contain no user id/email (FR-012).
2. Open `/share/{token}` in an incognito window (logged out) → conversation prompts + responses render
   read-only; **no** identity, IP, or named-like detail present (FR-013/FR-015).
3. Confirm the first public read issues an HttpOnly visitor cookie. Anonymous-like with an empty-body
   `POST /api/v2/shared/{token}/responses/{rid}/like` → `anonymousLikeCount` increments,
   `likedByThisVisitor:true` (SC-004).
4. Repeat from the same browser → count unchanged (best-effort dedup, FR-016); verify arbitrary
   caller-supplied visitor IDs are ignored.
5. Sign in as a non-owner with the share link and use token-scoped `PUT` → a named like is recorded.
6. As owner, revoke (`DELETE .../shares/{token}`) → reopening `/share/{token}` returns 404 (FR-017).
7. Delete the conversation while shared → link 404s, no 500 (edge case).

## Scenario 4 — Usage analytics dashboard (US4)

1. Open **Personal Center → Analytics**.
2. `GET /api/v2/analytics/summary` + `by-model` + `by-session` render breakdowns: response count,
   avg/p95 latency, token usage, success rate, and estimated costs separated by currency (`—` where
   pricing is unknown) (FR-021/FR-030).
3. Drill into per-response detail (`GET /api/v2/analytics/responses`) → shows user-scoped rows with
   time, model, latency, consumption, outcome, **IP**, connection (FR-022).
4. Apply a time-range and configured-model UUID filter → metrics update (FR-023; SC-006).
5. Confirm only your own data appears — no other user's rows/IPs (FR-024; SC-006).
6. As a brand-new user with no history → friendly empty state, not an error (SC-008).
7. Generate success and error terminal outcomes → each appears as exactly one persisted row; terminal
   event count equals persisted-record count (SC-005); stream-start latency stays within SC-007.
8. Change or delete the configured model pricing → historical response estimates remain unchanged.

## Scenario 5 — Public anonymous model analytics (US5)

1. Log out and open `/analytics` → the page loads without redirecting to `/login`.
2. `GET /api/v2/analytics/public/by-model?page=0&size=25` returns the standard page envelope with
   protocol + literal model ID aggregates, latency/tokens/success, and named/anonymous like totals.
3. Confirm the payload contains no user/session/IP/connection/configured-model/user-label/prompt/
   response fields (FR-029; SC-009).
4. Verify `size=101` is rejected and an empty filter result renders a friendly empty state.

## Privacy spot-checks (Constitution V/VI)

- No `client_ip`, user identity, connection field, or configured-model UUID appears in any personal
  aggregate payload.
- The public `/api/v2/shared/{token}` payload contains zero identity/IP/connection/named-like fields.
- The public analytics payload contains only protocol + literal model aggregates and satisfaction
  totals.
- Share tokens are opaque; revoked or deleted sessions are inaccessible.
- Browser requests use same-origin `/api/...`; verify the proxy preserves each upstream path exactly
  with real HTTP requests from the published frontend origin.

## Implementation verification record (2026-06-13)

- `cd backend && ./gradlew test`: reached 59 tests; the only failures were the legacy registration
  expectation and the new reaction fixture's PostgreSQL `INET` binding. Both were fixed and their
  focused integration tests passed afterward.
- Focused passing backend gates:
  - `AuthControllerTest` and `ReactionControllerTest`
  - `ShareControllerTest` (opaque active share, public read, HttpOnly cookie, repeated-like dedup)
  - `UserPreferenceRepositoryTest` after Flyway/JPA schema validation
- Frontend verification with the required Node.js 24 runtime:
  - `node node_modules/vitest/vitest.mjs --run`: 25 test files and 112 tests passed.
  - `node node_modules/typescript/bin/tsc --noEmit`: passed.
  - `node node_modules/eslint/bin/eslint.js .`: passed.
- `cd frontend && npm run build`: passed with all new routes (`/account`, `/account/security`,
  `/account/analytics`, `/analytics`, `/forgot-password`, `/share/[token]`).
- Docker Compose was already running on frontend `:3001`, backend `:8080`, PostgreSQL `:5432`, and
  MailHog `:8025`. Rebuilding the images was attempted but the execution sandbox denied access to the
  Docker socket; the required escalation was then rejected because the environment approval quota
  was exhausted.
- A final full backend build and the new private-analytics integration test were also blocked by that
  exhausted escalation quota. No source or test failure was returned by those blocked commands.
- Browser visual verification was not performed against the already-running stack because it still
  contained the pre-change images and Docker rebuild access was unavailable.
