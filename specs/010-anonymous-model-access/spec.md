# Feature Specification: Anonymous Chat and Model Access Management

**Feature Branch**: `010-anonymous-model-access`
**Created**: 2026-09-02
**Status**: Draft
**Input**: User description: "让服务可以匿名使用；管理员可以配置哪些模型无需登录即可使用；匿名用户的聊天记录保存在浏览器 local storage，注册时自动同步到后端；local storage 对话无法分享；管理员需要方便地批量开放匿名访问、批量删除和批量展示模型。"

## Goal

Allow a visitor to open the chat experience and use administrator-approved models without creating an account first. Give administrators a fast, safe way to manage model availability at scale, while keeping anonymous conversations private to the browser until the visitor registers.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start Chatting Without an Account (Priority: P1)

A visitor opens the chat page without signing in. They can see the models that the administrator has made available to anonymous visitors, choose one or more of them, and submit a prompt immediately.

**Why this priority**: Anonymous first use removes the highest-friction step in discovering the service and is the core value of this feature.

**Independent Test**: Open the chat page in a fresh browser context with no authentication, verify that only public models are listed, and complete a prompt against at least one listed model.

**Acceptance Scenarios**:

1. **Given** an administrator has made at least one enabled model available to anonymous visitors, **When** a visitor opens the chat page without an account, **Then** the visitor can reach the composer and the model picker without being redirected to sign in.
2. **Given** several configured models exist, **When** the visitor opens the model picker, **Then** only models that are both enabled for the service and approved for anonymous use are shown, with enough display information to distinguish them.
3. **Given** the visitor selects multiple approved models, **When** they submit a prompt, **Then** the service runs the selected models using the existing comparison experience and shows each model's response or a clear model-specific failure.
4. **Given** no model is currently approved and enabled for anonymous use, **When** a visitor opens the chat page, **Then** the page explains that anonymous chat is temporarily unavailable and provides a clear path to sign in or register.

### User Story 2 - Manage Model Access in Bulk (Priority: P1)

An administrator manages a large model catalogue from the existing administration area. They can search and filter models, select individual models or a complete filtered result set, and apply anonymous-access, display, or deletion actions in bulk.

**Why this priority**: The value of anonymous access depends on administrators being able to maintain the allowlist efficiently as the number of models grows.

**Independent Test**: Seed at least 100 configured models, use the administration UI to filter and select a set spanning multiple pages, perform each bulk action, and verify the resulting model states and confirmation feedback.

**Acceptance Scenarios**:

1. **Given** an administrator opens model management, **When** the model list is displayed, **Then** each model exposes its current enabled/display state and anonymous-access state, and the administrator can search, filter, sort, and page through the list.
2. **Given** a filtered model result contains models across multiple pages, **When** the administrator chooses “select all matching models”, **Then** every model in the filtered result is selected and the pending operation count is clear.
3. **Given** one or more models are selected, **When** the administrator confirms “allow anonymous access”, **Then** anonymous access is enabled for all selected models without changing their normal authenticated-user enabled/display state.
4. **Given** one or more models are selected, **When** the administrator confirms “remove anonymous access”, **Then** those models no longer appear in the anonymous model picker or accept new anonymous chat turns.
5. **Given** one or more models are selected, **When** the administrator chooses the bulk display action, **Then** the selected models become displayed/enabled in the normal model picker; the administrator can also bulk hide/disable them. Display changes do not by themselves grant anonymous access.
6. **Given** one or more models are selected for deletion, **When** the administrator confirms the destructive action, **Then** the selected configured models are removed, historical saved responses remain readable, and the UI reports the completed and unsuccessful items.
7. **Given** an enabled model is approved for anonymous use, **When** an administrator marks it as a Guest default, **Then** it is preselected first for fresh anonymous browsers; no more than three Guest defaults can be configured, and hiding or revoking a default automatically removes that marker.
7. **Given** a bulk operation has completed, **When** the UI refreshes the list, **Then** it shows the resulting states and a non-silent success or error summary, including partial failures when applicable.

### User Story 3 - Preserve Anonymous Conversations Locally (Priority: P1)

A visitor can continue an anonymous conversation across page refreshes in the same browser. The conversation is kept in browser-local storage and is not treated as an account-owned server session while the visitor remains anonymous.

**Why this priority**: Local persistence gives anonymous visitors continuity without storing identifiable conversation history on the service before they choose to register.

**Independent Test**: Start several anonymous conversations, refresh and revisit them in the same browser, then inspect authenticated history and sharing entry points to confirm that the conversations are not exposed as server-owned sessions.

**Acceptance Scenarios**:

1. **Given** an anonymous visitor has completed or partially completed a conversation, **When** they refresh the page or return later in the same browser, **Then** the conversation and its available response state are still listed locally.
2. **Given** an anonymous visitor has local conversations, **When** they view the conversation actions, **Then** no share action or shareable URL is offered for those conversations.
3. **Given** browser-local storage is unavailable or full, **When** an anonymous response is received, **Then** the visitor sees a clear warning that the conversation could not be saved locally and can continue using the current page where possible.
4. **Given** an anonymous conversation references a model that is later hidden, disabled, or deleted, **When** the visitor opens the local conversation, **Then** previously stored content remains readable and the unavailable model is clearly labeled; new turns cannot use that model.

### User Story 4 - Migrate Local Conversations on Registration (Priority: P2)

An anonymous visitor decides to register. After successful registration, the service automatically imports the visitor's browser-local conversations into the new account so the visitor does not lose their work.

**Why this priority**: Registration should convert anonymous discovery into an account relationship without forcing the visitor to manually copy conversation history.

**Independent Test**: Create multiple local conversations with completed, failed, and in-progress response states, register in the same browser, and verify that the resulting account history contains each eligible conversation once with its original content and order.

**Acceptance Scenarios**:

1. **Given** the browser contains anonymous conversations, **When** registration succeeds, **Then** synchronization starts automatically without requiring the visitor to export or re-enter the conversations.
2. **Given** synchronization succeeds, **When** the new user opens their account history, **Then** the imported conversations are available with their prompts, model responses, response states, titles or labels, and chronological ordering preserved as far as the source data allows.
3. **Given** a synchronization request is retried, **When** the same local conversations are submitted again, **Then** the account contains no duplicate conversations or turns.
4. **Given** synchronization fails or only some conversations can be imported, **When** the visitor returns to the application, **Then** the visitor sees a clear status and the unsynchronized local data is retained for retry; local data is removed only after the service confirms successful import.
5. **Given** a conversation has been synchronized into the authenticated account, **When** the user views it, **Then** it follows the existing authenticated-session rules, including the existing sharing rules; before synchronization, it remains local-only and unshareable.

### User Story 5 - Continue Existing Authenticated Use (Priority: P2)

An authenticated user continues using the service with their existing account history, model access, and sharing behavior. Anonymous access configuration is an additional policy and does not unintentionally remove models from authenticated users.

**Why this priority**: The feature must expand access for visitors without changing the established experience or permissions of registered users.

**Independent Test**: Compare the model picker, conversation history, model configuration, and sharing behavior for an authenticated user before and after anonymous access is changed.

**Acceptance Scenarios**:

1. **Given** a model is enabled for authenticated users but not approved for anonymous use, **When** an authenticated user opens the model picker, **Then** the model remains available to that user and is absent for anonymous visitors.
2. **Given** anonymous access is revoked for a model, **When** an authenticated user uses that model, **Then** their existing authenticated access is unchanged.
3. **Given** a visitor is not authenticated, **When** they attempt to access account history, account settings, administration, or another personal-data surface, **Then** they are directed to authenticate rather than receiving another user's data.

## Edge Cases

- An administrator disables a model while it is approved for anonymous access. The model must disappear from the anonymous picker and reject new anonymous turns because normal enabled state is required for public use.
- Anonymous access is revoked while a visitor's request is already streaming. The current request may finish or fail according to its existing lifecycle, but no new turn or retry may start with the revoked model.
- A visitor submits a stale or tampered model identifier that is not in the current anonymous allowlist. The request is rejected without exposing private model or connection details, and other local conversations remain intact.
- A bulk action includes a model that was changed or deleted by another administrator. The result identifies that item as unsuccessful or already satisfied and does not silently claim that every item changed.
- The filtered result set changes while an administrator is selecting models. The confirmation step shows the exact number and scope of models that will be affected.
- Local storage is cleared, the visitor uses a different browser/device, or the browser is in a privacy mode that does not retain storage. Anonymous conversations cannot be recovered by the service unless they were already synchronized.
- A registration is interrupted after some conversations have been imported. Retrying must be safe and must not duplicate already imported content.
- A local conversation contains a partial response, model error, attachment reference, or unsupported historical field. The visitor must retain a readable record and the migration must preserve supported data while clearly identifying data that cannot be imported.
- No anonymous models are configured, or all configured anonymous models are temporarily disabled. Anonymous chat must show an actionable empty state rather than a blank model picker.
- An anonymous visitor opens multiple tabs. Conversation identifiers and updates must not cause one conversation to overwrite a different conversation; minor last-write timing conflicts may be resolved by the latest completed local update.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The service MUST allow an unauthenticated visitor to open the chat experience and use the models currently approved for anonymous access without first registering or signing in.
- **FR-002**: The service MUST maintain a per-administrator-controlled configured-model anonymous-access policy that is separate from the model's normal enabled/display state for authenticated users.
- **FR-003**: The public model catalogue MUST return only administrator-controlled models that are both normally enabled and approved for anonymous use, and MUST omit provider credentials, private connection data, user-owned models, and other administrator-only information.
- **FR-004**: Anonymous model selection and chat submission MUST be validated against the current public model catalogue on every new turn; a client MUST NOT gain access by submitting an identifier for a private, disabled, deleted, or revoked model.
- **FR-005**: Anonymous visitors MUST be able to select one or more currently public models and receive the existing multi-model comparison and streaming behavior, subject to each model's declared capabilities.
- **FR-006**: The service MUST preserve anonymous conversation data, including prompts, model responses, response states, model labels, and ordering, in browser-local storage for the same browser profile.
- **FR-007**: Anonymous conversation data MUST NOT be persisted as an account-owned server session while the visitor is anonymous, and anonymous conversations MUST NOT expose a share action or generate a shareable URL.
- **FR-008**: The registration flow MUST automatically begin synchronizing the browser's anonymous conversations after a new account is created in that browser; when there are no local conversations, it MAY complete without an import step.
- **FR-009**: Synchronization MUST be safe to retry, MUST prevent duplicate imported conversations and turns, and MUST remove local copies only after successful server confirmation. Failed or incomplete synchronization MUST leave unsynchronized local data available for retry and MUST provide clear status feedback.
- **FR-010**: A successfully synchronized conversation MUST become an authenticated account session and follow the existing authenticated history and sharing rules; unsynchronized local conversations MUST remain local-only.
- **FR-011**: The administration model-management view MUST support searching, filtering, sorting, pagination, individual selection, page selection, and selection of all models matching the active filter.
- **FR-012**: The administration view MUST show each model's normal enabled/display state and anonymous-access state, and MUST make the distinction between the two states understandable before an operation is confirmed.
- **FR-013**: Administrators MUST be able to apply anonymous-access allow and revoke operations to all selected models in one confirmed bulk action. These operations MUST be idempotent.
- **FR-014**: Administrators MUST be able to apply bulk display/enable and bulk hide/disable operations to all selected models in one confirmed bulk action. Display operations MUST NOT implicitly alter anonymous-access policy.
- **FR-015**: Administrators MUST be able to delete all selected administrator-controlled configured models in one confirmed bulk action. The operation MUST warn that deletion removes configuration while preserving historical saved responses according to existing data-retention rules.
- **FR-016**: Bulk operations MUST display the exact target scope before confirmation, disable conflicting controls while processing, and report successful, already-satisfied, and failed items after completion. Partial failures MUST be visible and retryable.
- **FR-017**: Anonymous-access changes MUST take effect for newly loaded public model lists and new anonymous turns without requiring a service restart. Revoking access MUST prevent new turns and retries while not corrupting already stored local conversations.
- **FR-018**: Every administrator-initiated change to anonymous access, normal display state, or model deletion MUST be auditable with the administrator, operation time, affected models, and resulting action.
- **FR-019**: Anonymous access MUST not expose API keys, private connection settings, personal account data, or another user's conversations. It MUST enforce safe anonymous defaults for per-client request rate, concurrent requests, prompt/history size, selected-model count, and provider execution duration, and MUST continue to honor the service's existing spend, safety, and model-capability policies.
- **FR-020**: Authenticated users MUST retain their existing model access, server-side history, account features, and sharing behavior unless an administrator changes the corresponding normal model state or existing account policy.
- **FR-021**: Administrators MUST be able to configure at most three enabled, anonymous-approved built-in models as Guest defaults. The public catalogue MUST order those defaults first, and a fresh anonymous browser MUST preselect the configured defaults before filling remaining selections from other public models.

### Scope Boundaries

- This feature covers anonymous discovery, model selection, chat, local conversation continuity, registration synchronization, and administrator model-access management.
- Account creation, login, password recovery, provider configuration, model configuration, and the existing authenticated sharing experience remain existing capabilities; this feature only connects registration to local-conversation synchronization and adds anonymous-access policy management.
- Anonymous access applies only to models and provider connections controlled by the administrator. User-owned models and user-provided provider credentials are never eligible for the public catalogue.
- Anonymous conversation data is intentionally browser-scoped. Cross-device recovery, anonymous share links, and server-side anonymous history are not included.
- Anonymous abuse quotas and spend limits use dedicated safe defaults and the service's existing operational policy mechanisms. Adding a separate quota-management product surface is not required for this feature unless a later specification extends the scope.

### Assumptions

- A configured model is eligible for anonymous access only when it is available to the service and normally enabled/displayed; anonymous permission is an additional allowlist.
- Only administrator-controlled models backed by service-managed provider configuration can be placed on the anonymous allowlist; user-owned or user-key-backed models remain private.
- “批量展示” means bulk show/hide of a model in the normal model picker, represented by the existing enabled/display state. It is intentionally independent from “allow anonymous access”.
- A visitor who registers in the same browser is the owner of that browser's anonymous local conversations. The first release does not merge local conversations into an unrelated existing account through a separate login flow.
- The first release accepts text prompts for anonymous chat. Media upload and server-side tool execution remain authenticated-only until they have an explicit anonymous policy and abuse controls.
- Local conversation migration preserves all fields supported by the authenticated session format and retains unsupported local fields locally with an explicit migration status when they cannot be imported.
- Existing authenticated sessions and historical responses remain governed by the platform's current immutable-history and retention behavior.
- The service rejects or throttles anonymous requests according to dedicated anonymous defaults and existing operational, safety, or spend protections; the anonymous model allowlist does not bypass them.

### Key Entities *(include if feature involves data)*

- **Anonymous Access Policy**: The administrator-controlled availability state for one configured model when the caller is not authenticated.
- **Public Model Entry**: Safe, user-facing model metadata returned to anonymous visitors for selection and comparison.
- **Anonymous Browser Conversation**: A browser-scoped conversation containing prompts, model responses, status, and ordering before account synchronization.
- **Conversation Synchronization Record**: The idempotent import identity and outcome used to associate one local conversation with one authenticated session during registration.
- **Bulk Model Operation**: A confirmed administrator action over a precisely defined set of models, including anonymous allow/revoke, display/hide, or deletion.
- **Administration Audit Event**: A record of who performed a model-access or model-lifecycle change, when it happened, which models were affected, and the resulting action.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a fresh unauthenticated browser context, a visitor can reach the composer, select an approved model, and submit the first prompt within 30 seconds in at least 95% of usability tests.
- **SC-002**: 100% of models shown to anonymous visitors are both normally enabled and approved for anonymous access; no private or revoked model is selectable through the public UI or a direct request.
- **SC-003**: An administrator can identify, select, and apply an anonymous-access or display-state change to at least 100 filtered models across multiple pages with one confirmation flow and within two minutes.
- **SC-004**: For a bulk operation, the final result accurately identifies every targeted model as changed, already in the requested state, or failed in 100% of tested cases, with no silent partial success.
- **SC-005**: At least 95% of test visitors can refresh and revisit an anonymous conversation in the same browser without losing any successfully stored prompt or response content.
- **SC-006**: After successful registration, 100% of eligible local conversations and turns are available in the new account exactly once; a repeated synchronization attempt creates zero duplicates.
- **SC-007**: Before synchronization, 100% of anonymous conversations remain unavailable through authenticated history and share links; browser-local anonymous data is never presented as another user's data.
- **SC-008**: Revoking anonymous access or hiding a model is reflected in newly opened public model lists within one normal page refresh and prevents new anonymous turns for that model.
- **SC-009**: Existing authenticated users complete their normal model selection, history, and sharing tasks with no measurable regression attributable solely to anonymous-access configuration.
- **SC-010**: 100% of requests that exceed anonymous rate, concurrency, payload, model-count, or execution-duration limits are rejected or terminated with a clear non-sensitive explanation, and no such request bypasses the model allowlist.

## Notes

- The public model catalogue is a policy-filtered view of configured models, not a second model configuration system.
- Normal enabled/display state and anonymous-access state are intentionally separate so administrators can hide a model from everyone without losing its anonymous policy, or permit a model for authenticated users while keeping it private.
- Local-only conversations are not shareable because they have no server-owned share target. Once successfully synchronized, they become authenticated sessions and use the existing authenticated sharing rules.
- Bulk management is designed around explicit scope, confirmation, idempotent actions, and visible per-item outcomes so it remains safe as the catalogue grows.
