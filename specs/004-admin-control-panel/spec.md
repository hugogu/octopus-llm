# Feature Specification: Admin Control Panel

**Feature Branch**: `004-admin-control-panel`  
**Created**: 2026-06-13  
**Status**: Ready for planning  
**Input**: User description: "Add admin control panel for admin user to active registered user and allocate built-in Connection with keys to specific users. Registered users can always use BYOK mode. Admin can also disable a user, reset password, etc."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage user accounts (Priority: P1)

An administrator opens a control panel that lists every registered account. From the list the administrator can activate an account, disable (and later re-enable) an account, trigger a password reset, and inspect basic account status (email, verification state, activation state, enabled/disabled state, creation date). A disabled account is immediately blocked from authenticating, while its stored data and history are preserved.

**Why this priority**: Account lifecycle control is the minimum viable administrative capability; everything else (built-in connection allocation) depends on being able to identify and act on specific accounts.

**Independent Test**: Sign in as an administrator, open the panel, disable a test account, confirm that account can no longer authenticate, then re-enable it and confirm access is restored — all without touching built-in connections.

**Acceptance Scenarios**:

1. **Given** an authenticated administrator, **When** they open the control panel, **Then** a paginated, searchable list of all registered accounts is displayed with each account's status, and no password hashes or API key material are exposed.
2. **Given** a registered account, **When** the administrator activates it, **Then** the account becomes eligible to be allocated built-in connections and the change is reflected in the list.
3. **Given** an active account, **When** the administrator disables it, **Then** the account is immediately prevented from authenticating, any existing sessions are invalidated, and the account's saved data (sessions, BYOK connections) is retained.
4. **Given** a disabled account, **When** the administrator re-enables it, **Then** the account can authenticate again with its prior data intact.
5. **Given** any account, **When** the administrator triggers a password reset, **Then** the user must set a new password before regaining access, and the previous password no longer works.
6. **Given** a non-administrator user, **When** they attempt to reach any control-panel capability, **Then** the request is refused with a non-disclosing authorization error.

---

### User Story 2 - Allocate built-in connections to users (Priority: P1)

An administrator manages a set of platform-owned "built-in" connections — each a protocol, endpoint, and administrator-supplied API key — and allocates specific built-in connections to specific activated accounts. An allocated user can select the built-in connection's models for chat exactly like their own connections, but can never view, edit, or delete the connection or see its key. One built-in connection can be allocated to many users.

**Why this priority**: Allocating managed credentials is the core differentiator of this feature; it lets the platform offer ready-to-use models to chosen users without exposing keys.

**Independent Test**: As an administrator, create a built-in connection with a mock endpoint and key, allocate it to one activated account and not another, then confirm the allocated user sees and can chat with its models while the key is never returned, and the non-allocated user sees nothing.

**Acceptance Scenarios**:

1. **Given** an administrator, **When** they create a built-in connection with a valid endpoint and key, **Then** it is stored with the key encrypted at rest and the key is never returned in any response.
2. **Given** a built-in connection and an activated account, **When** the administrator allocates the connection to the account, **Then** the account gains read-only access to that connection and its models for chat.
3. **Given** a built-in connection allocated to multiple accounts, **When** the administrator revokes the allocation from one account, **Then** that account immediately loses access while other allocations are unaffected.
4. **Given** an allocated user, **When** they view their connections, **Then** built-in connections are clearly distinguished from their own and offer no edit, delete, or key-reveal actions.
5. **Given** an allocated user, **When** they submit a chat turn using a built-in connection's model, **Then** the call is dispatched using the administrator-supplied key without the key being exposed to the user or appearing in any log, response, or analytics payload.
6. **Given** an account that has not been activated, **When** the administrator attempts to allocate a built-in connection to it, **Then** the allocation is refused until the account is activated.

---

### User Story 3 - BYOK remains always available (Priority: P2)

Any registered, email-verified, non-disabled user can create and use their own (BYOK) connections and models regardless of activation state or built-in allocations. Activation and built-in allocation only gate built-in connections; they never restrict a user's own keys.

**Why this priority**: Preserving the existing self-service workflow guarantees the new administrative gating does not regress today's core experience.

**Independent Test**: Register a fresh account, verify email, do not activate it administratively, then confirm the user can still create a BYOK connection and chat with it, while having no access to any built-in connection.

**Acceptance Scenarios**:

1. **Given** a registered, verified, non-disabled account that has not been administratively activated, **When** the user creates a BYOK connection, **Then** it succeeds and is usable for chat.
2. **Given** a disabled account, **When** the user attempts any BYOK action, **Then** access is refused because the account cannot authenticate.
3. **Given** a user with both BYOK and allocated built-in connections, **When** they open chat model selection, **Then** both sets appear together and are individually selectable.

---

### Edge Cases

- **Last-usable-admin protection**: The system MUST prevent any action that would leave zero usable administrators — this includes disabling an administrator, demoting (removing admin rights from) an administrator, **and resetting an administrator's password** (which invalidates their credential and sessions). Each such action against the sole usable administrator MUST be refused.
- **Concurrent admin actions**: Two simultaneous requests that each individually appear safe (e.g. two disables, or two password resets, when exactly two usable admins remain) MUST NOT together reduce the usable-admin count to zero; the invariant is enforced under concurrency, so exactly one such request succeeds.
- **Disable blocks login too**: A disabled account MUST be refused at the login endpoint (no token issued), not only on already-authenticated requests.
- **Disable while chatting**: When an account is disabled mid-session, in-flight requests are allowed to fail gracefully and no new authenticated request succeeds.
- **Allocation to disabled account**: A built-in connection allocated to an account that is later disabled is inert while disabled and becomes usable again only if the account is re-enabled (and still activated).
- **Built-in connection key rotation**: When an administrator updates a built-in connection's key, all allocated users continue to use the connection with the new key without re-allocation.
- **Deleting a built-in connection** that is currently allocated removes it from every allocated user's available models; previously saved chat responses retain their immutable model/protocol snapshots.
- **Password reset for a built-in-only user**: Resetting the password does not affect which built-in connections remain allocated.
- **Duplicate activation/allocation**: Activating an already-active account or allocating an already-allocated connection is idempotent and does not error.
- **Search/pagination on large user bases**: The user list remains responsive and correctly paginated when there are many thousands of accounts.

## Requirements *(mandatory)*

### Functional Requirements

#### Administrator role & access control

- **FR-001**: The system MUST distinguish an administrator role from a regular registered user, and MUST restrict all control-panel capabilities to administrators.
- **FR-002**: The system MUST provide a way to designate at least one initial administrator at deployment time (bootstrap), without requiring a pre-existing administrator.
- **FR-003**: The system MUST refuse every administrative action requested by a non-administrator with a non-disclosing authorization error.
- **FR-004**: The system MUST prevent any action that would leave zero usable administrators — disabling an administrator, demoting an administrator, or resetting an administrator's password — and MUST enforce this invariant safely under concurrent requests (no interleaving of individually-safe actions may drive the usable-admin count to zero).

#### User account management

- **FR-005**: The system MUST let an administrator view a paginated, searchable list of all registered accounts showing email, email-verification state, activation state, enabled/disabled state, administrator flag, and creation date.
- **FR-006**: The system MUST let an administrator activate a registered account, marking it eligible to be allocated built-in connections.
- **FR-007**: The system MUST let an administrator disable an account, immediately blocking authentication — both refusing the login endpoint (no token issued) and rejecting already-issued tokens on the next request — and invalidating that account's existing sessions while preserving all of the account's stored data.
- **FR-008**: The system MUST let an administrator re-enable a previously disabled account, restoring authentication with prior data intact.
- **FR-009**: The system MUST let an administrator trigger a password reset for any account such that the account's current password stops working and the user must establish a new password before regaining access. The reset link MUST be single-use even under concurrent submissions, and a reset that would lock out the last usable administrator MUST be refused (see FR-004).
- **FR-010**: The system MUST treat repeated activation, disable, enable, and allocation actions idempotently (no error when the target is already in the requested state).
- **FR-011**: Account management actions MUST never expose password hashes, plaintext passwords, or API key material in any response or log.

#### Built-in connections & allocation

- **FR-012**: The system MUST let an administrator create, edit, and delete built-in connections, each consisting of a protocol, endpoint, optional label, and an administrator-supplied API key.
- **FR-013**: The system MUST encrypt built-in connection API keys at rest and MUST never return key material in any response, log, error message, or analytics payload — for administrators or users.
- **FR-014**: The system MUST apply the same endpoint-safety rules to built-in connections that apply to user connections (rejecting endpoints resolving to loopback, link-local, multicast, or private network space).
- **FR-015**: The system MUST let an administrator allocate a built-in connection to one or more activated accounts, and MUST refuse allocation to accounts that are not activated.
- **FR-016**: The system MUST let an administrator revoke a built-in-connection allocation from a specific account without affecting that connection's other allocations.
- **FR-017**: Allocated users MUST be able to select a built-in connection's models for chat with read-only access — they MUST NOT be able to edit, delete, reveal the key of, or change the endpoint of a built-in connection.
- **FR-018**: The system MUST visually and structurally distinguish built-in connections from a user's own connections in any user-facing connection listing.
- **FR-019**: When a built-in connection is used in chat, the system MUST dispatch the call using the administrator-supplied key without exposing the key to the allocated user.
- **FR-020**: When an administrator updates a built-in connection's key or endpoint, all current allocations MUST continue to function with the updated values without re-allocation.
- **FR-021**: When a built-in connection is deleted, it MUST be removed from every allocated user's available models, and previously saved chat responses MUST retain their immutable model, protocol, and connection-label snapshots.

#### BYOK guarantees

- **FR-022**: Any registered, email-verified, non-disabled account MUST be able to create and use its own BYOK connections and models regardless of activation state or built-in allocations.
- **FR-023**: The system MUST present a user's own connections and any allocated built-in connections together in chat model selection, each individually selectable.
- **FR-024**: A disabled account MUST be unable to perform any authenticated action, including BYOK actions, until re-enabled.

#### Auditability

- **FR-025**: The system MUST record an audit trail of administrative actions (activate, disable, enable, password reset, built-in connection create/update/delete, allocate, revoke) capturing the acting administrator, the target account or connection, the action, and a timestamp, without recording any key material or plaintext password.

#### Discoverability & password-reset completion

- **FR-026**: The system MUST let an authenticated user retrieve their own identity and administrator status so the frontend can expose the control panel only to administrators (no admin navigation is shown to, or reachable by, non-administrators).
- **FR-027**: A user who receives a password-reset link MUST be able to complete the reset by entering a new password through a dedicated page, after which they can sign in with the new password.

### Key Entities *(include if feature involves data)*

- **Administrator role**: A designation on an account granting access to control-panel capabilities. At least one must always remain enabled.
- **Account status**: The lifecycle state of a registered account, composed of activation state (activated vs. not activated, governing built-in eligibility) and enabled state (enabled vs. disabled, governing authentication). Independent of the existing email-verification state.
- **Built-in connection**: A platform-owned connection (protocol, endpoint, label, encrypted administrator-supplied key) created and managed only by administrators; never editable or key-readable by regular users.
- **Built-in connection allocation**: A grant linking one built-in connection to one activated account, enabling read-only chat use; revocable independently per account; many-to-many between connections and accounts.
- **Administrative audit record**: An append-only entry capturing who did what to which target and when, excluding secret material.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An administrator can locate a specific account and change its status (activate, disable, enable, or reset password) in under 30 seconds from opening the control panel.
- **SC-002**: A disabled account is blocked from all authenticated access within one request cycle of being disabled (no successful authenticated request after disable).
- **SC-003**: Allocating a built-in connection to an activated user makes its models available to that user for chat on the user's next connection refresh, with the API key never appearing in any response, log, or analytics payload (0 key disclosures across all tests).
- **SC-004**: Revoking an allocation removes the affected user's access to that built-in connection while leaving 100% of other users' allocations of the same connection intact.
- **SC-005**: 100% of fresh registered, verified, non-disabled accounts can create and use a BYOK connection without any administrative action.
- **SC-006**: The control-panel user list returns its first page in under 1 second for an account base of at least 10,000 users (verified by a test that seeds ≥10,000 accounts and measures first-page latency with indexed search and deterministic ordering).
- **SC-007**: The system never allows the count of usable administrators to reach zero — verified by attempted last-admin disable, demote, and password-reset being refused 100% of the time, including under two concurrent requests racing on the last two usable admins.

## Assumptions

- The administrator role is a flag/attribute on the existing account model; the initial administrator is designated at deployment time via configuration (e.g., a seed identity), consistent with the project's existing deployment model.
- "Activation" is a distinct administrative gate from the existing email-verification flow: email verification proves identity ownership, while activation grants eligibility for built-in connections. A user may be verified but not activated.
- Built-in connections reuse the existing protocol/connection/configured-model concepts (feature 003); the difference is ownership (platform/administrator) and read-only exposure to allocated users, plus the allocation relationship.
- Disabling preserves the account's data (sessions, BYOK connections, allocations); it only blocks authentication. Re-enabling restores prior access.
- A password reset forces the user to set a new password (e.g., via the existing email-based reset/verification mechanism) rather than the administrator choosing the password directly, so administrators never learn user passwords.
- Built-in connection allocation is read-only "use" only; allocated users cannot promote a built-in connection into an editable BYOK connection.
- The control panel is delivered through the same versioned public API and frontend stack as the rest of the platform (API-first), with administrative endpoints gated by the administrator role.
