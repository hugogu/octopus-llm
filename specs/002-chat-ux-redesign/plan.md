# Implementation Plan: Chat UX Redesign and Session Persistence

**Branch**: `002-chat-ux-redesign` | **Date**: 2026-06-10 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/002-chat-ux-redesign/spec.md`

## Summary

Redesign the configuration page with a modern, minimal, responsive UI; implement dynamic model loading based on configured API keys; add real-time markdown and HTML rendering to the chat window; persist user model preferences; and enhance session-based chat history with conversation threading.

**Technical approach**: Extend existing backend session/turn persistence with a `UserPreference` entity and `selected_model_id` on sessions. Frontend work is primary: adopt `react-markdown` with `remark-gfm` for streaming markdown rendering, redesign settings page with modal-based key/model management, and implement session sidebar with conversation threading.

## Technical Context

**Language/Version**: Kotlin 2.0.21 (JVM 21) backend; TypeScript 5 + React 19 frontend  
**Primary Dependencies**: Spring Boot 3.3.5 (WebFlux, Data JPA, Security), Next.js 16.2.7, Tailwind CSS v4  
**Storage**: PostgreSQL 15+ with Flyway migrations, JSONB for flexible schema fields  
**Testing**: JUnit 5 + MockK (backend); Vitest + React Testing Library (frontend)  
**Target Platform**: Web application (responsive: 320px–2560px width)  
**Project Type**: Full-stack web application (backend + frontend)  
**Performance Goals**: Session list <500ms for 100 items; message history <2s for 50 turns; streaming render debounced at 50–100ms  
**Constraints**: API-first design (all frontend via REST API); no direct DB access from frontend; SSE for streaming  
**Scale/Scope**: Single-user sessions persisted per account; target 100+ sessions per user without degradation

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Provider-Agnostic Abstraction | PASS | No provider changes required; feature is UI/persistence only |
| II. API-First Design | PASS | All new functionality exposed via `/api/v1/` endpoints; frontend consumes same APIs |
| III. Concurrent Execution & Streaming | PASS | Existing SSE streaming preserved; no changes to concurrent orchestration |
| IV. Data Integrity & Immutable Sessions | PASS | Sessions remain append-only; migration V014 follows Flyway versioning; soft deletes NOT used |
| V. Observability & Analytics | PASS | No new LLM calls introduced; existing analytics remain valid |
| VI. Security & User Key Privacy | PASS | No key handling changes; preferences store model IDs only (non-sensitive) |
| VII. Simplicity & Horizontal Scalability | PASS | No distributed locks; stateless API design; single new entity (UserPreference) |

**Re-check after Phase 1**: All principles remain PASS. No violations introduced.

## Project Structure

### Documentation (this feature)

```text
specs/002-chat-ux-redesign/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   ├── user-preferences-api.md
│   └── chat-sessions-api.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/kotlin/com/octopusllm/
│   ├── userconfig/
│   │   ├── UserPreference.kt              # NEW: User preferences entity
│   │   ├── UserPreferenceRepository.kt    # NEW: JPA repository
│   │   ├── UserConfigController.kt        # MODIFIED: Add /preferences endpoints
│   │   └── UserConfigService.kt           # MODIFIED: Add preference CRUD
│   ├── chat/
│   │   ├── ChatSession.kt                 # MODIFIED: Add selectedModelId
│   │   ├── ChatController.kt              # MODIFIED: Add DELETE endpoint
│   │   └── ChatService.kt                 # MODIFIED: Add deleteSession, update selectedModelId
│   └── config/
│       └── SecurityConfig.kt              # EXISTING: Ensure /preferences endpoints are secured
└── src/main/resources/db/migration/
    └── V014__user_preferences_and_session_model.sql  # NEW: Migration

frontend/
├── src/
│   ├── app/
│   │   ├── (app)/
│   │   │   ├── chat/
│   │   │   │   ├── page.tsx               # MODIFIED: Add session sidebar
│   │   │   │   └── [sessionId]/
│   │   │   │       └── page.tsx           # MODIFIED: Add markdown rendering
│   │   │   ├── settings/
│   │   │   │   └── models/
│   │   │   │       └── page.tsx           # MODIFIED: Redesign with modals
│   │   │   └── layout.tsx                 # MODIFIED: Add global preference context
│   │   └── layout.tsx                     # MODIFIED: Theme support
│   ├── components/
│   │   ├── chat/
│   │   │   ├── ChatInput.tsx              # MODIFIED: Persist model selection
│   │   │   ├── SessionSidebar.tsx         # NEW: Session list sidebar
│   │   │   ├── MessageThread.tsx          # NEW: Conversation threading
│   │   │   ├── MarkdownRenderer.tsx       # NEW: Streaming markdown component
│   │   │   └── ModelSelector.tsx          # MODIFIED: Dynamic loading
│   │   ├── models/
│   │   │   ├── ApiKeyForm.tsx             # MODIFIED: Modal wrapper
│   │   │   ├── CustomModelForm.tsx        # MODIFIED: Modal wrapper
│   │   │   ├── ModelList.tsx              # NEW: Provider-grouped model list
│   │   │   └── SettingsLayout.tsx         # NEW: Modern settings container
│   │   └── ui/
│   │       ├── Modal.tsx                  # NEW: Reusable modal component
│   │       ├── Button.tsx                 # NEW: Styled button component
│   │       └── Input.tsx                  # NEW: Styled input component
│   ├── lib/
│   │   ├── api/
│   │   │   ├── userConfig.ts              # MODIFIED: Add preference API calls
│   │   │   └── chat.ts                    # MODIFIED: Add deleteSession
│   │   ├── types/
│   │   │   └── api.ts                     # MODIFIED: Add preference types
│   │   └── hooks/
│   │       ├── usePreferences.ts          # NEW: Preference management hook
│   │       └── useSessions.ts             # NEW: Session management hook
│   └── styles/
│       └── globals.css                    # MODIFIED: Theme variables
└── package.json                           # MODIFIED: Add react-markdown, etc.
```

**Structure Decision**: The project already uses a clear backend/frontend split. This feature adds minimal backend changes (one entity, one migration, controller extensions) and substantial frontend changes (new components, redesign, markdown rendering). The structure above reflects the existing conventions.

## Complexity Tracking

No constitution violations. Complexity is justified:

| Component | Justification | Simpler Alternative Rejected Because |
|-----------|--------------|-------------------------------------|
| `react-markdown` + plugins | Industry standard for React markdown; handles all required elements (tables, code blocks, GFM) | Custom parser would require ongoing maintenance for edge cases; `marked.js` is less React-idiomatic |
| Modal-based settings | User explicitly requested independent add-key/add-model UI; keeps settings page minimal | Inline forms would clutter the page contrary to "modern, minimal" requirement |
| Debounced streaming render | Prevents excessive re-renders during high-frequency SSE events; 50–100ms debounce is imperceptible | Immediate re-render on every token causes CPU spikes and layout thrashing |

## Design Decisions

### Markdown Rendering Architecture

**Decision**: Accumulate raw text in React state; debounce markdown re-renders at 100ms intervals.

**Rationale**:
- Markdown parsers require complete text to generate valid AST
- Re-rendering on every SSE token (potentially 10–30 events/second) causes performance issues
- Debouncing at 100ms provides smooth visual updates while maintaining <60fps
- For very long responses (>4000 tokens), consider virtualizing or truncating render

**Trade-offs**:
- Slightly delayed formatting updates vs. performance
- Code blocks may appear partially formatted during streaming (acceptable UX)

### Session State Management

**Decision**: URL-based session selection (`/chat/{sessionId}`) with lightweight React Context for global preferences.

**Rationale**:
- URL-based state enables direct linking and browser back/forward
- Context is sufficient for `lastSelectedModelId`, `themePreference`, `sidebarCollapsed`
- No need for Zustand/Redux (YAGNI)

**Trade-offs**:
- Context re-renders all consumers on any preference change (mitigated by infrequent updates)

### Dynamic Model Loading

**Decision**: Frontend polls or refetches model list when settings change; backend `ProviderModelSyncService` already filters by configured keys.

**Rationale**:
- Existing backend service (`ProviderModelSyncService`) handles provider-model synchronization
- Frontend simply needs to re-fetch the catalog after key changes
- No real-time updates needed (model list changes infrequently)

**Trade-offs**:
- Brief stale data window after key addition (acceptable; user just added the key)

## Dependencies

### Frontend Additions

```json
{
  "react-markdown": "^9.0.0",
  "remark-gfm": "^4.0.0",
  "rehype-sanitize": "^6.0.0",
  "react-syntax-highlighter": "^15.5.0",
  "lucide-react": "^0.400.0"
}
```

**Rationale**:
- `react-markdown`: Core markdown rendering
- `remark-gfm`: Tables, strikethrough, task lists (LLMs frequently output tables)
- `rehype-sanitize`: OWASP-compliant HTML sanitization (constitution VI compliance)
- `react-syntax-highlighter`: Code block syntax highlighting (enhances UX significantly)
- `lucide-react`: Modern, lightweight icon set for UI components

### Backend Additions

None. All backend functionality uses existing Spring Data JPA, Flyway, and WebFlux stack.

## Migration Plan

### V014: User Preferences and Session Model

See [data-model.md](data-model.md) for full migration SQL.

**Deployment notes**:
- Migration is backward-compatible: existing sessions work without `selected_model_id`
- `user_preferences` table is empty until users interact with new features
- No data migration needed

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Streaming markdown causes jank/lag | Debounce re-renders; benchmark with large responses; consider virtual scrolling for long conversations |
| Markdown parser breaks on malformed input | Wrap parser in error boundary; fallback to plain text rendering |
| Large session history loads slowly | Implement pagination for turns (>50); lazy-load message content |
| Responsive design breaks on unusual viewports | Test on real devices; use fluid Tailwind utilities (`min-w-0`, `flex-shrink`) |
| Preference API conflicts with concurrent updates | Use optimistic UI; last-write-wins is acceptable for preferences |

## Performance Budget

| Metric | Target | Measurement |
|--------|--------|-------------|
| Session list API | <500ms | `time curl /api/v1/chat/sessions?limit=100` |
| Session detail API | <2s | `time curl /api/v1/chat/sessions/{id}` |
| Markdown render (per debounce) | <16ms | React DevTools Profiler |
| Settings page load | <1s | Lighthouse |
| Chat page load (with sidebar) | <1.5s | Lighthouse |
| Streaming token latency | <50ms per token | Browser Performance API |

## Testing Strategy

### Backend

- **Unit tests**: `UserConfigService` preference CRUD; `ChatService` deleteSession
- **Integration tests**: `UserConfigController` preference endpoints; `ChatController` delete endpoint
- **Migration test**: Flyway V014 applies cleanly on empty and populated databases

### Frontend

- **Unit tests**: `MarkdownRenderer` component (various markdown inputs); `SessionSidebar` (sorting, click handlers)
- **Integration tests**: Settings page flow (add key → model list updates); Chat flow (send message → session saves)
- **E2E tests**: Full user journey (create session → send messages → verify markdown → delete session)
- **Visual regression**: Settings page at 375px, 768px, 1920px widths

## Success Criteria Mapping

| Spec Criterion | Validation Method |
|----------------|-------------------|
| SC-001: Add key in <60s | User timing test / UX review |
| SC-002: Add model in <90s | User timing test / UX review |
| SC-003: 95% locate settings | Usability test with 5+ users |
| SC-004: Responsive 320px–2560px | Browser devtools + real device testing |
| SC-005: 100% markdown renders | Automated test with markdown test corpus |
| SC-006: 100% dangerous HTML sanitized | Automated test with XSS payload corpus |
| SC-007: 100% model restore | Integration test: select → restart → verify |
| SC-008: History loads in <2s | API timing test with 50-turn session |
| SC-009: Continue chat in <3 clicks | UX audit |
| SC-010: 100+ sessions no degradation | Load test: generate 100 sessions, measure list API |
| SC-011: 100% chronological order | Unit test: verify sequenceNum ordering |
| SC-012: 99% no layout shifts/flicker | Visual regression test + manual observation |

## Notes

- The existing backend provides ~80% of session persistence infrastructure; backend work is minimal
- Frontend work is substantial but well-scoped: markdown rendering, settings redesign, session UI
- The feature aligns with all constitution principles without violations
- No new backend dependencies keeps deployment simple
- Consider adding `react-virtuoso` later if conversation threading performance degrades with 1000+ messages (YAGNI for now)