# Tasks: Chat UX Redesign and Session Persistence

**Input**: Design documents from `/specs/002-chat-ux-redesign/`  
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are OPTIONAL and NOT included in this task list (not explicitly requested in feature specification).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Install dependencies and prepare shared UI components

- [ ] T001 Install frontend markdown dependencies: `react-markdown`, `remark-gfm`, `rehype-sanitize`, `react-syntax-highlighter` in `frontend/package.json`
- [ ] T002 Install frontend UI dependencies: `lucide-react` in `frontend/package.json`
- [ ] T003 [P] Create reusable `Modal` component in `frontend/src/components/ui/Modal.tsx`
- [ ] T004 [P] Create reusable `Button` component in `frontend/src/components/ui/Button.tsx`
- [ ] T005 [P] Create reusable `Input` component in `frontend/src/components/ui/Input.tsx`
- [ ] T006 Run `npm install` in `frontend/` to lock dependency changes

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend infrastructure that MUST be complete before user stories can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Database & Entities

- [ ] T007 Create Flyway migration V014: `backend/src/main/resources/db/migration/V014__user_preferences_and_session_model.sql`
- [ ] T008 [P] Create `UserPreference` entity in `backend/src/main/kotlin/com/octopusllm/userconfig/UserPreference.kt`
- [ ] T009 [P] Create `UserPreferenceRepository` in `backend/src/main/kotlin/com/octopusllm/userconfig/UserPreferenceRepository.kt`
- [ ] T010 Add `selectedModelId` field to `ChatSession` entity in `backend/src/main/kotlin/com/octopusllm/chat/ChatSession.kt`

### Backend Services & APIs

- [ ] T011 Extend `UserConfigService` with preference CRUD in `backend/src/main/kotlin/com/octopusllm/userconfig/UserConfigService.kt`
- [ ] T012 Add `/api/v1/user/preferences` endpoints (GET, PUT, PATCH) to `UserConfigController` in `backend/src/main/kotlin/com/octopusllm/userconfig/UserConfigController.kt`
- [ ] T013 Add `deleteSession` method to `ChatService` in `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`
- [ ] T014 Update `ChatService.createSession` to accept and store `selectedModelId` in `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`
- [ ] T015 Add DELETE endpoint to `ChatController` in `backend/src/main/kotlin/com/octopusllm/chat/ChatController.kt`
- [ ] T016 Add `selectedModelId` to `SessionResponse` DTO in `backend/src/main/kotlin/com/octopusllm/chat/ChatController.kt`

### Frontend API Layer

- [ ] T017 [P] Add preference types to `frontend/src/lib/types/api.ts`
- [ ] T018 [P] Add preference API functions to `frontend/src/lib/api/userConfig.ts`
- [ ] T019 [P] Add `deleteSession` to `frontend/src/lib/api/chat.ts`

**Checkpoint**: Foundation ready - migrations run, backend builds, frontend dependencies installed

---

## Phase 3: User Story 1 - Modern Configuration Page (Priority: P1) 🎯 MVP

**Goal**: Redesign settings page with modern, minimal, responsive UI using modal dialogs for Add Key and Add Model

**Independent Test**: Navigate to `/settings/models`, verify clean layout, click Add Key → modal opens, add key → modal closes, model section updates

### Implementation

- [ ] T020 [US1] Redesign `SettingsLayout` container in `frontend/src/components/models/SettingsLayout.tsx`
- [ ] T021 [P] [US1] Wrap `ApiKeyForm` in modal in `frontend/src/components/models/ApiKeyForm.tsx`
- [ ] T022 [P] [US1] Wrap `CustomModelForm` in modal in `frontend/src/components/models/CustomModelForm.tsx`
- [ ] T023 [US1] Redesign settings page in `frontend/src/app/(app)/settings/models/page.tsx`
- [ ] T024 [US1] Apply Tailwind responsive classes to settings page for 320px–2560px support
- [ ] T025 [US1] Add empty-state prompt when no API keys configured

**Checkpoint**: Settings page is visually modern, responsive, and uses modals for key/model addition

---

## Phase 4: User Story 2 - Dynamic Model Loading (Priority: P1)

**Goal**: Model list dynamically reflects available providers based on configured API keys

**Independent Test**: Add OpenAI key → OpenAI models appear; remove key → models disappear

### Implementation

- [ ] T026 [US2] Create `ModelList` component with provider grouping in `frontend/src/components/models/ModelList.tsx`
- [ ] T027 [US2] Update `ModelSelectorPanel` to use dynamic `ModelList` in `frontend/src/components/chat/ModelSelectorPanel.tsx`
- [ ] T028 [US2] Add provider validation status display (valid/invalid key indicator)
- [ ] T029 [US2] Wire frontend to re-fetch models after key add/remove operations

**Checkpoint**: Model list updates automatically when API keys change; no manual refresh needed

---

## Phase 5: User Story 3 - Rich Chat Output with Streaming (Priority: P1)

**Goal**: Chat window renders Markdown and HTML in real-time during SSE streaming

**Independent Test**: Send message → verify code blocks, tables, bold/italic render progressively during streaming

### Implementation

- [ ] T030 [US3] Create `MarkdownRenderer` component in `frontend/src/components/chat/MarkdownRenderer.tsx`
- [ ] T031 [US3] Integrate `react-markdown` + `remark-gfm` + `rehype-sanitize` in renderer
- [ ] T032 [US3] Add code block syntax highlighting via `react-syntax-highlighter`
- [ ] T033 [US3] Implement debounced re-render (100ms) for streaming performance
- [ ] T034 [US3] Add error boundary with fallback to raw text for malformed markdown
- [ ] T035 [US3] Update `[sessionId]/page.tsx` to use `MarkdownRenderer` for assistant responses
- [ ] T036 [US3] Ensure progressive rendering avoids layout shifts during streaming

**Checkpoint**: Markdown renders correctly in real-time; HTML is sanitized; no flicker or layout shifts

---

## Phase 6: User Story 4 - Persistent Model Selection (Priority: P2)

**Goal**: Last selected model is remembered across sessions

**Independent Test**: Select model → close app → reopen → same model pre-selected

### Implementation

- [ ] T037 [US4] Create `usePreferences` hook in `frontend/src/lib/hooks/usePreferences.ts`
- [ ] T038 [US4] Update `ChatInput` to persist model selection via preferences API in `frontend/src/components/chat/ChatInput.tsx`
- [ ] T039 [US4] Update `ModelSelectorPanel` to restore last selected model on load in `frontend/src/components/chat/ModelSelectorPanel.tsx`
- [ ] T040 [US4] Handle unavailable model: show notification + select fallback
- [ ] T041 [US4] Add first-time user default model selection or prompt

**Checkpoint**: Model preference persists across app restarts; gracefully handles removed models

---

## Phase 7: User Story 5 - Session-Based Chat History (Priority: P2)

**Goal**: Conversations organized into sessions with persistent history, sidebar list, load/delete

**Independent Test**: Create session → send messages → close app → reopen → see session in sidebar → click → history loads → delete → gone

### Implementation

- [ ] T042 [US5] Create `useSessions` hook in `frontend/src/lib/hooks/useSessions.ts`
- [ ] T043 [US5] Create `SessionSidebar` component in `frontend/src/components/chat/SessionSidebar.tsx`
- [ ] T044 [US5] Add session list rendering with title/preview and recency sorting
- [ ] T045 [US5] Integrate `SessionSidebar` into chat layout in `frontend/src/app/(app)/chat/page.tsx`
- [ ] T046 [US5] Update `chat/page.tsx` to create new session on first message
- [ ] T047 [US5] Add session click handler to load history via `GET /api/v1/chat/sessions/{id}`
- [ ] T048 [US5] Add session delete button with confirmation dialog
- [ ] T049 [US5] Update `[sessionId]/page.tsx` to load and display session history

**Checkpoint**: Sessions persist, appear in sidebar, load history, and can be deleted

---

## Phase 8: User Story 6 - Conversation Threading (Priority: P3)

**Goal**: Messages within a session form a coherent threaded conversation

**Independent Test**: Send multiple messages → verify alternating user/assistant with clear visual distinction

### Implementation

- [ ] T050 [US6] Create `MessageThread` component in `frontend/src/components/chat/MessageThread.tsx`
- [ ] T051 [US6] Render user and assistant messages with distinct visual styling
- [ ] T052 [US6] Ensure chronological ordering with 100% accuracy via `sequenceNum`
- [ ] T053 [US6] Optimize scroll performance for long conversations (20+ messages)
- [ ] T054 [US6] Append new messages to bottom of existing thread without full re-render

**Checkpoint**: Messages thread correctly with clear visual distinction; scroll performance acceptable

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Responsive design, theme support, performance optimization, final validation

- [ ] T055 [P] Apply responsive design to all chat components (sidebar, thread, input) using Tailwind breakpoints
- [ ] T056 [P] Add dark/light theme CSS variables to `frontend/src/app/globals.css`
- [ ] T057 Update `frontend/src/app/layout.tsx` to support theme switching
- [ ] T058 [P] Add loading states and skeleton screens for session list
- [ ] T059 [P] Add error handling and toast notifications for API failures
- [ ] T060 Verify `backend/build.gradle.kts` builds successfully (`./gradlew build`)
- [ ] T061 Verify frontend TypeScript compiles (`npx tsc --noEmit`)
- [ ] T062 Run quickstart.md validation scenarios manually

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies
- **Phase 2 (Foundational)**: Depends on Phase 1; BLOCKS all user stories
- **Phase 3–8 (User Stories)**: All depend on Phase 2 completion
  - Can proceed in priority order (P1 → P2 → P3)
  - Or in parallel if team capacity allows
- **Phase 9 (Polish)**: Depends on all user stories

### User Story Dependencies

- **US1 (Config Page)**: Starts after Phase 2; no other dependencies
- **US2 (Dynamic Models)**: Starts after Phase 2; integrates with US1 components
- **US3 (Markdown Streaming)**: Starts after Phase 2; no dependencies on US1/US2
- **US4 (Model Persistence)**: Starts after Phase 2; depends on backend preference API from Phase 2
- **US5 (Session History)**: Starts after Phase 2; depends on backend session API from Phase 2
- **US6 (Threading)**: Starts after Phase 2; builds on US5 components

### Within Each User Story

- Frontend API layer (Phase 2) before any story-specific work
- UI components before page integration
- Backend services before frontend integration

### Parallel Opportunities

- Phase 1: All dependency installations marked [P] can run in parallel
- Phase 2: Backend entity + repository tasks marked [P] can run in parallel
- Phase 2: Frontend API type + function tasks marked [P] can run in parallel
- Across user stories: US1, US2, and US3 are independent and can be worked on in parallel after Phase 2
- US4 and US5 are independent and can be worked on in parallel
- Phase 9: All polish tasks marked [P] can run in parallel

---

## Parallel Example: User Story 1 + User Story 2

```bash
# After Phase 2 completes, launch US1 and US2 in parallel:

# Developer A: User Story 1 (Settings Redesign)
Task: "Redesign SettingsLayout in frontend/src/components/models/SettingsLayout.tsx"
Task: "Wrap ApiKeyForm in modal in frontend/src/components/models/ApiKeyForm.tsx"
Task: "Wrap CustomModelForm in modal in frontend/src/components/models/CustomModelForm.tsx"
Task: "Redesign settings page in frontend/src/app/(app)/settings/models/page.tsx"

# Developer B: User Story 2 (Dynamic Model Loading)
Task: "Create ModelList component in frontend/src/components/models/ModelList.tsx"
Task: "Update ModelSelectorPanel in frontend/src/components/chat/ModelSelectorPanel.tsx"
Task: "Add provider validation status display"
```

---

## Implementation Strategy

### MVP First (User Stories 1–3)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: US1 - Modern Configuration Page
4. Complete Phase 4: US2 - Dynamic Model Loading
5. Complete Phase 5: US3 - Rich Chat Output with Streaming
6. **STOP and VALIDATE**: Test core chat + settings functionality
7. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add US1 + US2 → Test independently → Deploy/Demo (Core config works)
3. Add US3 → Test independently → Deploy/Demo (Rich chat works)
4. Add US4 → Test independently → Deploy/Demo (Preferences persist)
5. Add US5 + US6 → Test independently → Deploy/Demo (Full session management)
6. Polish → Final validation

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 + US2 (settings + dynamic models)
   - Developer B: US3 (markdown streaming)
   - Developer C: US4 + US5 + US6 (persistence + sessions + threading)
3. Stories complete and integrate independently
4. Team converges on Polish phase

---

## Task Summary

| Phase | Tasks | Description |
|-------|-------|-------------|
| Phase 1: Setup | T001–T006 | Dependency installation + shared UI components |
| Phase 2: Foundational | T007–T019 | Backend migration, entities, services, APIs |
| Phase 3: US1 | T020–T025 | Settings page redesign with modals |
| Phase 4: US2 | T026–T029 | Dynamic model loading |
| Phase 5: US3 | T030–T036 | Markdown/HTML streaming render |
| Phase 6: US4 | T037–T041 | Model preference persistence |
| Phase 7: US5 | T042–T049 | Session history sidebar + CRUD |
| Phase 8: US6 | T050–T054 | Conversation threading |
| Phase 9: Polish | T055–T062 | Responsive design, theme, validation |

**Total Tasks**: 62  
**MVP Scope**: Phases 1–5 (Setup + Foundational + US1 + US2 + US3) = 36 tasks  
**Full Feature**: All phases = 62 tasks