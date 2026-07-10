# Research: Unified Tool Calling and Time Awareness

**Feature**: Unified Tool Calling and Time Awareness  
**Date**: 2026-07-10  
**Purpose**: Record design decisions, alternatives, and rationale for the implementation plan.

## Decision R1: Unified, provider-independent event model

**Decision**: Introduce a single `UnifiedInteractionEvent` sealed class representing tool calls, tool results, tool status, and text tokens. The application layer (chat orchestration, UI) operates only on this format. Each LLM adapter maps its provider's native tool-calling representation to/from this format.

**Rationale**: The feature's core requirement is that "the application layer can uniformly process tool interactions while the adapter layer only handles provider-specific behavior." A shared event model prevents provider logic from leaking into chat orchestration and the frontend. It also makes it possible to add new providers by adding only an adapter mapping.

**Alternatives considered**:
- **Per-provider event streams**: Rejected because the application layer would need to branch on provider to handle tool calls, violating the unified-layer goal.
- **Adapter-agnostic raw JSON blobs**: Rejected because it would push schema knowledge into the application layer and make validation/testing harder.

## Decision R2: In-memory per-turn deduplication

**Decision**: Within a single turn, if multiple models request the same tool with the same arguments, the unified layer executes the tool once and shares the result. Tool execution results are stored in the database with a join table to each requesting `provider_response`.

**Rationale**: Deduplication reduces external API cost and latency while still giving each model a consistent answer. Persisting through a join table preserves the immutable-session principle and lets analytics reconstruct exactly which result each model consumed.

**Alternatives considered**:
- **No deduplication (each model executes independently)**: Rejected because it multiplies external API cost and can produce inconsistent answers if the tool result changes between calls.
- **Cross-turn caching**: Rejected for the first release because it introduces cache-invalidation complexity and potential staleness; the spec scopes deduplication to a single turn.

## Decision R3: Separate `tool_invocations` table with join table

**Decision**: Add a `tool_invocations` table to store each unique execution and a `provider_response_tool_invocations` join table to record which model responses consumed which execution.

**Rationale**: A normalized model supports shared invocations cleanly and keeps `provider_responses` immutable (no JSONB mutation). Analytics can query tool usage, latency, and failure rates without parsing JSONB. The join table records per-response lineage for reproducibility.

**Alternatives considered**:
- **JSONB column on `provider_responses`**: Rejected because it duplicates execution data when multiple models share a result and makes shared-lineage queries harder.
- **Single wide table with nullable `shared_invocation_id`**: Rejected because it mixes per-execution and per-response fields in one table, leading to sparse columns and unclear ownership.

## Decision R4: Built-in tools as code + configuration, not admin CRUD

**Decision**: The first release ships built-in tools (current time, web search, stock quote, weather, news) implemented in code and enabled via configuration. An administrative UI for defining new tools is deferred.

**Rationale**: The spec explicitly states the first release focuses on "built-in tools administered by the platform." Implementing them in code keeps the change focused and avoids a new admin CRUD surface. Third-party/MCP registries can be added later without changing the architecture.

**Alternatives considered**:
- **Admin CRUD for tool definitions**: Rejected because it expands scope beyond the first release and introduces validation/security concerns for arbitrary tool definitions.
- **MCP server registry from the start**: Rejected because MCP discovery, auth, and versioning are complex and not required for the built-in tool set.

## Decision R5: Time context always injected, not conditionally

**Decision**: The current date, time, and timezone are always included in the conversation context as a system-level prompt, rather than being injected only when time-sensitive keywords are detected.

**Rationale**: Conditional injection requires a heuristic that can miss implicit time references (e.g., "开盘了吗" implies today). The overhead of a single time line is negligible, and the clarification explicitly chose this behavior.

**Alternatives considered**:
- **Keyword detection**: Rejected because of missed edge cases and added complexity.
- **Tool-based time lookup**: Rejected as the default; a dedicated `current_time` tool can still be invoked for authoritative time, but the baseline context is always present.

## Decision R6: No automated PII redaction in tool arguments

**Decision**: Tool arguments are forwarded to external tools as-is. The platform does not automatically mask or redact PII. Tool availability is gated by user permission, and users are responsible for not submitting sensitive data.

**Rationale**: This is the explicit product choice from the clarification session. Implementing robust, configurable PII redaction would require a much larger scope (regex/classifier maintenance, false-positive handling, admin policy UI). Permission gating provides a basic control point.

**Alternatives considered**:
- **Default regex-based redaction**: Rejected because it gives a false sense of security and could break legitimate queries with false positives.
- **Admin-configurable redaction policy**: Rejected as out of scope for the first release; can be added later without changing the architecture.

## Decision R7: Tool timeout and retry policy

**Decision**: Each tool call has a 15-second timeout and one retry using short exponential backoff. If the retry fails, the error is returned to the model and surfaced to the user if the model cannot recover.

**Rationale**: 15 seconds leaves enough time for most external APIs while preserving the 30-second end-to-end target. One retry with backoff handles transient failures without multiplying latency. This matches the clarification outcome.

**Alternatives considered**:
- **10 seconds, no retry**: Rejected because it fails too often on slow external APIs.
- **30 seconds, no retry**: Rejected because it consumes the entire end-to-end budget and leaves no room for model reasoning.
