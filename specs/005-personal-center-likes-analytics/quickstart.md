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
2. **Profile**: change display name → inline success; reload `GET /api/v2/me` reflects `displayName`.
3. **Security**: change password with correct current + valid new → `200 password_updated`.
   - In a second browser/incognito logged in as the same user beforehand: its next request returns
     `401` (other sessions invalidated — FR-002).
   - Old password no longer logs in; new password does.
4. **Email verification**: with an unverified user, click *Resend* → `202 verification_sent`; rapid
   repeat → `429 rate_limited`. Follow the emailed link → status flips to verified.
5. Follow **Manage models** → lands on `/settings/models` via in-app nav.

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
3. Anonymous like a response (`POST /api/v2/shared/{token}/responses/{rid}/like` with a
   client-generated `visitorToken`) → `anonymousLikeCount` increments, `likedByThisVisitor:true`
   (SC-004).
4. Repeat with the same `visitorToken` → count unchanged (best-effort dedup, FR-016).
5. As owner, revoke (`DELETE .../shares/{token}`) → reopening `/share/{token}` returns 404 (FR-017).
6. Delete the conversation while shared → link 404s, no 500 (edge case).

## Scenario 4 — Usage analytics dashboard (US4)

1. Open **Personal Center → Analytics**.
2. `GET /api/v2/analytics/summary` + `by-model` + `by-session` render breakdowns: response count,
   avg/p95 latency, token usage, success rate, estimated cost (`—` where pricing unknown) (FR-021).
3. Drill into per-response detail (`GET /api/v2/analytics/responses`) → shows user-scoped rows with
   time, model, latency, consumption, outcome, **IP**, connection (FR-022).
4. Apply a time-range and a model filter → metrics update (FR-023; SC-006).
5. Confirm only your own data appears — no other user's rows/IPs (FR-024; SC-006).
6. As a brand-new user with no history → friendly empty state, not an error (SC-008).
7. Generate a new response (incl. an error) → it appears as exactly one row; response count == stats
   row count (SC-005); streaming latency unaffected (SC-007).

## Privacy spot-checks (Constitution V/VI)

- No `client_ip` or user identity appears in any `by-model` / `by-session` / summary payload.
- The public `/api/v2/shared/{token}` payload contains zero identity/IP/named-like fields.
- Share tokens are opaque; revoked or deleted sessions are inaccessible.
