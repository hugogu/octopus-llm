# Feature Specification: Data Migration, Quest Sharing & Lifecycle

**Feature Branch**: `008-data-migration`
**Created**: 2026-06-16
**Status**: Draft
**Input**: User description: "开启008 提供数据迁移的能力，在管理员页面中提供所有对话数据的导出、导入功能。注意不同服务上用户可能是不同的，配置的Provider、Connection也可能是不同的。所以也需要支持Connection的导出、导入。新服务器中导入的会话默认属于管理员。其它普通用户，可以导入其它人分享出来的对话，以便继续后续的会话（这个要在分享页面有所体现，以便让人知道有这个能力）导入会话和新增会话功能放一起，以combinedButton的形式呈现，导入作为这个新增按钮的附加功能。分享需要支持两种范围——仅对其他登录用户分享和公开分享。每个对话中的每个Dialog都可以单独删除（包括用户发的和AI Model回答的）删除操作都需要加上弹出窗口以确认。会话现在叫（Chat），全改成(Quest)图标也从对话变成任务性的。目的是这个平台不是对话服务，而是用来对比测试各个LLM的工具平台，本向没有LLM的对话能力的。"

> **Terminology note (applies throughout this spec):**
> - **Quest** — what was previously called a "Chat" / "Conversation": one comparison thread owned by a user, made of ordered prompt **turns**. Each turn fans a single user prompt out to several selected LLM models.
> - **Dialog** — an individual message inside a Quest: either the user's **prompt** bubble, or one model's **response** panel.
> - **Connection** — an existing concept: a configured provider endpoint (+ credentials + configured models) used to run prompts.
> - **Migration Artifact** — the portable export/import bundle introduced by this feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Admin migrates an entire deployment to a new server (Priority: P1)

An administrator wants to move (or back up and restore) a whole deployment. From the admin area they export **all** Quest data plus all Connection definitions into one portable Migration Artifact, then import that artifact into a different (possibly empty) deployment. Because users and providers may differ between servers, imported Quests are all assigned to the importing administrator, and Connections are recreated on the target.

**Why this priority**: This is the headline capability of 008 ("数据迁移"). Without it, moving or backing up a deployment requires manual database surgery, which is error-prone and unavailable to operators.

**Independent Test**: On server A (with several users' Quests and several Connections), the admin exports a Migration Artifact. On empty server B, the admin imports it; every Quest appears owned by the admin and every Connection appears in settings. Delivers complete value on its own.

**Acceptance Scenarios**:

1. **Given** an admin in the admin area, **When** they trigger "Export all data", **Then** a single downloadable Migration Artifact is produced containing every Quest (prompts, model responses, and referenced media) and every Connection definition.
2. **Given** a target deployment, **When** the admin imports a Migration Artifact, **Then** every Quest in it is created and owned by the importing admin, preserving turn order and response content.
3. **Given** the artifact contained Connections, **When** it is imported, **Then** each Connection is recreated and the admin is shown which Connections still need credentials before they can run.
4. **Given** a malformed or incompatible-version artifact, **When** the admin imports it, **Then** the system rejects it with a clear message and creates **no** partial data.

---

### User Story 2 - A user imports a shared Quest to continue it (Priority: P1)

A normal user opens a share link to someone else's Quest. The share page makes it clear they can **import** the Quest into their own account so they can keep going. After importing, they continue the thread by submitting new prompts answered by **their own** selected models.

**Why this priority**: Turns shared comparisons from read-only artifacts into reusable starting points — a core differentiator for a comparison/testing tool, and explicitly requested to be discoverable on the share page.

**Independent Test**: User B opens User A's share link, uses the clearly visible "Import to continue" control, lands on a new Quest in B's list containing A's history, and successfully submits a new prompt turn.

**Acceptance Scenarios**:

1. **Given** a shared Quest the viewer is allowed to see, **When** they view the share page, **Then** an obvious "Import to continue" affordance is shown with a short explanation of what it does.
2. **Given** a logged-in viewer, **When** they import the shared Quest, **Then** a new Quest owned by them is created containing the shared history and they are taken to it.
3. **Given** an imported Quest, **When** the user submits a new prompt, **Then** it streams responses from the user's own selected models and appends to the imported history.
4. **Given** an anonymous (not logged-in) viewer, **When** they attempt to import, **Then** they are guided to sign in or register first, and the import resumes afterward.

---

### User Story 3 - Create or import a Quest from one combined control (Priority: P2)

The primary "New Quest" control becomes a **combined button**: the main action starts a new Quest; an attached secondary action imports an existing Quest (e.g. from a share link the user has access to). Import is presented as an add-on to the create action, not a separate primary entry.

**Why this priority**: Keeps a single, discoverable entry point for "get a Quest into my list" without cluttering the UI with duplicate primary actions (honours the "one primary action per task" UX rule).

**Independent Test**: From the Quest list, the user sees a combined button, triggers its secondary "Import" action, imports a Quest, and it appears in their list.

**Acceptance Scenarios**:

1. **Given** the Quest list, **When** the user views the primary action, **Then** it is a combined button: "New Quest" as the primary action and "Import" as an attached secondary action.
2. **Given** the secondary action, **When** the user activates it and supplies a Quest they may import, **Then** a new Quest is created in their list and opened.

---

### User Story 4 - Share a Quest with a chosen audience scope (Priority: P2)

When sharing a Quest, the owner chooses the audience: **(a) logged-in users only**, or **(b) public** (anyone with the link). The owner can change the scope or revoke the share later.

**Why this priority**: Lets owners share comparisons internally without exposing them publicly, broadening safe sharing while preserving the existing public-link behaviour.

**Independent Test**: An owner creates a "logged-in only" share; an anonymous visitor opening the link is required to authenticate and sees no content beforehand; a logged-in visitor sees it. Switching to "public" lets anyone view.

**Acceptance Scenarios**:

1. **Given** a Quest, **When** the owner opens Share, **Then** they can choose scope "Logged-in users only" or "Public".
2. **Given** a logged-in-only share, **When** an anonymous visitor opens the link, **Then** they must authenticate to view, and no Quest content or owner identity is revealed before authentication.
3. **Given** a public share, **When** anyone opens the link, **Then** they can view it without authenticating.
4. **Given** an existing share, **When** the owner changes its scope or revokes it, **Then** access updates immediately on the next load.

---

### User Story 5 - Delete an individual Dialog, with confirmation (Priority: P2)

Inside a Quest, the user can remove any single Dialog — their own prompt, or any one model's response — each guarded by a styled confirmation dialog. All destructive actions across the app use a confirmation step.

**Why this priority**: Lets users prune noisy or failed comparisons without discarding a whole Quest; confirmation prevents accidental loss.

**Independent Test**: The user deletes one model's response and confirms; the sibling responses remain. The user deletes a prompt and confirms; that whole turn disappears. Canceling any confirmation changes nothing.

**Acceptance Scenarios**:

1. **Given** a model-response Dialog, **When** the user deletes it and confirms, **Then** that response is removed from the Quest while other responses in the same turn remain.
2. **Given** a user-prompt Dialog, **When** the user deletes it and confirms, **Then** that prompt turn and its responses are removed from the Quest.
3. **Given** any destructive action in the app, **When** the user triggers it, **Then** a styled in-app confirmation dialog appears first; canceling makes no change and no native browser dialog is used.
4. **Given** a deleted Dialog, **When** the Quest or any of its shares is viewed, **Then** the deleted Dialog no longer appears.

---

### User Story 6 - The platform is reframed as a "Quest" comparison tool (Priority: P2)

Every user-facing label and icon that called this a "Chat"/"Conversation" becomes "Quest" with task-oriented iconography. The product is positioned as a tool for comparing and testing LLMs, not a chat service, and no copy implies the platform itself can converse.

**Why this priority**: Aligns the product identity with its actual value (LLM comparison/testing). Pervasive but low-risk; can ship alongside the other stories.

**Independent Test**: Walk every surface that previously said Chat/Conversation; each now reads "Quest" with a task-style icon, and no copy claims platform-native conversational ability.

**Acceptance Scenarios**:

1. **Given** any surface previously labeled "Chat"/"Conversation", **When** it is viewed, **Then** it reads "Quest" and uses a task-oriented icon.
2. **Given** product copy, **When** read, **Then** nothing presents the platform as having its own conversational/LLM capability.

---

### Edge Cases

- **Corrupt / oversized / wrong-version artifact** → rejected with a clear message; no partial import.
- **Imported Quest references models or Connections absent on the target** → history is preserved as a read-only snapshot; continuation uses the importer's own selected models.
- **Duplicate import** (same Quest/artifact imported twice) → creates an independent copy; no dedupe, no overwrite.
- **Connection name collision on import** → imported as a new, suffixed Connection; nothing existing is overwritten.
- **Deleting the last remaining Dialog of a Quest** → the Quest becomes empty (allowed); the user may then delete the Quest itself.
- **Scope change from public to logged-in-only while anonymous users are viewing** → subsequent loads require authentication.
- **Referenced media (feature 007) in an exported Quest** → included in the artifact so it renders after import.
- **Very large full export** → may be split internally but remains a single logical artifact to import.

## Requirements *(mandatory)*

### Functional Requirements

**Admin data export / import**

- **FR-001**: Admins MUST be able to export all platform Quest data — every user's Quests including prompts, model responses, and referenced media — into a single portable Migration Artifact.
- **FR-002**: The export MUST include Connection definitions (provider, endpoint, configured models) so the target deployment can recreate them.
- **FR-003**: Admins MUST be able to import a Migration Artifact; all Quests it contains MUST be created and owned by the importing admin, preserving turn order and response content.
- **FR-004**: On import, Connections MUST be recreated with their provider secrets so imported Connections are immediately usable without re-entering keys. Secrets travel in the artifact in plaintext (decided: cross-server master keys differ, so ciphertext is not portable).
- **FR-005**: Because the artifact contains live credentials, producing an export that includes Connection secrets MUST require an explicit administrator acknowledgement of a prominent warning before the artifact is generated, the artifact MUST be marked as sensitive (advise secure handling and deletion after migration), and only administrators may produce or download it.
- **FR-006**: Import MUST be atomic per artifact: a malformed or incompatible artifact MUST be rejected with a clear error and MUST NOT create partial data.
- **FR-007**: The Migration Artifact MUST carry a format/version identifier; incompatible versions MUST be rejected on import.
- **FR-008**: Export and import MUST be restricted to administrators and MUST surface progress/result state (success, counts, items needing attention).

**Continue-from-share import (all users)**

- **FR-010**: The share page MUST clearly surface that a viewer can import the shared Quest to continue it, with a brief explanation.
- **FR-011**: A logged-in viewer MUST be able to import a shared Quest they can access, producing a new Quest owned by them that contains the shared history.
- **FR-012**: After importing, the user MUST be able to append new prompt turns that are answered by their own selected models.
- **FR-013**: Anonymous viewers attempting to import MUST be guided to authenticate first, after which the import completes.
- **FR-014**: The create-Quest control MUST be a combined button whose primary action creates a new Quest and whose attached secondary action imports one.

**Sharing scopes**

- **FR-020**: Quest owners MUST be able to share with scope "logged-in users only" or "public".
- **FR-021**: Logged-in-only shares MUST require authentication to view and MUST reveal no Quest content or owner identity to unauthenticated visitors.
- **FR-022**: Public shares MUST remain viewable without authentication.
- **FR-023**: Share links MUST remain opaque tokens regardless of scope, and owners MUST be able to change a share's scope or revoke it.

**Per-Dialog deletion & confirmation**

- **FR-030**: Users MUST be able to delete an individual model-response Dialog within their own Quest, leaving sibling responses in the same turn intact.
- **FR-031**: Users MUST be able to delete a user-prompt Dialog, which removes that prompt turn together with its responses.
- **FR-032**: Every destructive action (delete Dialog, delete Quest, revoke share, admin import-overwrite, etc.) MUST require confirmation via a styled in-app dialog; native browser dialogs MUST NOT be used.
- **FR-033**: Deleted Dialogs MUST NOT appear in the Quest view or in any share of it.

**Terminology & iconography**

- **FR-040**: All user-facing "Chat"/"Conversation" terminology MUST be replaced with "Quest".
- **FR-041**: Conversation/chat icons MUST be replaced with task-oriented iconography.
- **FR-042**: No user-facing copy may present the platform as having its own conversational/LLM capability.

### Key Entities *(include if feature involves data)*

- **Quest**: a user-owned comparison thread (formerly "Chat Session") composed of ordered prompt turns; the unit of export, import, sharing, and deletion.
- **Dialog**: an individual message within a Quest — a user prompt or one model's response; the unit of per-Dialog deletion.
- **Migration Artifact**: a versioned, portable bundle containing Quests (with referenced media) and Connection definitions; the unit of admin export/import.
- **Connection**: an existing provider endpoint definition (+ credentials + configured models); included in export and recreated on import.
- **Share**: a revocable, opaque-token link to a Quest, carrying an audience **scope** (logged-in-only | public).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An admin can export a full deployment and re-import it into an empty deployment in a single export→import cycle, with 100% of Quests present and owned by the importing admin.
- **SC-002**: 100% of imported Quests render their full history — including media — with no missing prompts or responses.
- **SC-003**: A logged-in user can import a shared Quest and submit a continuing prompt in under 30 seconds, with no manual data entry beyond selecting models.
- **SC-004**: 100% of destructive actions present a confirmation step, and 0 native browser dialogs appear anywhere in the app.
- **SC-005**: Unauthenticated visitors can view 0% of the content of logged-in-only shares.
- **SC-006**: 0 occurrences of user-facing "Chat"/"Conversation" wording remain; every such surface reads "Quest".
- **SC-007**: 100% of imported Connections are immediately usable without re-entering keys; and producing a secret-bearing export is impossible without an explicit admin warning acknowledgement, with 0% of non-admin users able to produce or download the artifact.

## Assumptions

- **Deletion is a soft "remove from view"**: a deleted Dialog disappears from the Quest and its shares, while the immutable provider-response snapshots that aggregate analytics rely on are retained (Constitution IV "Immutable Sessions" & V "Analytics"). Hard deletion of analytics history is out of scope.
- **Unresolved model/connection references on import** keep the history as a read-only snapshot; continuation uses the importer's own selected models.
- **Default share scope** for a newly created share is "logged-in users only" (the more private option); the owner may switch it to public.
- **Admin full export includes referenced media** so imported Quests render completely; a large export may be chunked internally but is imported as one logical artifact.
- **Duplicate imports** create independent copies; there is no dedupe.
- **Connection name collisions** on import create suffixed new Connections; nothing existing is overwritten.
- **"Logged-in users only"** means any authenticated platform user on the target deployment — not a named allow-list of specific users.
- **Imported Quests default to the importing admin** (admin migration) or the importing user (share import); original authorship is not transferred as ownership, though it may be shown as informational metadata.
- **Connection secrets travel in plaintext** inside the admin-only artifact (chosen for true one-step migration). ⚠️ **Constitution exception**: Principle VI (NON-NEGOTIABLE) states keys must not appear in exports. This deliberate exception MUST be recorded and justified in the plan's Complexity Tracking / Constitution Check, with the compensating controls in FR-005 (admin-only, explicit warning + acknowledgement, artifact marked sensitive).

## Dependencies

- Builds on existing **Sharing** (005) — extends shares with an audience scope.
- Builds on existing **Connections / configured models** (003/004) — adds export/import.
- Builds on existing **media storage** (007) — exported Quests reference media that must travel with the artifact.
- Operates within Constitution principles IV (immutable sessions), V (analytics), VI (key privacy / opaque share tokens), and VIII (UX consistency, styled confirmations).
