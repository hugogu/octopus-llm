# Quickstart Validation

## Prerequisites

- Node.js 24
- Java 21
- Docker with PostgreSQL 16
- A test API key or a mock OpenAI-compatible endpoint

## 1. Migration preservation

1. Start PostgreSQL and migrate only through V016.
2. Insert one provider key, one usable model config, one model config without a key, and one historical completed response.
3. Run V017.
4. Verify one connection and one configured model were migrated.
5. Verify the skipped row is recorded in migration audit.
6. Verify historical response count and payload are unchanged and snapshot columns are populated.

## 2. Connection security and key privacy

1. Register and authenticate a test user.
2. Attempt connections to `127.0.0.1`, `169.254.169.254`, RFC1918 IPv4, unique-local IPv6, and a redirect to those destinations.
3. Verify each request returns 400.
4. Create a public HTTPS connection.
5. Verify create/list/update responses contain `hasKey: true` and no substring from the key.
6. Rotate the key and verify configured models remain.

## 3. Duplicate model IDs

1. Create two connections.
2. Add `same-model-id` to each.
3. Submit one v2 chat turn selecting both configured-model UUIDs.
4. Verify two concurrent stream states and two persisted responses with distinct configured-model UUIDs.

## 4. Historical snapshot

1. Complete a chat turn.
2. Delete one configured model and its connection.
3. Reload the session.
4. Verify model ID, display name, protocol, connection label, response text, reasoning, tokens, and latency still render.

## 5. Catalogue fallback and pagination

1. Request catalogue and configured-model pages with `size=2`.
2. Verify deterministic paging metadata and no duplicates across pages.
3. Simulate catalogue failure in the frontend.
4. Verify Add Model still accepts manual model ID and parameters.

## 6. Quality gates

```bash
cd backend && ./gradlew build
cd frontend && npx tsc --noEmit
cd frontend && npx vitest run
docker compose build
```
