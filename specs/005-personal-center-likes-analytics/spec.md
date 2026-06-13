# Feature Specification: Personal Center, Response Likes & Usage Analytics

**Feature Branch**: `005-personal-center-likes-analytics`
**Created**: 2026-06-13
**Status**: Draft
**Input**: User description: "为这个平台 搭建便于个人使用的功能。比如个人中心（能改、重置密码，验证邮件、管理模型），在对话中，添加对各个AI回答的点赞功能。当把一个会话分享出去之后，其它人也可以 对各个 回答点赞，但是其他人可能并不是注册用户，这个点赞就只是个计数，不计名。同时所有会话记录之外，如还没有，需要有一个数据统计的表，统计每个AI每个对话的用户、用时、模型名、消耗、响应结果、IP、connection等相关信息以便做数据分析面板（也是个人中心）。"

## Clarifications

### Session 2026-06-13

- Q: 「消耗」(consumption) 在统计与面板里到底要记什么？ → A: Token 用量始终记录；仅在已配置该模型单价时显示估算金额（未知显示「—」），不引入计费级价格目录
- Q: 分享链接的有效期/生命周期如何控制？ → A: 永不过期，访问权完全由所有者「撤销」控制，无自动过期
- Q: 修改密码后该用户在其它设备上的已登录会话如何处理？ → A: 改密码即使该用户其它所有已登录会话全部失效（复用 revoked_tokens）
- Q: 响应统计记录(含 IP/connection)的保留期限？ → A: 长期保留，无固定过期；仅在对应会话/用户被删除时级联清除

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Personal Center: manage my account (Priority: P1)

A registered user wants a single, self-service hub where they can review and change their own
account settings: update their display profile, change their password, re-trigger / confirm email
verification, and reach the model-management screen. Today these capabilities are scattered (some
reachable only by hand-typed URLs) and there is no single landing place.

**Why this priority**: This is the foundational "home for the user" the rest of the feature hangs
off (analytics dashboard and likes overview also live here). It delivers immediate standalone value
even if likes and analytics are never built, and it makes existing-but-hidden capabilities
discoverable, satisfying the "Connected" UX principle.

**Independent Test**: Log in, open the Personal Center from in-app navigation, change the password,
verify the old password no longer works and the new one does, resend the verification email and
confirm the verified state updates, and follow the link into model management — all without typing a
URL manually.

**Acceptance Scenarios**:

1. **Given** a logged-in user, **When** they open in-app navigation, **Then** a "Personal Center"
   destination is visible and active-state is shown when selected.
2. **Given** a user on the Personal Center, **When** they change their password with a correct
   current password and a valid new password, **Then** they see an inline success confirmation and
   subsequent logins require the new password.
3. **Given** a user with an unverified email, **When** they request a new verification email, **Then**
   they see confirmation that the email was sent and the page reflects the unverified→pending state.
4. **Given** a user who clicks the verification link in the email, **When** the link is valid and
   unexpired, **Then** their account shows as verified in the Personal Center.
5. **Given** a user with an expired or already-used verification link, **When** they open it, **Then**
   they see a clear error and an option to resend.
6. **Given** a user on the Personal Center, **When** they update their display profile (e.g. display
   name), **Then** the change is saved and reflected across the app.
7. **Given** a user on the Personal Center, **When** they choose to manage models, **Then** they reach
   the model-management screen via in-app navigation.

---

### User Story 2 - Like individual AI responses in my conversations (Priority: P2)

While comparing multiple AI answers to the same prompt, a logged-in user wants to like (upvote) the
responses they find best, on a per-response basis. The like is attributed to them (named), can be
toggled off, and persists with the session so they can later see which answers they preferred.

**Why this priority**: Likes are the explicit satisfaction signal the platform's analytics value
proposition depends on (Constitution V). They are independently demonstrable on any existing
conversation without sharing or the analytics dashboard.

**Independent Test**: Open a saved conversation with several model responses, like one response,
reload the page and confirm the like persisted and is shown as "liked by me", then un-like it and
confirm the count decrements.

**Acceptance Scenarios**:

1. **Given** a logged-in user viewing a conversation with multiple per-model responses, **When** they
   like a specific response, **Then** that response shows a liked state and an incremented like count.
2. **Given** a user who already liked a response, **When** they like it again (toggle), **Then** the
   like is removed and the count decrements.
3. **Given** a user who liked a response, **When** they revisit the conversation later, **Then** their
   like is still shown.
4. **Given** a response with likes from multiple registered users, **When** the owner views it, **Then**
   the total like count reflects all registered-user likes.
5. **Given** a registered user, **When** they like the same response only once, **Then** their like is
   counted at most once (idempotent per user per response).

---

### User Story 3 - Anonymous likes on shared conversations (Priority: P3)

A user shares one of their conversations via a shareable link. Anyone with the link — including
people who are not registered users — can view the conversation and like individual responses. For
non-registered visitors the like is an anonymous counter only: it is never attributed to an identity
and never reveals who liked what.

**Why this priority**: Sharing + anonymous likes extends reach and gathers broader satisfaction
signal, but depends on likes (US2) existing first and is the most contained slice to defer.

**Independent Test**: Create a share link for a conversation, open it in a logged-out / incognito
context, like a response, and confirm the anonymous like count increments and is visible — without
any account, and without exposing any liker identity.

**Acceptance Scenarios**:

1. **Given** a conversation owner, **When** they create a share link, **Then** they receive an opaque
   shareable URL that contains no user-identifying information.
2. **Given** a visitor opening a valid share link, **When** the page loads, **Then** they can read the
   conversation's prompts and all per-model responses read-only.
3. **Given** an anonymous (non-registered) visitor on a shared conversation, **When** they like a
   response, **Then** the response's anonymous like count increments and is shown.
4. **Given** an anonymous visitor, **When** they view any response, **Then** no information about who
   liked it (identity, count of named users, etc.) is exposed.
5. **Given** a response liked by both registered users and anonymous visitors, **When** the owner views
   it, **Then** they can see the named-like total and the anonymous-like count.
6. **Given** an owner who revokes a share link, **When** a visitor opens the revoked link, **Then** the
   conversation is no longer accessible.
7. **Given** an anonymous visitor who already liked a response in their browser session, **When** they
   attempt to like it again, **Then** repeated rapid likes from the same visitor are not counted (best-
   effort de-duplication; the count is not trivially inflatable by one visitor).

---

### User Story 4 - Personal usage analytics dashboard (Priority: P2)

A registered user wants a data-analysis dashboard in their Personal Center that summarizes, per AI
model and per conversation, the relevant facts of every response they generated: which user, time
taken (latency), model name, consumption (token usage and, where known, estimated cost), the response
outcome (success/error), the originating IP, and the connection used. This lets them understand which
models perform best for them, what they are spending, and where errors occur.

**Why this priority**: This is a primary, explicitly requested deliverable and the reason the
statistics records exist. It is independently testable from a populated history and delivers
standalone analytical value. It is P2 alongside likes because both depend only on US1's hub.

**Independent Test**: With a history of generated responses, open the analytics dashboard in the
Personal Center and confirm it shows per-model and per-conversation breakdowns of latency, token
usage / cost, success vs error rates, and the supporting per-response detail (time, model, IP,
connection) — scoped to only the viewing user's own data.

**Acceptance Scenarios**:

1. **Given** a user with response history, **When** they open the analytics dashboard, **Then** they
   see aggregate metrics broken down by model (e.g. count, average latency, token usage, success rate).
2. **Given** the same user, **When** they view per-conversation breakdowns, **Then** each conversation
   shows its responses' models, latency, consumption, and outcome.
3. **Given** a recorded response, **When** the user inspects its detail, **Then** they can see the user,
   time taken, model name, consumption, response outcome, originating IP, and connection used.
4. **Given** a user viewing the dashboard, **When** they apply a time-range or model filter, **Then**
   the metrics update to that selection.
5. **Given** any user, **When** they view the dashboard, **Then** only their own data is shown; no
   other user's records, IPs, or identities are visible.
6. **Given** a freshly generated response, **When** the user later opens the dashboard, **Then** that
   response's statistics are included.

---

### Edge Cases

- **Password change while logged in elsewhere**: changing the password MUST invalidate all of the
  user's other active sessions/tokens, forcing re-authentication on other devices.
- **Verification email throttling**: rapid repeated "resend verification" requests MUST be rate-limited
  to prevent abuse; the user sees a "please wait" state rather than an error.
- **Liking on a session that is later deleted**: likes for a deleted conversation MUST be removed with
  it (no orphaned counters).
- **Share link to a conversation that is later deleted**: the link MUST become inaccessible, not 500.
- **Anonymous like de-duplication across browsers**: the system cannot perfectly prevent a determined
  anonymous visitor from inflating a counter across devices; the requirement is best-effort, not exact.
- **Response with no token/cost data** (e.g. a provider that did not return usage): analytics MUST
  display it gracefully (e.g. "—") rather than break aggregates.
- **Error responses**: failed model responses MUST still produce a statistics record (outcome = error)
  so error rates are measurable.
- **Empty states**: a brand-new user with no history sees a friendly empty dashboard, not a blank or
  broken page.
- **Like on a shared session by a logged-in non-owner**: treated as a named like (attributed), the same
  as US2, not as an anonymous counter.

## Requirements *(mandatory)*

### Functional Requirements

#### Personal Center (US1)

- **FR-001**: The system MUST provide a Personal Center destination reachable through in-app
  navigation, showing active-section state, for authenticated users.
- **FR-002**: Users MUST be able to change their password by supplying their current password and a new
  password that meets the platform's password policy. A successful password change MUST invalidate all
  of the user's other active sessions/tokens (via the existing token-revocation mechanism), requiring
  re-authentication on other devices.
- **FR-003**: Users MUST be able to trigger (resend) an email-verification message and see their
  current verification status (verified / unverified / pending).
- **FR-004**: The system MUST verify a user's email when they follow a valid, unexpired verification
  link, and MUST reject expired or already-used links with a clear, recoverable error.
- **FR-005**: Users MUST be able to view and update their editable profile attributes (at minimum a
  display name) from the Personal Center.
- **FR-006**: The Personal Center MUST link to model management via in-app navigation.
- **FR-007**: Email-verification resend and password-related actions MUST be rate-limited to prevent
  abuse.

#### Response Likes (US2)

- **FR-008**: Authenticated users MUST be able to like an individual AI response within a conversation,
  on a per-response (per model per turn) basis.
- **FR-009**: A registered user's like MUST be idempotent — at most one like per user per response —
  and MUST be toggleable (like / un-like).
- **FR-010**: A response's named-like count MUST reflect the number of distinct registered users who
  currently like it, and MUST persist with the conversation.
- **FR-011**: Likes MUST be removed automatically when their conversation (or the response) is deleted.

#### Sharing & Anonymous Likes (US3)

- **FR-012**: Conversation owners MUST be able to create an opaque shareable link to one of their
  conversations; the link MUST NOT encode any user-identifying information.
- **FR-013**: Anyone with a valid share link MUST be able to view the conversation's prompts and all
  per-model responses read-only, without authenticating.
- **FR-014**: Non-registered visitors MUST be able to like individual responses on a shared
  conversation; such likes MUST be recorded as an anonymous count only, with no identity attribution.
- **FR-015**: The system MUST NOT expose any liker identity or named-like detail to anonymous visitors
  of a shared conversation.
- **FR-016**: The system MUST apply best-effort de-duplication so a single anonymous visitor cannot
  trivially inflate a response's anonymous count (exact prevention is out of scope).
- **FR-017**: Owners MUST be able to revoke a share link, after which the link is no longer accessible.
  Share links MUST NOT expire automatically; access remains valid until the owner revokes it (or the
  conversation is deleted).
- **FR-018**: A logged-in non-owner who likes a response on a shared conversation MUST be recorded as a
  named like (per FR-008/FR-009), not as an anonymous count.

#### Usage Analytics (US4)

- **FR-019**: The system MUST record a statistics entry for every AI response (success and error)
  capturing at least: owning user, time taken (latency), model name/identifier, consumption (token
  usage; estimated monetary cost where pricing is known — see Assumptions), response outcome
  (success/error), originating IP address, and the connection used.
- **FR-020**: Statistics entries MUST be immutable once written (append-only), consistent with the
  platform's immutable-session principle, and MUST NOT block or slow the response hot path. Entries MUST
  be retained indefinitely (no fixed expiry) and removed only when their conversation or owning user is
  deleted (cascade).
- **FR-021**: The Personal Center MUST provide an analytics dashboard presenting, for the viewing
  user's own data, aggregate metrics broken down by model and by conversation (at minimum: response
  count, average/percentile latency, token usage / cost, and success vs error rate).
- **FR-022**: The dashboard MUST allow drilling into per-response detail showing user, time taken,
  model, consumption, outcome, IP, and connection.
- **FR-023**: The dashboard MUST support filtering by at least time range and model.
- **FR-024**: Analytics queries MUST be read-only, MUST be scoped to the requesting user's own data,
  and MUST NOT expose another user's records, IP addresses, or identities.
- **FR-025**: IP addresses and any other personal data captured in statistics MUST NOT appear in any
  cross-user / aggregate view.

#### Cross-cutting

- **FR-026**: All new user-facing surfaces (Personal Center, likes controls, share view, analytics
  dashboard) MUST follow the four UX principles (consistent, fluent, responsive, connected) and be
  visually verified before completion.
- **FR-027**: All capabilities that read or mutate personal data MUST require authentication, except
  the explicitly anonymous shared-view read and anonymous-like actions.

### Key Entities *(include if feature involves data)*

- **User Profile**: The editable, user-facing attributes of an account (display name, email,
  verification status). Extends the existing user record; does not duplicate authentication data.
- **Email Verification**: A time-bounded, single-use token associating a user with a pending email
  verification; tracks issued/expiry/used state. (May reuse existing verification mechanism.)
- **Response Like (named)**: An attributed like by one registered user on one specific response
  (response = a given model's answer within a given conversation turn). Unique per (user, response);
  removable; cascades on conversation/response deletion.
- **Anonymous Like Counter**: A per-response tally of likes from non-registered visitors. Holds a
  count, never an identity. Best-effort de-duplication keys (e.g. an opaque per-visitor browser token)
  MUST NOT be linkable to any account.
- **Share Link**: An opaque, revocable token granting read-only public access to one conversation;
  carries no user-identifying information; references its owning conversation; has an active/revoked
  state with no automatic expiry — it stays active until the owner revokes it or the conversation is
  deleted.
- **Response Statistics Record**: An immutable, append-only analytical record for one AI response,
  capturing owning user, timestamp, latency, model name/identifier, consumption (token usage; cost
  estimate where known), outcome (success/error), originating IP, and connection used. The source of
  truth for the analytics dashboard. Retained indefinitely; cascades on conversation/user deletion.
  Personal fields (IP, user) are excluded from any aggregate view.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can locate and open the Personal Center from in-app navigation in under 10 seconds
  on first attempt, without typing a URL.
- **SC-002**: 95% of password changes and email-verification resends complete with clear success/error
  feedback and no dead clicks (every action shows a loading and a result state).
- **SC-003**: A user can like or un-like a response and see the count update within 1 second, with the
  state persisting across page reloads 100% of the time.
- **SC-004**: An anonymous visitor on a shared link can read a conversation and like a response without
  creating an account, and no liker identity is ever exposed to them (verified by inspection: 0
  identity fields in the anonymous view).
- **SC-005**: Every generated AI response — success or error — produces exactly one statistics record
  (100% coverage), verifiable by comparing response count to statistics-record count.
- **SC-006**: The analytics dashboard renders per-model and per-conversation breakdowns for a history
  of at least 1,000 responses in under 2 seconds, and shows only the viewing user's data (0 cross-user
  leakage in audit).
- **SC-007**: Capturing statistics adds no user-perceptible latency to response delivery (response
  streaming start time is unchanged within measurement noise when statistics capture is enabled).
- **SC-008**: New users with no history see a friendly empty state on the dashboard rather than an
  error or blank page (0 broken empty states).

## Assumptions

- **Reuse over rebuild**: Some Personal-Center pieces already exist (login/registration, a
  reset-password flow, a "me"/profile capability, and a model-management settings page). This feature
  consolidates and surfaces them under a discoverable Personal Center rather than reinventing them, and
  adds the missing pieces (in-app navigation entry, profile editing surface, verification status UI).
- **Consumption / cost semantics**: "消耗" is interpreted as token usage (input + output tokens) as the
  always-available primary metric, plus an *estimated* monetary cost shown only where per-model pricing
  is configured/known. A full billing-grade pricing catalog is treated as out of scope; cost is a
  best-effort estimate and may display "—" when pricing is unknown.
- **"Response" granularity**: A likeable / measured "AI response" is one model's answer within one
  conversation turn (the existing per-model response unit), so a single multi-model turn yields
  multiple independently likeable, independently measured responses.
- **Statistics vs existing response records**: A dedicated statistics record is introduced because the
  existing per-response storage does not capture IP, connection, or cost; the new record is the
  analytics source of truth and is written without coupling to the response hot path.
- **Anonymous de-duplication**: Implemented best-effort (e.g. an opaque per-browser token), explicitly
  not guaranteed across devices/cleared storage; the user accepted "就只是个计数" (just a count).
- **Privacy boundary**: IP and user identity live only in user-scoped (owner-visible) views, never in
  any anonymous shared view or cross-user aggregate, per the platform's analytics privacy principle.
- **Authentication model**: Existing session/token-based auth governs all non-anonymous actions;
  anonymous actions are limited to reading a shared conversation and incrementing anonymous like
  counts.

## Out of Scope

- Billing, invoicing, or payment collection based on computed cost.
- A global / cross-user leaderboard or public aggregate analytics surface (this feature is
  user-scoped; aggregate analytics is governed separately by the observability principle).
- Editing or moderating other users' conversations or likes.
- Social features beyond like counts (comments, follows, sharing to specific named recipients).
- Exact, abuse-proof prevention of anonymous like inflation.
