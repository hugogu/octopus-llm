# Data Model: Chat UX Redesign and Session Persistence

**Feature**: Chat UX Redesign and Session Persistence  
**Date**: 2026-06-10  
**Status**: Draft

## Entity Overview

This feature extends the existing data model with minimal changes. The backend already has session and turn persistence. New entities are added for user preferences and session-model association.

## Existing Entities (No Changes Required)

### ChatSession

Already exists in `com.octopusllm.chat.ChatSession`.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | Unique session identifier |
| user | User | FK, NOT NULL | Owning user |
| title | VARCHAR(500) | NULL | Optional session title |
| created_at | TIMESTAMP | NOT NULL | Session creation time |
| updated_at | TIMESTAMP | NOT NULL | Last modification time |

### ChatTurn

Already exists in `com.octopusllm.chat.ChatTurn`.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | Unique turn identifier |
| session | ChatSession | FK, NOT NULL | Parent session |
| sequence_num | INTEGER | NOT NULL | Order within session |
| prompt_text | TEXT | NOT NULL | User prompt |
| attachments | JSONB | NULL | File attachments metadata |
| selected_model_ids | TEXT[] | NOT NULL | Models used for this turn |
| client_request_id | VARCHAR(100) | NULL | Client deduplication ID |
| created_at | TIMESTAMP | NOT NULL | Turn creation time |

### ProviderResponse

Already exists in `com.octopusllm.chat.ProviderResponse`.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | Unique response identifier |
| turn | ChatTurn | FK, NOT NULL | Parent turn |
| model_id | VARCHAR(255) | NOT NULL | Responding model |
| status | VARCHAR(50) | NOT NULL | success / error |
| response_text | TEXT | NULL | Full response content |
| error_message | TEXT | NULL | Error details if failed |
| input_tokens | INTEGER | NULL | Token count |
| output_tokens | INTEGER | NULL | Token count |
| latency_ms | INTEGER | NOT NULL | Response time |
| created_at | TIMESTAMP | NOT NULL | Response time |

### UserModelConfig

Already exists in `com.octopusllm.userconfig.UserModelConfig`.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | Unique config identifier |
| user | User | FK, NOT NULL | Owning user |
| model | ModelDefinition | FK, NOT NULL | Configured model |
| enabled | BOOLEAN | NOT NULL | Whether active |
| custom_params | JSONB | NULL | Custom parameters (added in previous feature) |

## New Entities

### UserPreference

**Purpose**: Store user-level preferences including last selected model and UI settings.

**Location**: `com.octopusllm.userconfig.UserPreference`

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | Unique preference identifier |
| user | User | FK, NOT NULL | Owning user (one-to-one) |
| last_selected_model_id | VARCHAR(255) | NULL | Most recently used model |
| theme_preference | VARCHAR(50) | NULL | light / dark / system |
| sidebar_collapsed | BOOLEAN | DEFAULT FALSE | Whether sidebar is collapsed |
| created_at | TIMESTAMP | NOT NULL | Creation time |
| updated_at | TIMESTAMP | NOT NULL | Last update time |

**Relationships**:
- One-to-one with `User`
- `last_selected_model_id` is a string reference (not FK) to allow graceful handling of removed models

### ChatSession (Extension)

**Addition**: Add `selected_model_id` column to track which model was primarily used for the session.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| selected_model_id | VARCHAR(255) | NULL | Primary model for this session |

**Rationale**: While `ChatTurn` stores models per-turn, having a session-level default simplifies UI restoration when reopening a session. The session-level model is the model used for the first turn (or the user's explicitly chosen default).

## Database Migrations

### Migration V014: Add User Preferences and Session Model

```sql
-- User preferences table
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_selected_model_id VARCHAR(255),
    theme_preference VARCHAR(50) DEFAULT 'system',
    sidebar_collapsed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_preferences_user UNIQUE (user_id)
);

CREATE INDEX idx_user_preferences_user ON user_preferences(user_id);

-- Add selected_model_id to chat_sessions
ALTER TABLE chat_sessions
    ADD COLUMN selected_model_id VARCHAR(255);

CREATE INDEX idx_chat_sessions_user_updated ON chat_sessions(user_id, updated_at DESC);
```

## Validation Rules

- `last_selected_model_id` must reference a model that the user has configured (or be NULL)
- `theme_preference` must be one of: `light`, `dark`, `system`
- Each user has exactly one `UserPreference` record (enforced by UK constraint)
- `ChatSession.selected_model_id` is informational only; turns may use different models

## State Transitions

### Session Lifecycle

```
[CREATED] → [ACTIVE] → [ARCHIVED]
   ↑                           ↓
   └───────────────────────────┘
```

- **CREATED**: Session created, no turns yet
- **ACTIVE**: Has one or more turns; appears in session list
- **ARCHIVED**: User deleted session; hard delete (not soft delete per constitution immutability principle)

### Preference Updates

User preferences are updated immediately on user action:
- Model selection → update `last_selected_model_id`
- Theme change → update `theme_preference`
- Sidebar toggle → update `sidebar_collapsed`

## Data Flow

### Creating a New Session

1. User clicks "New Chat"
2. Frontend creates session via `POST /api/v1/chat/sessions`
3. Backend creates `ChatSession` with `selected_model_id` = user's `last_selected_model_id`
4. Frontend redirects to `/chat/{sessionId}`

### Sending a Message

1. User types message and submits
2. Frontend sends `POST /api/v1/chat/sessions/{id}/turns`
3. Backend saves `ChatTurn` with `selected_model_ids`
4. Backend streams response via SSE
5. Frontend renders streaming markdown
6. On completion, backend saves `ProviderResponse`(s)

### Loading Session History

1. User clicks session in sidebar
2. Frontend fetches `GET /api/v1/chat/sessions/{id}`
3. Backend returns session + turns + responses
4. Frontend renders messages with markdown formatting

## Performance Considerations

- Session list queries MUST use `idx_chat_sessions_user_updated` for fast recency sorting
- Message history for a single session loads in one query (session + turns + responses joined)
- For sessions with >100 turns, consider pagination: `limit`/`offset` on turns
- User preference is fetched once on app initialization and cached

## Notes

- The existing `ProviderResponse` table already stores the full response text, which is sufficient for history replay
- No new tables needed for markdown rendering — formatting is done client-side from raw text
- The data model supports the "session box forming conversations" requirement through the existing turn/response structure
- Soft deletes are NOT used for sessions; deleted sessions are permanently removed (simple and aligns with immutability for created data)