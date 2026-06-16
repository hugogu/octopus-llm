# Implementation Plan: Data Migration, Quest Sharing & Lifecycle

**Branch**: `008-data-migration` | **Date**: 2026-06-17 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/008-data-migration/spec.md`

## Summary

008 adds **portable data migration** plus several Quest lifecycle/sharing capabilities on top of the
existing chat/share/connection stack:

1. **Admin migration** — export every user's Quest (with referenced media) and every Connection
   (provider/endpoint/configured-models/**plaintext keys**) into one versioned bundle, and import it
   into another deployment where all Quests land under the importing admin.
2. **Continue-from-share import** — any logged-in user can import a Quest they can view via a share
   link into their own account and keep prompting it with their own models; the share page advertises
   this. The Quest-list "New" control becomes a combined button (primary = new, secondary = import).
3. **Share scopes** — shares gain an audience scope: `authenticated` (logged-in users only) or
   `public` (current behaviour), changeable/revocable, always behind an opaque token.
4. **Per-Dialog deletion** — delete an individual model response, or a user prompt (which removes its
   whole turn), each behind the styled `confirmDialog`. Deletion is a **redaction marker** so the
   immutable response snapshots that analytics relies on are preserved.
5. **Chat → Quest reframing** — user-facing copy and icons change from chat/conversation to
   task-oriented "Quest"; the product is positioned as an LLM comparison tool.

Technical approach: extend existing backend packages (`chat`, `share`, `connection`, `admin`) and
the Next.js app, add Flyway migrations `V032`–`V034`, a new `migration` backend package for the
bundle export/import, and reuse the existing `ApiKeyEncryptionService` to re-encrypt imported keys.

## Technical Context

**Language/Version**: Kotlin on JVM (Java 21) backend; TypeScript 5 / Node.js 24 frontend
**Primary Dependencies**: Spring Boot WebFlux, Spring Data JPA/Hibernate, Flyway, jjwt, AWS SDK v2 (S3
media); Next.js App Router, React, Tailwind. Bundle packaging via JVM `java.util.zip` (no new dep).
**Storage**: PostgreSQL (Flyway). Existing tables: `chat_sessions`, `chat_turns`,
`provider_responses`, `session_shares`, `connections`, `configured_models`, `media`. New columns/
table for share scope and dialog redactions.
**Testing**: Backend `./gradlew test` (JUnit + integration); frontend `vitest` + `tsc --noEmit`.
**Target Platform**: Linux server (Docker Compose), horizontally scalable, ARM64 dev / AMD64 prod.
**Project Type**: Web application (backend + frontend) — Option 2.
**Performance Goals**: Migration export/import are admin batch operations (streamed, not in the
concurrent LLM hot path). Continue-from-share import completes in < 30 s (SC-003). Per-Dialog delete
and share-scope changes are interactive (< 1 s perceived).
**Constraints**: No distributed locks in hot path (VII); migrations forward-only (IV); opaque share
tokens (VI); styled confirmations only, no native dialogs (VIII). Bundle may be large → stream to/
from disk/object storage rather than buffering whole in memory.
**Scale/Scope**: Single-deployment admin migration (all users' Quests + connections). Bundle size
bounded by total stored Quests + media; treat as potentially GB-scale → streamed ZIP.

## Constitution Check

*GATE: evaluated against `.specify/memory/constitution.md` v1.2.0.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Provider-Agnostic Abstraction | ✅ Pass | No provider-specific logic added; Connection export/import is protocol-agnostic (stores `protocol` + opaque key). |
| II. API-First | ✅ Pass | All new capability via `/api/v2/*` endpoints the frontend consumes; no FE→DB shortcuts. No breaking change to existing routes (paths keep `chat` internally; see research R7). |
| III. Concurrent Execution & Streaming | ✅ Pass | Migration is batch/off-hot-path. Continue-from-share import reuses existing concurrent turn streaming for new prompts. |
| IV. Data Integrity & Immutable Sessions | ⚠️ Exception (justified) | Per-Dialog deletion must not mutate write-once `provider_responses`/turns. Resolved via an **append-only `dialog_redactions` table** (no UPDATE/DELETE on immutable rows); reads filter redacted ids. Imports use append-only inserts. See Complexity Tracking. |
| V. Observability & Analytics | ✅ Pass | Redactions hide Dialogs from Quest/share views only; analytics keeps reading unfiltered immutable snapshots. Migration/delete emit admin audit events (reuse `AdminAuditLog`). |
| VI. Security & User Key Privacy | ⚠️ Exception (user-approved) | Export carries **plaintext** provider keys (cross-server master keys differ). Deliberate exception to "keys MUST NOT appear in exports". Compensating controls: admin-only, explicit warning + typed acknowledgement before generation, bundle flagged sensitive, audit-logged, never exposed to non-admins. Share tokens remain opaque (no identity in URLs) — that part still holds. See Complexity Tracking. |
| VII. Simplicity & Horizontal Scalability | ✅ Pass | No distributed locks; bundle work is stateless per request and streamable. One new backend package (`migration`) — within limits. |
| VIII. UX Consistency & Visual Coherence | ✅ Pass | Reuse design system (`Button`, AdminShell, cards, `confirmDialog`), combined button + share-scope UI styled, all destructive actions confirmed, pages reachable via in-app nav; visual verification required before done. |

**Gate result**: PASS with two documented exceptions (IV redaction approach, VI plaintext keys),
both recorded in Complexity Tracking with compensating controls.

## Project Structure

### Documentation (this feature)

```text
specs/008-data-migration/
├── plan.md              # This file
├── research.md          # Phase 0 — decisions (R1..R8)
├── data-model.md        # Phase 1 — entities, migrations V032..V034
├── quickstart.md        # Phase 1 — end-to-end validation scenarios
├── contracts/           # Phase 1 — API contracts
│   ├── admin-migration.md
│   ├── quest-dialogs.md
│   ├── shares-scope.md
│   └── shared-import.md
└── checklists/
    └── requirements.md  # from /speckit-specify
```

### Source Code (repository root)

```text
backend/src/main/kotlin/com/octopusllm/
├── migration/                 # NEW package
│   ├── MigrationController.kt        # /api/v2/admin/migration/{export,import}
│   ├── MigrationExportService.kt     # streams ZIP bundle (manifest.json + media/)
│   ├── MigrationImportService.kt     # parses + inserts; assigns Quests to importing admin
│   ├── MigrationBundle.kt            # versioned manifest DTOs (format id + version)
│   └── MigrationAuditActions.kt
├── chat/
│   ├── ChatControllerV2.kt           # + DELETE turn (prompt Dialog), + DELETE response (model Dialog),
│   │                                  #   + POST /import (continue-from-share & combined-button import)
│   ├── ChatService.kt                # + redaction + import-copy logic
│   ├── DialogRedaction.kt            # NEW entity (append-only)
│   └── DialogRedactionRepository.kt  # NEW
├── share/
│   ├── SessionShare.kt               # + scope field
│   ├── ShareControllerV2.kt          # create takes scope; + PATCH scope
│   └── SharedSessionController.kt    # enforce scope (authenticated vs public); + POST /import
└── connection/                       # read by MigrationExportService (decrypt) / import (encrypt)

backend/src/main/resources/db/migration/
├── V032__session_share_scope.sql
├── V033__dialog_redactions.sql
└── V034__quest_import_origin.sql     # optional origin metadata on chat_sessions

frontend/src/
├── app/(app)/chat/ …                 # Quest pages (rename copy/icons; combined New/Import button)
├── app/(app)/admin/migration/page.tsx# NEW admin page (export/import) under AdminShell
├── app/(public)/share/[token]/ …     # show "Import to continue" + auth gate for authenticated scope
├── components/chat/
│   ├── SessionSidebar.tsx            # combined New Quest / Import button
│   ├── MessageThread.tsx / ResponseGroup.tsx  # per-Dialog delete affordances + confirm
│   └── QuestImportDialog.tsx         # NEW
├── components/share/
│   ├── SharedConversation.tsx        # import affordance + scope-aware rendering
│   └── ShareConversationButton.tsx   # scope selector (authenticated | public)
├── components/admin/MigrationPage.tsx# NEW
└── lib/api/{migration.ts(NEW),shares.ts,chatV2.ts}  # client methods
```

**Structure Decision**: Web application (Option 2). New backend `migration` package isolates
bundle concerns; everything else extends existing packages. Rename to "Quest" is a frontend
copy/icon/route concern — backend table and API paths keep the internal `chat`/`session` naming to
avoid a breaking `/api/v2` change (Constitution II); see research R7.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **VI exception — plaintext provider keys in export bundle** | User-approved requirement for true one-step migration; ciphertext is not portable because each deployment has a different AES master key, so re-encryption requires the plaintext. | (a) *Exclude keys, re-enter on import* — rejected by user (wants one-step). (b) *Passphrase-encrypted bundle* — rejected by user. Compensating controls: admin-only endpoint, mandatory typed warning acknowledgement, bundle marked sensitive + advised deletion, audit-logged, never returned to non-admins, keys re-encrypted at rest immediately on import. |
| **IV exception — Dialog deletion on immutable session data** | Spec FR-030/031/033 require removing individual Dialogs from view. `provider_responses` is write-once; turns are part of immutable sessions. | *Add mutable `deleted_at` column to `provider_responses`* — rejected because it UPDATEs a write-once table, weakening the immutability guarantee. Chosen instead: **append-only `dialog_redactions`** table (insert-only markers keyed by turn id / response id); Quest & share reads exclude redacted ids; analytics ignores redactions and keeps reading the untouched immutable snapshots. |
