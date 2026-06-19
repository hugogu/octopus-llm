# Implementation Plan: Data Migration, Quest Sharing & Lifecycle

**Branch**: `008-data-migration` | **Date**: 2026-06-17 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/008-data-migration/spec.md`

## Summary

008 adds **portable data migration** plus several Quest lifecycle/sharing capabilities on top of the
existing chat/share/connection stack:

1. **Admin migration** — export every user's Quest (with referenced media) and every Connection
   into one versioned, passphrase-encrypted bundle, and import it into another deployment where all
   Quests and Connections land under the importing admin.
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
the Next.js app, add Flyway migrations `V032`–`V035`, a new `migration` backend package for bundle
export/import, use Spring Security Crypto's authenticated password-based encryption for portable
artifact encryption, and reuse `ApiKeyEncryptionService` to encrypt imported keys at rest.

## Technical Context

**Language/Version**: Kotlin on JVM (Java 21) backend; TypeScript 5 / Node.js 24 frontend
**Primary Dependencies**: Spring Boot WebFlux, Spring Data JPA/Hibernate, Flyway, jjwt, Spring
Security Crypto, AWS SDK v2 (S3 media); Next.js App Router, React, Tailwind. Bundle packaging via
JVM `java.util.zip`.
**Storage**: PostgreSQL (Flyway). Existing tables: `chat_sessions`, `chat_turns`,
`provider_responses`, `session_shares`, `connections`, `configured_models`, `media`. New columns/
tables for share scope, dialog redactions, import origin, and migration operations.
**Testing**: Backend `cd backend && ./gradlew build`; frontend `cd frontend && npm run build &&
npm run lint && npm run test:run`.
**Target Platform**: Linux server (Docker Compose), horizontally scalable, ARM64 dev / AMD64 prod.
**Project Type**: Web application (backend + frontend) — Option 2.
**Performance Goals**: Migration export/import are admin batch operations (streamed, not in the
concurrent LLM hot path). Continue-from-share import completes in < 30 s (SC-003). Per-Dialog delete
and share-scope changes are interactive (< 1 s perceived).
**Constraints**: No distributed locks (VII); migrations forward-only (IV); opaque share tokens
(VI); no plaintext key material in API responses/logs/errors/audit metadata (VI); styled
confirmations only, no native dialogs (VIII). Bundle may be large → stream through the Next proxy
and backend temp file rather than buffering the artifact in browser-proxy or JVM memory. Artifact
passphrase is sourced from an optional `MIGRATION_ARTIFACT_PASSPHRASE` env var (memory-only, like
`ENCRYPTION_MASTER_KEY`), falling back to an admin-entered passphrase; manual entry always overrides.
**Scale/Scope**: Single-deployment admin migration (all users' Quests + connections). Bundle size
bounded by total stored Quests + media; treat as potentially GB-scale → streamed ZIP.

## Constitution Check

*GATE: evaluated against `.specify/memory/constitution.md` v1.2.0.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Provider-Agnostic Abstraction | ✅ Pass | No provider-specific logic added; Connection export/import is protocol-agnostic (stores `protocol` + opaque key). |
| II. API-First | ✅ Pass | All new capability via `/api/v2/*` endpoints the frontend consumes; no FE→DB shortcuts. No breaking change to existing routes (paths keep `chat` internally; see research R7). |
| III. Concurrent Execution & Streaming | ✅ Pass | Migration is batch/off-hot-path. Continue-from-share import reuses existing concurrent turn streaming for new prompts. |
| IV. Data Integrity & Immutable Sessions | ✅ Pass | Per-Dialog deletion uses an **append-only `dialog_redactions` table** (no UPDATE/DELETE on immutable rows); reads filter redacted ids. Imports use fresh append-only rows. |
| V. Observability & Analytics | ✅ Pass | Redactions hide Dialogs from Quest/share views only; analytics keeps reading unfiltered immutable snapshots. `migration_operations` records actor, state, and counts without secret material. |
| VI. Security & User Key Privacy | ✅ Pass | The complete artifact payload is passphrase-encrypted with authenticated encryption before it enters the API response. Passphrases and decrypted keys are memory-only, never logged/audited, and imported keys are immediately encrypted with the target master key. Share tokens remain opaque. |
| VII. Simplicity & Horizontal Scalability | ✅ Pass | No distributed locks; bundle work is stateless per request and streamable. One new backend package (`migration`) — within limits. |
| VIII. UX Consistency & Visual Coherence | ✅ Pass | Reuse design system (`Button`, AdminShell, cards, `confirmDialog`), combined button + share-scope UI styled, all destructive actions confirmed, pages reachable via in-app nav; visual verification required before done. |

**Gate result**: PASS. The redaction model preserves immutable rows, and portable artifact
encryption removes the previous plaintext-key exception.

## Project Structure

### Documentation (this feature)

```text
specs/008-data-migration/
├── plan.md              # This file
├── research.md          # Phase 0 — decisions (R1..R10)
├── data-model.md        # Phase 1 — entities, migrations V032..V035
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
│   ├── MigrationExportService.kt     # streams ZIP envelope + encrypted structured/media entries
│   ├── MigrationImportService.kt     # parses + inserts; assigns Quests to importing admin
│   ├── MigrationBundle.kt            # versioned manifest DTOs (format id + version)
│   ├── MigrationArtifactCrypto.kt     # authenticated passphrase encryption/decryption
│   ├── MigrationOperation.kt          # idempotency/result record; no secret material
│   ├── MigrationOperationRepository.kt
│   ├── MigrationStagedMedia.kt         # crash-safe external blob cleanup ledger
│   └── MigrationStagedMediaRepository.kt
├── chat/
│   ├── ChatControllerV2.kt           # + DELETE turn (prompt Dialog), + DELETE response (model Dialog)
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
├── V034__quest_import_origin.sql     # optional origin metadata on chat_sessions
└── V035__migration_operations.sql    # idempotency + result/audit metadata, no secrets

frontend/src/
├── app/(app)/quests/ …               # Quest pages after route rename
├── app/(app)/chat/ …                 # compatibility redirects only
├── app/(app)/admin/migration/page.tsx# NEW admin page (export/import) under AdminShell
├── app/(public)/share/[token]/ …     # show "Import to continue" + auth gate for authenticated scope
├── components/chat/
│   ├── SessionSidebar.tsx            # combined New Quest / Import button
│   ├── MessageThread.tsx / ResponseGroup.tsx  # per-Dialog delete affordances + confirm
│   ├── QuestImportDialog.tsx         # NEW
│   └── ShareConversationButton.tsx   # scope selector (authenticated | public)
├── components/share/
│   └── SharedConversation.tsx        # import affordance + scope-aware rendering
├── components/admin/MigrationPage.tsx# NEW
└── lib/api/{migration.ts(NEW),shares.ts,chatV2.ts}  # client methods
```

**Structure Decision**: Web application (Option 2). New backend `migration` package isolates
bundle concerns; everything else extends existing packages. Rename to "Quest" is a frontend
copy/icon/route concern — backend table and API paths keep the internal `chat`/`session` naming to
avoid a breaking `/api/v2` change (Constitution II); see research R7.

## Complexity Tracking

| Complexity | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **Portable encrypted artifact** | Source and target deployments have different at-rest master keys, while Constitution VI prohibits returning provider keys in plaintext. | Excluding keys breaks the required immediately-usable import. Returning plaintext is prohibited. A passphrase-encrypted payload uses existing Spring Security Crypto and keeps portability without adding a custom cryptographic primitive. |
| **Append-only Dialog redactions** | FR-030/031/033 remove Dialogs from user-visible reads while preserving immutable comparison snapshots and analytics. | A mutable `deleted_at` column or physical delete would weaken Constitution IV. Append-only markers keep original rows unchanged. |
| **Staged media import with compensation** | Database transactions cannot roll back local/S3 object writes. User-visible artifact atomicity therefore requires blobs to exist before the single DB commit and failed/crashed staging to be cleaned independently. | Writing blobs inside the transaction can leave committed rows pointing at missing media; pretending batch transactions are artifact-atomic is incorrect. Staging plus idempotent cleanup preserves visible atomicity without a distributed lock. |
