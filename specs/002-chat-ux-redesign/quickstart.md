# Quickstart: Chat UX Redesign and Session Persistence

**Feature**: Chat UX Redesign and Session Persistence  
**Date**: 2026-06-10  
**Status**: Draft

## Prerequisites

- Docker and Docker Compose installed
- Node.js 24+ (for frontend development)
- Java 21+ (for backend development)
- PostgreSQL running (via Docker Compose or local install)

## Environment Setup

### 1. Start Infrastructure

```bash
cd /Users/gqq/OpenSource/octopus-llm
docker-compose up -d postgres
```

### 2. Run Backend

```bash
cd backend
./gradlew bootRun
```

The backend will start on `http://localhost:8080` and apply Flyway migrations automatically.

### 3. Run Frontend

```bash
cd frontend
npm install  # Install new dependencies (react-markdown, etc.)
npm run dev
```

The frontend will start on `http://localhost:3000`.

## Validation Scenarios

### Scenario 1: Configuration Page Redesign

**Goal**: Verify modern, minimal settings page with modal-based key/model management.

**Steps**:
1. Navigate to `http://localhost:3000/settings/models`
2. Verify the page uses a clean, modern layout with adequate whitespace
3. Verify "Add API Key" is not visible by default — click a "Add Key" button to open a modal/dialog
4. Add a test API key (use a fake key for testing)
5. Verify the model list updates to show models for that provider
6. Click "Add Custom Model" — verify a modal/dialog opens
7. Add a custom model configuration
8. Verify the new model appears in the list
9. Resize browser window to 375px width — verify layout adapts responsively

**Expected Result**: Settings page is visually appealing, modals work correctly, model list updates dynamically.

---

### Scenario 2: Dynamic Model Loading

**Goal**: Verify model list filters based on configured API keys.

**Steps**:
1. Ensure no API keys are configured (or remove all)
2. Navigate to chat page (`/chat`)
3. Verify model selector shows a "Configure API keys" prompt or is disabled
4. Add an OpenAI API key in settings
5. Return to chat page
6. Verify OpenAI models appear in the selector
7. Remove the OpenAI key
8. Verify OpenAI models disappear

**Expected Result**: Model list always reflects currently available providers.

---

### Scenario 3: Markdown Streaming in Chat

**Goal**: Verify real-time markdown rendering during SSE streaming.

**Steps**:
1. Create a new chat session
2. Select a model (e.g., GPT-4o)
3. Send: "Write a Python function with docstring and show me a markdown table of its parameters"
4. Observe the response as it streams in
5. Verify code blocks appear with monospace formatting and syntax highlighting
6. Verify tables render with proper rows/columns
7. Verify headers, lists, and inline formatting render correctly
8. Send: "Show me bold text, italic text, and a link"
9. Verify formatting renders correctly

**Expected Result**: All markdown elements render progressively without layout shifts or flicker.

---

### Scenario 4: HTML Sanitization

**Goal**: Verify dangerous HTML is sanitized while safe HTML renders.

**Steps**:
1. Send: "Write HTML with a script tag saying alert('xss')"
2. Verify the script tag does NOT execute (no alert popup)
3. Verify the script tag appears as plain text or is removed
4. Send: "Write HTML with bold and italic text"
5. Verify `<b>` and `<i>` tags render as formatted text

**Expected Result**: Dangerous tags are neutralized; safe formatting tags work.

---

### Scenario 5: Model Preference Persistence

**Goal**: Verify selected model is remembered across sessions.

**Steps**:
1. Log in as a user
2. Select "claude-3-opus" in the chat model selector
3. Send a message
4. Close browser tab
5. Reopen application
6. Verify "claude-3-opus" is still selected
7. Remove the Anthropic API key
8. Refresh the page
9. Verify a notification appears about the unavailable model
10. Verify a fallback model is selected

**Expected Result**: Model preference persists and handles unavailable models gracefully.

---

### Scenario 6: Session Persistence

**Goal**: Verify conversations are saved and can be resumed.

**Steps**:
1. Create a new chat session
2. Send 3-4 messages
3. Note the session ID from the URL (`/chat/{sessionId}`)
4. Navigate to the chat home page (`/chat`)
5. Verify the session appears in the sidebar with a title/preview
6. Click the session
7. Verify all previous messages load with correct formatting
8. Send a new message
9. Verify it appends to the existing thread
10. Click "Delete" on the session
11. Confirm deletion
12. Verify the session disappears from the list

**Expected Result**: Sessions persist, load correctly, and can be deleted.

---

### Scenario 7: Conversation Threading

**Goal**: Verify messages form a coherent thread.

**Steps**:
1. Open a session with multiple turns
2. Verify user messages and assistant messages alternate
3. Verify visual distinction between user and assistant (different colors/alignment)
4. Scroll through a long conversation (20+ messages)
5. Verify smooth scrolling without lag

**Expected Result**: Messages are clearly threaded with good performance.

---

### Scenario 8: Responsive Design

**Goal**: Verify UI works across screen sizes.

**Steps**:
1. Open chat page on desktop (1920px width)
2. Verify sidebar, chat area, and input are well-proportioned
3. Resize to 768px (tablet)
4. Verify sidebar collapses or adapts
5. Resize to 375px (mobile)
6. Verify chat is usable; input is accessible
7. Verify settings page is usable at all sizes

**Expected Result**: UI is responsive and functional from 320px to 2560px.

## Backend API Validation

### Test User Preferences API

```bash
# Get preferences (requires auth)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/user/preferences

# Update preferences
curl -X PUT -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"lastSelectedModelId":"gpt-4o"}' \
  http://localhost:8080/api/v1/user/preferences
```

### Test Session Management

```bash
# Create session
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Session"}' \
  http://localhost:8080/api/v1/chat/sessions

# List sessions
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/chat/sessions?limit=10"

# Get session with turns
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/chat/sessions/{sessionId}

# Delete session
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/chat/sessions/{sessionId}
```

## Performance Checks

### Session List Load Time

```bash
# Should complete in < 500ms for 100 sessions
time curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/chat/sessions?limit=100"
```

### Message History Load Time

```bash
# Should complete in < 2 seconds for 50 turns
time curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/chat/sessions/{sessionId}
```

## Troubleshooting

### Issue: Markdown not rendering

- Check that `react-markdown` and plugins are installed: `npm list react-markdown`
- Verify SSE events contain text deltas (check browser Network tab)
- Check browser console for React rendering errors

### Issue: Sessions not persisting

- Verify PostgreSQL is running: `docker-compose ps`
- Check Flyway migration applied: query `user_preferences` table exists
- Check backend logs for SQL errors

### Issue: Model list not updating

- Verify API key is saved correctly (check backend logs)
- Verify `ProviderModelSyncService` is running
- Check browser console for API errors when fetching models

## Notes

- Use test API keys (not production keys) for validation
- The SSE stream can be tested directly via curl: `curl -N -H "Authorization: Bearer $TOKEN" -H "Accept: text/event-stream" ...`
- For frontend-only testing, mock the backend API using MSW or similar tools