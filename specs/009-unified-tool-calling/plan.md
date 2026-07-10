# Implementation Plan: Unified Tool Calling and Time Awareness

**Branch**: `009-unified-tool-calling` | **Date**: 2026-07-10 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/009-unified-tool-calling/spec.md`

## Summary

This feature introduces a **unified tool-calling interaction layer** and **time-aware context injection** so the platform can answer questions that depend on the current date (e.g., "今天 A 股怎么样") or require real-time external information (e.g., stock prices, weather, news). The application layer owns tool execution, deduplication, and result distribution in a provider-independent format; each LLM adapter only translates between that unified format and its provider's native tool-calling protocol. The first release includes built-in tools for current time, web search, stock quote, weather, and news retrieval, with tool arguments passed to external services as-is and availability gated by user permission.

## Technical Context

**Language/Version**: Kotlin 2.0.21 (JVM 21), TypeScript 5 / Node.js 24
**Primary Dependencies**: Spring Boot 3.3.5 WebFlux, Spring Data JPA/Hibernate, Flyway, jjwt; Next.js 16.2.7 App Router, React 19, Tailwind CSS v4
**Storage**: PostgreSQL 16 with Flyway migrations
**Testing**: Backend `cd backend && ./gradlew build`; frontend `cd frontend && npm run build && npm run lint && npm run test:run`
**Target Platform**: Linux server (Docker Compose), ARM64 local development / AMD64 production builds
**Project Type**: Web application (backend + frontend)
**Performance Goals**: Tool-driven end-to-end responses complete within 30 seconds in 90% of attempts; concurrent multi-model streaming is preserved; tool calls use a 15-second timeout with one retry.
**Constraints**: API-first (frontend consumes backend API only); no distributed locks; no plaintext key material in logs/responses/errors; Flyway migrations are forward-only; session/response snapshots remain immutable; tool arguments are not automatically redacted.
**Scale/Scope**: Multi-user LLM comparison platform; built-in tools administered by the platform; tool invocations are per-turn and may be shared across multiple model responses within the same turn through deduplication.

## Constitution Check

*GATE: evaluated against `.specify/memory/constitution.md` v1.2.0.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Provider-Agnostic Abstraction | ✅ Pass | The unified interaction layer is the central abstraction; all provider-specific tool-calling semantics are confined to the existing adapter modules. |
| II. API-First Design | ✅ Pass | New capability is delivered through the existing `/api/v2` chat streaming endpoints; the frontend consumes SSE events from the backend with no direct database access. |
| III. Concurrent Execution & Streaming | ✅ Pass | Each model's tool requests are processed independently by the unified layer; shared invocations are deduplicated but do not block other models. Streaming remains concurrent. |
| IV. Data Integrity & Immutable Sessions | ✅ Pass | Tool execution metadata is stored in a new append-only `tool_invocations` table with a join table to immutable `provider_responses`; no in-place mutation of response snapshots. |
| V. Observability & Analytics | ✅ Pass | Every tool invocation records start/end time, status, arguments, and result in a structured table; analytics can read from immutable snapshots. |
| VI. Security & User Key Privacy | ⚠️ Attention | Tool arguments are passed to external tools as-is by explicit product choice. No API keys or encrypted connection material are included in arguments. Tool availability is gated by user permission. This is documented as a deliberate tradeoff. |
| VII. Simplicity & Horizontal Scalability | ✅ Pass | No distributed locks; tool execution is stateless per request and relies on per-turn deduplication in memory. |
| VIII. UX Consistency & Visual Coherence | ✅ Pass | Tool-status indicators reuse existing message-thread components and design-system tokens; no new standalone pages are required. |

**Gate result**: PASS with one explicit security tradeoff documented in the Assumptions section of the spec. The adapter isolation and concurrent-streaming preservation are the primary architectural guards.

## Project Structure

### Documentation (this feature)

```text
specs/009-unified-tool-calling/
├── plan.md              # This file
├── research.md          # Phase 0 — design decisions and alternatives
├── data-model.md        # Phase 1 — entities and migrations
├── quickstart.md        # Phase 1 — end-to-end validation scenarios
├── contracts/           # Phase 1 — API contracts
│   └── chat-tool-events.md
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
backend/src/main/kotlin/com/octopusllm/
├── tool/                      # NEW package
│   ├── Tool.kt                      # executable tool interface
│   ├── ToolDefinition.kt            # declarative tool metadata
│   ├── ToolResult.kt                # result/error wrapper
│   ├── ToolRegistry.kt              # name -> Tool lookup
│   ├── ToolExecutor.kt              # timeout, retry, deduplication
│   ├── BuiltInTools.kt              # current_time, web_search, stock_quote, weather, news
│   ├── UnifiedInteractionEvent.kt   # sealed provider-independent events
│   └── UnifiedInteractionMapper.kt  # helpers to map events to/from adapter payloads
├── chat/
│   ├── ChatService.kt               # + inject time context, + attach tool definitions, + run tool loop
│   └── ChatControllerV2.kt          # unchanged endpoint shape; new SSE event types
├── llm/
│   ├── LlmRequest.kt                # + systemPrompt, + tools
│   ├── LlmStreamEvent.kt            # + ToolCall / ToolResult / ToolStatus
│   ├── ConcurrentLlmOrchestrator.kt # unchanged; consumes unified events
│   └── adapter/
│       ├── OpenAiCompatAdapter.kt   # + tool translation
│       ├── AnthropicAdapter.kt      # + tool translation
│       └── MiniMaxAdapter.kt        # capability gate; no tool translation if unsupported
└── entity/ or migration package     # ToolInvocation, ProviderResponseToolInvocation

backend/src/main/resources/db/migration/
└── V037__tool_invocations.sql       # new table + join table

frontend/src/
├── components/chat/
│   ├── MessageThread.tsx            # + render tool status chips
│   ├── ResponseGroup.tsx            # + show per-model tool calls
│   └── ToolStatusIndicator.tsx      # NEW small reusable component
└── lib/api/chatV2.ts                # + parse tool events from SSE
```

**Structure Decision**: Web application (existing backend + frontend). The new `tool` package isolates tool execution from chat orchestration and LLM adapters; chat/llm packages extend existing types without changing the project structure.

## Complexity Tracking

No constitution violations require justification. The single new `tool` package is justified by the need to keep provider-agnostic tool logic separate from both chat orchestration and provider-specific adapter code. The `tool_invocations` table is justified by the immutable-session requirement and the need for analytics to reproduce exactly what each model saw.

## Design Decisions (for research.md)

1. **Unified event model over per-adapter events**: All tool events flow through a single sealed class so the application layer does not branch on provider. Adapters only serialize/deserialize.
2. **In-memory per-turn deduplication**: Identical `(tool_name, arguments)` within one turn execute once and share results. This keeps the design lock-free and avoids external API cost amplification.
3. **Separate `tool_invocations` table with join table**: Normalizes shared executions while preserving per-response lineage in immutable snapshots.
4. **Built-in tools as code + config, not admin UI**: The first release focuses on platform-administered tools; adding an admin CRUD for tool definitions is deferred.
5. **Time context as always-on system prompt**: Avoids detection logic and ensures every model has the same temporal baseline, matching the clarification outcome.
