# Data Model: Unified Tool Calling and Time Awareness

**Feature**: Unified Tool Calling and Time Awareness  
**Date**: 2026-07-10

## Overview

The data model adds persistent storage for tool execution metadata while preserving the immutable-session principle. Tool definitions are built-in and configured in code for the first release, so no new entity is required for them. Tool executions are stored in a normalized `tool_invocations` table; a join table records which immutable `provider_responses` consumed each execution. This supports deduplication, reproducibility, and analytics without mutating response snapshots.

## Entities

### ToolInvocation

Represents a single execution of a tool, which may be shared by multiple model responses in the same turn.

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| id | UUID | No | Primary key |
| quest_id | UUID | No | Foreign key to `chat_sessions` (Quest) |
| turn_id | UUID | No | Foreign key to `chat_turns` |
| tool_name | VARCHAR | No | Built-in tool name, e.g., `web_search` |
| arguments_hash | VARCHAR | No | Hash of canonical arguments for deduplication |
| arguments | JSONB | No | Actual arguments sent to the tool |
| result | JSONB | Yes | Tool result when successful |
| error_message | TEXT | Yes | Error detail when failed |
| status | VARCHAR | No | `pending`, `running`, `success`, `failed`, `timeout` |
| started_at | TIMESTAMPTZ | Yes | When execution began |
| completed_at | TIMESTAMPTZ | Yes | When execution ended |
| created_at | TIMESTAMPTZ | No | Record creation time |
| updated_at | TIMESTAMPTZ | No | Last update time |

**Uniqueness**: `(quest_id, turn_id, tool_name, arguments_hash)` is unique so that identical invocations within a turn are deduplicated.

**Relationships**:
- Many `ProviderResponseToolInvocations` (one per model response that requested this tool)
- Belongs to one `ChatSession` (quest) and one `ChatTurn`

### ProviderResponseToolInvocation

Join table linking an immutable provider response to the tool executions it used.

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| id | UUID | No | Primary key |
| provider_response_id | UUID | No | Foreign key to `provider_responses` |
| tool_invocation_id | UUID | No | Foreign key to `tool_invocations` |
| created_at | TIMESTAMPTZ | No | Record creation time |

**Relationships**:
- Many-to-one with `ProviderResponse`
- Many-to-one with `ToolInvocation`

## Existing Entities Updated (Runtime Only)

### LlmRequest

Extended at runtime only; no database migration.

| Field | Type | Notes |
|-------|------|-------|
| systemPrompt | String | Includes current date/time context |
| tools | List<ToolDefinition> | Tools available to the model this turn |

### LlmStreamEvent

Extended at runtime only; no database migration.

| Variant | Purpose |
|---------|---------|
| ToolCall | Model requests a tool invocation |
| ToolResult | Result of a completed tool invocation |
| ToolStatus | Pending/running/completed/failed status for UI |
| Token | Existing text token variant |

## Migration

**V037__tool_invocations.sql**

```sql
CREATE TABLE tool_invocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quest_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    turn_id UUID NOT NULL REFERENCES chat_turns(id) ON DELETE CASCADE,
    tool_name VARCHAR(64) NOT NULL,
    arguments_hash VARCHAR(64) NOT NULL,
    arguments JSONB NOT NULL,
    result JSONB,
    error_message TEXT,
    status VARCHAR(16) NOT NULL CHECK (status IN ('pending', 'running', 'success', 'failed', 'timeout')),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (quest_id, turn_id, tool_name, arguments_hash)
);

CREATE INDEX idx_tool_invocations_quest_turn ON tool_invocations(quest_id, turn_id);
CREATE INDEX idx_tool_invocations_status ON tool_invocations(status);

CREATE TABLE provider_response_tool_invocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_response_id UUID NOT NULL REFERENCES provider_responses(id) ON DELETE CASCADE,
    tool_invocation_id UUID NOT NULL REFERENCES tool_invocations(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider_response_id, tool_invocation_id)
);

CREATE INDEX idx_pr_tool_invocations_response ON provider_response_tool_invocations(provider_response_id);
CREATE INDEX idx_pr_tool_invocations_invocation ON provider_response_tool_invocations(tool_invocation_id);
```

## Validation Rules

- `tool_name` must match a tool registered in the platform's built-in tool registry.
- `arguments` must conform to the JSON schema defined by the tool's `ToolDefinition`.
- `status` transitions are: `pending` → `running` → (`success` | `failed` | `timeout`).
- `result` is required when `status` is `success`; `error_message` is required when `status` is `failed` or `timeout`.
- The unique constraint enforces per-turn deduplication of identical tool invocations.

## State Transitions

```
[pending]
   |
   v
[running]
   |
   +---> [success]   (result populated)
   |
   +---> [failed]     (error_message populated)
   |
   +---> [timeout]    (error_message populated)
```

## Notes

- No changes are made to existing `chat_sessions`, `chat_turns`, or `provider_responses` tables; the new tables are append-only and link to them.
- Tool definitions are not stored in the database in the first release; they are built into the application and enabled through configuration/permissions.
- Time context is generated at runtime and does not require a database entity.
