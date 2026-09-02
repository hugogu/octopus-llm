# Quickstart: Anonymous Chat and Model Access Management

This guide is for local verification of feature 010 after implementation. It assumes PostgreSQL 16 is available and the existing backend/frontend setup from the repository README is configured.

## 1. Prepare the environment

Set the normal backend secrets plus conservative anonymous defaults. Names below are the planned configuration surface; implementation should bind them under `app.anonymous` and document deployment-specific values.

```text
JWT_SECRET=<development-secret>
ENCRYPTION_MASTER_KEY=<development-key>
ANONYMOUS_VISITOR_HMAC_SECRET=<development-anonymous-hmac-secret>
ANONYMOUS_RATE_LIMIT=<small-fixed-window-limit>
ANONYMOUS_CONCURRENCY_LIMIT=<small-active-stream-limit>
ANONYMOUS_PROMPT_MAX_BYTES=<bounded-prompt-size>
ANONYMOUS_HISTORY_MAX_BYTES=<bounded-history-size>
ANONYMOUS_MODEL_MAX_COUNT=<bounded-model-count>
ANONYMOUS_EXECUTION_TIMEOUT_SECONDS=<bounded-provider-timeout>
```

Never use a production key in local fixtures or commit these values. The HMAC secret is used to derive an anonymous client bucket and must not be returned in an API response.

## 2. Apply schema and seed a public model

Start the existing PostgreSQL service, run the normal Flyway-backed backend startup, and verify migration `V041__anonymous_model_access.sql` applies cleanly. Do not run the migration SQL manually against the database.

Using an administrator account, create or identify at least three built-in configured models, then set combinations such as:

| Model | `is_enabled` | `is_anonymous_allowed` | Expected public result |
|---|---:|---:|---|
| A | true | true | Listed and selectable anonymously |
| B | true | false | Absent anonymously, available to eligible authenticated users |
| C | false | true | Absent anonymously because normal display is disabled |

Also keep one user-owned/BYOK model enabled; it must never appear in the public catalogue or admin built-in model-management list.

## 3. Verify public chat

From a fresh browser context with no authentication:

1. Open `/chat` and confirm there is no login redirect.
2. Inspect `GET /api/v2/anonymous/models`; confirm only model A and safe metadata are returned.
3. Submit a text prompt with model A and confirm normalized SSE events render the response.
4. Refresh and revisit the conversation; confirm prompt and response remain in the versioned local-storage envelope.
5. Confirm there is no share button, share URL, account history request, attachment control, or tool execution in anonymous mode.
6. Revoke or hide model A in another admin tab, refresh the public catalogue, and confirm a new turn is rejected while the old local response remains readable.

Check the browser network panel and backend logs for absence of API keys, base URLs, raw client IPs, prompts in structured metrics, and private model metadata.

## 4. Verify registration synchronization

Create at least two local conversations, including one complete response and one unsupported/failed state. Register in the same browser and allow the existing registration flow to log in. Confirm:

- synchronization starts only after login succeeds;
- supported conversations appear once in authenticated history;
- the source local copy is removed only after `IMPORTED`/`ALREADY_IMPORTED` with a session ID;
- failed/skipped conversations remain local and expose retry feedback;
- retrying the same batch creates no duplicate session or turn;
- synchronized conversations now follow the existing authenticated sharing rules.

Also test a registration where synchronization is temporarily unavailable. The user must still reach authenticated chat, and local data must remain recoverable.

## 5. Verify bulk administration

Seed at least 100 built-in configured models across two connections. In `/admin/models`:

1. Search/filter across connection boundaries and confirm the response uses `{items,page,size,totalElements,totalPages}` with `size <= 100`.
2. Select all matching models across multiple pages and confirm the preview count and frozen scope.
3. Execute allow, revoke, show, and hide; verify each action changes only its own state.
4. Delete a mixed selection with one concurrently deleted target; verify `CHANGED`, `ALREADY_SATISFIED`, `ALREADY_DELETED`, and `FAILED` outcomes are visible and retryable.
5. Confirm historical provider-response snapshots remain readable after model configuration deletion.
6. Repeat an execute request with the same `Idempotency-Key`; confirm no duplicate mutation or audit event.

## 6. Automated checks

Run the repository's standard checks from the repository root after implementation:

```bash
cd /Users/gqq/OpenSource/octopus-llm/backend && ./gradlew test && ./gradlew build
cd /Users/gqq/OpenSource/octopus-llm/frontend && npm run test:run && npx tsc --noEmit && npm run lint && npm run build
cd /Users/gqq/OpenSource/octopus-llm/frontend && npx playwright test
```

For browser tests, start the frontend and backend using the repository's supported development commands and set the Playwright base URL to the published frontend origin. Run the same flows against both `http://localhost` and `http://127.0.0.1` when local CORS/origin behavior is part of the test. If Docker Compose is available, repeat the focused browser smoke tests against the published frontend origin after `docker compose up --build`.
