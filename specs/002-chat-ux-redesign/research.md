# Research: Chat UX Redesign and Session Persistence

**Feature**: Chat UX Redesign and Session Persistence  
**Date**: 2026-06-10  
**Status**: Complete

## Research Topics

### 1. Markdown/HTML Streaming Rendering in React

**Decision**: Use `react-markdown` with `remark-gfm` for markdown parsing, combined with a custom streaming renderer.

**Rationale**:
- The frontend currently has NO markdown rendering library (only raw Next.js + React + Tailwind)
- LLM outputs frequently contain markdown (code blocks, tables, lists) that must render in real-time during SSE streaming
- `react-markdown` is the industry standard for React markdown rendering, supporting all required elements (headers, lists, code blocks, tables, inline formatting)
- For HTML sanitization within markdown, `rehype-sanitize` provides OWASP-compliant HTML filtering
- Streaming challenge: markdown parsers typically need complete text. Solution: render progressively using a partial parser or re-render on each SSE token event with debouncing

**Alternatives considered**:
- **Marked.js**: Lighter weight but less React-idiomatic; requires manual DOM manipulation
- **Custom parser**: Would require significant maintenance for edge cases (nested lists, code fences, tables)
- **DOMPurify on raw HTML**: Rejected — LLMs output markdown, not HTML primarily. Sanitizing HTML is secondary to parsing markdown

**Implementation approach**:
- Parse markdown incrementally as tokens arrive via SSE
- Use React state to hold accumulated response text
- Re-render markdown on each token with debounce (50-100ms) to avoid excessive re-renders
- For code blocks: apply syntax highlighting via `react-syntax-highlighter` or Prism.js

---

### 2. Session Persistence Architecture

**Decision**: Extend existing backend entities (ChatSession, ChatTurn) with additional fields; frontend stores minimal state in localStorage for UI preferences only.

**Rationale**:
- Backend already has `ChatSession` and `ChatTurn` entities with JPA repositories
- Session data (messages, history) is already persisted to PostgreSQL
- The backend API already supports: create session, list sessions, get session, submit turn
- What's missing: frontend session management UI, model preference persistence, and enhanced turn rendering
- User model preference will be stored in backend (UserPreference entity or extended UserModelConfig) rather than localStorage to survive cross-device usage

**Alternatives considered**:
- **LocalStorage-only persistence**: Rejected — violates API-First principle (constitution II); users expect cloud-synced history
- **IndexedDB on frontend**: Rejected — adds unnecessary complexity; backend already handles persistence
- **Hybrid (backend + local cache)**: Considered but YAGNI — backend queries are fast enough for session lists

---

### 3. Configuration Page UX Patterns

**Decision**: Use modal dialogs (or slide-out panels) for Add Key and Add Model actions; implement provider-grouped model lists with dynamic filtering.

**Rationale**:
- User explicitly requested that Add Key and Add Model be "independent" and "not always showing"
- Modal dialogs are the standard pattern for focused, interruptive configuration tasks
- Dynamic model loading based on configured keys is already partially implemented (ProviderModelSyncService exists)
- Provider-grouped lists improve scannability when multiple providers are configured

**Alternatives considered**:
- **Accordion/expandable sections**: Less focused than modals; still consumes page space when collapsed
- **Wizard/stepper**: Overkill for single-key or single-model addition
- **Inline editing**: Would clutter the settings page, contrary to user's "modern, minimal" requirement

---

### 4. Responsive Design Strategy

**Decision**: Use Tailwind CSS responsive prefixes (`sm:`, `md:`, `lg:`) with a mobile-first approach.

**Rationale**:
- Project already uses Tailwind CSS v4
- Mobile-first is the Tailwind-recommended approach and aligns with modern CSS practices
- The spec requires support from 320px to 2560px width
- Key breakpoints: `sm:640px` (tablet), `md:768px` (small desktop), `lg:1024px` (desktop), `xl:1280px` (large desktop)

---

### 5. Frontend State Management for Chat

**Decision**: Use React Server Components where possible; client components only for interactivity. Use React Context for global state (current session, model preference) only if needed; prefer URL-based state for session selection.

**Rationale**:
- Constitution mandates "Server Components where possible; client components only for interactivity"
- URL-based session selection (`/chat/[sessionId]`) already exists in the project structure
- Model preference can be fetched from backend on app load and cached in a lightweight context
- Chat streaming requires client components (SSE connection, real-time updates)

**Alternatives considered**:
- **Zustand/Redux**: YAGNI — not justified for this feature's state complexity
- **React Query/TanStack Query**: Would be useful for server state but adds dependency; current project uses plain fetch (base.ts)

---

## Resolved Unknowns

All NEEDS CLARIFICATION items from Technical Context have been resolved:

| Unknown | Resolution | Source |
|---------|-----------|--------|
| Markdown rendering library | react-markdown + remark-gfm + rehype-sanitize | Industry standard, React-idiomatic |
| HTML sanitization strategy | rehype-sanitize (OWASP-compliant) | Built into react-markdown ecosystem |
| Session persistence | Extend existing backend entities | Already implemented in backend |
| Model preference storage | Backend UserPreference entity | Cross-device consistency |
| Responsive approach | Tailwind CSS mobile-first | Already in project tech stack |
| State management | Minimal — URL + light context | Constitution compliance |

## Dependencies to Add

### Frontend
- `react-markdown`: Markdown rendering
- `remark-gfm`: GitHub-flavored markdown (tables, strikethrough, task lists)
- `rehype-sanitize`: HTML sanitization within markdown
- `react-syntax-highlighter`: Code block syntax highlighting (optional but recommended)
- `lucide-react`: Modern icon set (likely already needed for UI improvements)

### Backend
- No new dependencies required for this feature. All persistence is handled by existing JPA/PostgreSQL stack.

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Streaming markdown rendering causes performance issues | Medium | High | Debounce re-renders; virtualize long conversations |
| Markdown parser breaks on malformed input | Medium | Medium | Wrap parser in try-catch; fallback to raw text |
| Large session history (>1000 messages) loads slowly | Medium | Medium | Implement pagination/infinite scroll for message history |
| Responsive design breaks at unusual viewport sizes | Low | Low | Test across standard breakpoints; use fluid layouts |

## Notes

- The existing backend already provides 80% of the session persistence infrastructure
- The primary work is frontend: markdown rendering, session UI, settings redesign
- Backend changes are minimal: add UserPreference entity, possibly extend ChatSession with modelId field
- The feature aligns well with constitution principles: API-first (backend already exposes session APIs), provider-agnostic (no provider changes needed), immutable sessions (already append-only)