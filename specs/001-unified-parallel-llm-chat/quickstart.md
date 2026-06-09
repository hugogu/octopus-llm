# Quickstart Validation Guide: Unified Parallel LLM Chat

**Feature**: 001-unified-parallel-llm-chat
**Purpose**: Step-by-step guide to validate that the feature works end-to-end after implementation.
**Prerequisites**: Docker Compose up with backend + frontend + PostgreSQL running.

---

## Prerequisites

1. Docker Compose stack is running:
   ```bash
   docker compose up -d
   ```
2. Backend health check passes:
   ```bash
   curl http://localhost:8080/api/v1/health
   # Expected: {"status":"UP"}
   ```
3. Frontend is accessible at `http://localhost:3000`.
4. You have valid API keys for at least two providers (e.g., OpenAI + Anthropic or DeepSeek).

---

## Scenario 1: User Registration & Login

**Goal**: Verify the full auth flow works end-to-end.

### Step 1 — Register

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234!"}'
```

**Expected**: `201 Created` with `{"userId":"...","message":"Verification email sent..."}`

### Step 2 — Verify Email (dev mode)

In development, check the backend logs for the verification URL (or use a local SMTP tool
like Mailhog at `http://localhost:8025`). Copy the token from the link.

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"token":"<token-from-email>"}'
```

**Expected**: `200 OK` with success message.

### Step 3 — Login

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234!"}' \
  | jq '.accessToken'
```

**Expected**: A JWT string. Save this as `$TOKEN` for subsequent requests.

```bash
TOKEN="<paste-jwt-here>"
```

---

## Scenario 2: Configure API Keys and Enable Models

**Goal**: Verify a user can add an API key and activate a model.

### Step 1 — View Available Models

```bash
curl -s http://localhost:8080/api/v1/models | jq '.models[].id'
```

**Expected**: A list containing model IDs for the 6 supported providers.

### Step 2 — Add an OpenAI API Key

```bash
curl -s -X POST http://localhost:8080/api/v1/user/api-keys \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"providerId":"openai","apiKey":"sk-proj-...","label":"Test OpenAI key"}'
```

**Expected**: `201 Created` with `{"id":"...","providerId":"openai",...}`. Save `id` as `$OPENAI_KEY_ID`.

### Step 3 — Enable a Model

```bash
curl -s -X POST http://localhost:8080/api/v1/user/model-configs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"modelId\":\"gpt-4o-2024-11-20\",\"apiKeyId\":\"$OPENAI_KEY_ID\"}"
```

**Expected**: `201 Created` with `{"modelId":"gpt-4o-2024-11-20","isEnabled":true,...}`.

### Step 4 — Add a Second Provider

Repeat Steps 2–3 for a second provider (e.g., Anthropic or DeepSeek). This gives us two
models for parallel testing.

---

## Scenario 3: Parallel Chat with Real-time Streaming

**Goal**: Verify that prompts are dispatched concurrently and streamed independently.

### Step 1 — Create a Session

```bash
SESSION=$(curl -s -X POST http://localhost:8080/api/v1/chat/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Parallel test"}' | jq -r '.id')
echo "Session: $SESSION"
```

### Step 2 — Submit Prompt and Stream Responses

```bash
curl -s -X POST "http://localhost:8080/api/v1/chat/sessions/$SESSION/turns" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"promptText":"Say hello in 10 words","selectedModelIds":["gpt-4o-2024-11-20","<second-model-id>"]}'
```

**Expected SSE output** (tokens interleaved, not sequential):
```
data: {"event":"turn_created","turnId":"...","sequenceNum":1}

data: {"event":"token","modelId":"gpt-4o-2024-11-20","delta":"Hello"}
data: {"event":"token","modelId":"<second-model>","delta":"Greetings"}
data: {"event":"token","modelId":"gpt-4o-2024-11-20","delta":" there"}
...
data: {"event":"model_complete","modelId":"gpt-4o-2024-11-20","inputTokens":8,"outputTokens":12,"latencyMs":1200}
data: {"event":"model_complete","modelId":"<second-model>","inputTokens":8,"outputTokens":14,"latencyMs":1500}
data: {"event":"all_complete"}
```

**Key checks:**
- [ ] `turn_created` is the first event
- [ ] Token events from BOTH models appear before either `model_complete`
- [ ] The faster model's `model_complete` appears before the slower model's completion
- [ ] `all_complete` is the final event
- [ ] Total elapsed time is close to the slower model's latency (not the sum)

### Step 3 — Verify Persistence

```bash
curl -s "http://localhost:8080/api/v1/chat/sessions/$SESSION" \
  -H "Authorization: Bearer $TOKEN" | jq '.turns[0].responses'
```

**Expected**: Both models' responses with `"status":"complete"` and populated `responseText`.

---

## Scenario 4: Single Model Failure Doesn't Block Others

**Goal**: Verify that a failed model doesn't affect other models in the same parallel call.

### Setup

The API key format is validated at save time (per FR-009), so a completely random string like
`INVALID_KEY` will be rejected before reaching the provider. To exercise the runtime rejection
path, use a key that **passes format validation** but will be **rejected by the provider at
call time** — for example, a correctly-formatted but semantically dead key:

```bash
# Moonshot keys follow the "sk-" prefix pattern; use a structurally valid but fake key:
curl -s -X POST http://localhost:8080/api/v1/user/api-keys \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"providerId":"moonshot","apiKey":"sk-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","label":"Bad key"}'
```

**Expected at save time**: `201 Created` (format passes; liveness is validated lazily).

Enable a Moonshot model with this bad key.

### Submit parallel prompt including the bad model

```bash
curl -s -X POST "http://localhost:8080/api/v1/chat/sessions/$SESSION/turns" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"promptText":"Hello","selectedModelIds":["gpt-4o-2024-11-20","moonshot-v1-8k"]}'
```

**Expected:**
```
data: {"event":"turn_created","turnId":"..."}
data: {"event":"token","modelId":"gpt-4o-2024-11-20","delta":"Hello!"}
...
data: {"event":"model_complete","modelId":"gpt-4o-2024-11-20",...}
data: {"event":"model_error","modelId":"moonshot-v1-8k","error":"API key invalid..."}
data: {"event":"all_complete"}
```

**Key checks:**
- [ ] `model_error` for Moonshot appears without blocking GPT-4o's stream
- [ ] `model_complete` for GPT-4o still contains full response data
- [ ] `all_complete` is sent after both events (complete + error)

---

## Scenario 5: Multi-modal Input (Image)

**Goal**: Verify image routing to capable models and graceful degradation for text-only models.

**Prerequisite**: At least one model with `inputModalities: ["text", "image"]` and one
text-only model both enabled.

```bash
# Encode a small test image
IMAGE_B64=$(base64 -i /path/to/test-image.png | tr -d '\n')

curl -s -X POST "http://localhost:8080/api/v1/chat/sessions/$SESSION/turns" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d "{
    \"promptText\": \"What is in this image?\",
    \"selectedModelIds\": [\"gpt-4o-2024-11-20\", \"deepseek-chat\"],
    \"attachments\": [{\"type\":\"image\",\"data\":\"$IMAGE_B64\",\"mimeType\":\"image/png\"}]
  }"
```

**Expected:**
```
data: {"event":"turn_created","turnId":"..."}
data: {"event":"capability_notice","modelId":"deepseek-chat","notice":"Image input not supported — text only sent"}
data: {"event":"token","modelId":"gpt-4o-2024-11-20","delta":"The image shows..."}
data: {"event":"token","modelId":"deepseek-chat","delta":"I cannot see..."}
...
data: {"event":"all_complete"}
```

**Key checks:**
- [ ] `capability_notice` emitted for DeepSeek before its first token
- [ ] GPT-4o response references the image content
- [ ] Both models complete successfully

---

## UI Validation Checklist

After verifying the API scenarios above, open `http://localhost:3000` and confirm:

- [ ] Registration form at `/register` submits and shows "check your inbox"
- [ ] Login at `/login` redirects to dashboard after successful auth
- [ ] Model settings page shows the platform catalogue with capability badges
- [ ] API key form saves and shows key metadata (not the key value) in the list
- [ ] Chat page shows a model selector with all enabled models
- [ ] After submitting a prompt, response panels for each model begin populating
  simultaneously — not sequentially
- [ ] A slow model's panel continues filling while a fast model shows "complete"
- [ ] A failed model's panel shows an error state without affecting other panels
- [ ] Image attachment button appears only when at least one image-capable model is selected
- [ ] Capability notice appears in a text-only model's panel when an image is attached
