# Design: Protocol, Connection, and Configured Model

**Date:** 2026-06-12
**Branch:** `003-protocol-connection-model`
**Status:** Approved for task generation
**Canonical requirements:** [spec.md](./spec.md)
**Implementation architecture:** [plan.md](./plan.md)

## Problem

The current design conflates provider branding, wire protocol, credentials, and model identity:

- OpenAI-compatible endpoints require duplicated provider-specific adapter configuration.
- User-entered model IDs become global `model_definitions` rows.
- Live model discovery creates stale or unavailable catalogue entries.
- A model ID string cannot distinguish the same model used through two connections.

## Domain Model

### Protocol Definition

A static code definition identifies a wire protocol and its conservative defaults. Supported initial IDs are:

- `openai-compatible`
- `anthropic`
- `minimax`

The protocol definition does not claim model-specific features. Vision, reasoning, function calling, video, and context length are supplied by catalogue or user overrides.

Each `LlmAdapter` declares `protocolId`. `ProtocolAdapterRegistry` receives adapters through Spring injection and rejects duplicate registrations. The orchestrator resolves adapters only through this registry.

### Connection

A connection is a user-owned protocol endpoint and credential:

```text
id             UUID primary key
user_id        UUID not null
protocol       varchar(50) not null
label          varchar(255)
base_url       varchar(500) not null
encrypted_key  bytea not null
key_iv         bytea not null
created_at     timestamptz not null
updated_at     timestamptz not null
unique(user_id, id)
```

Responses expose `hasKey` but no key-derived text. Key rotation updates encrypted key material without replacing the connection.

### Configured Model

A configured model is the operational model identity:

```text
id                    UUID primary key
user_id               UUID not null
connection_id         UUID not null
model_id              varchar(255) not null
display_name          varchar(255) not null
capability_overrides  jsonb not null default '{}'
custom_params         jsonb not null default '{}'
is_enabled            boolean not null default true
sort_order            integer not null default 0
created_at            timestamptz not null
updated_at            timestamptz not null
foreign key (user_id, connection_id) references connections(user_id, id)
```

Duplicate `model_id` values are allowed. UUID identifies the selected configuration and stream; `model_id` remains the literal provider request value.

### Immutable Provider Response Snapshot

Historical responses retain:

```text
configured_model_id       UUID not null
model_id                  varchar(255) not null
model_display_name        varchar(255) not null
protocol                  varchar(50) not null
connection_label          varchar(255)
unique(turn_id, configured_model_id)
```

`configured_model_id` is intentionally not a cascading FK. Configuration deletion cannot modify historical attribution.

## API Design

Breaking resources use `/api/v2`.

### Public

- `GET /api/v2/protocols`
- `GET /api/v2/catalogue`

### Authenticated

- `GET|POST /api/v2/connections`
- `PATCH|DELETE /api/v2/connections/{id}`
- `PUT /api/v2/connections/{id}/key`
- `GET|POST /api/v2/configured-models`
- `PATCH|DELETE /api/v2/configured-models/{id}`
- `/api/v2/chat/sessions/**`

All collection endpoints use `{items, page, size, totalElements, totalPages}`, default size 25, maximum size 100, and deterministic ordering.

Affected v1 model, configuration, and chat endpoints are removed in a coordinated backend/frontend major-version cutover. Unaffected v1 authentication endpoints may remain available.

## Endpoint Security

Connection URLs are normalized and validated:

- HTTPS is required in production.
- Userinfo and fragments are rejected.
- All DNS answers must be public unicast addresses.
- Private, loopback, link-local, multicast, unspecified, carrier-grade NAT, benchmarking, documentation, and metadata destinations are rejected.
- Redirects are disabled unless the adapter explicitly requires them; every redirect is revalidated.
- Local HTTP requires an explicit development-only property and a loopback hostname.

The endpoint is revalidated immediately before dispatch to reduce DNS rebinding risk. Production deployment should also apply network egress policy.

Owner-scoped repository methods return 404 for missing and foreign resources. Authentication is required before key decryption or provider dispatch.

## Capability Merge

Known override keys are type-validated and merged onto protocol defaults. A patch value of `null` removes a stored key. Omitted keys remain unchanged. Unknown keys are preserved for forward compatibility but do not influence routing unless an adapter explicitly consumes them.

The same merge semantics apply independently to `customParams`.

## Migration

`V017__protocol_connection_model_migration.sql` runs transactionally:

1. Create `connections`, `configured_models`, and migration audit structures.
2. Copy each provider key to a connection, preserving encrypted key and IV bytes.
3. Map provider IDs to protocols and calculate effective base URLs.
4. Copy usable user model configs to configured models.
5. Add and backfill response snapshot columns.
6. Add `selected_configured_model_ids` to chat turns and map rows where possible.
7. Validate migrated counts and ownership consistency.
8. Replace response uniqueness with `(turn_id, configured_model_id)`.
9. Remove obsolete FKs/tables only after validation succeeds.

Historical prompts and response payloads are never deleted or updated beyond adding attribution snapshots.

## UI

`/settings/models` is the single management entry point:

- Connection cards show label, protocol, endpoint, `hasKey`, and models.
- Add/Edit Connection supports endpoint validation and key rotation.
- Add Model uses catalogue suggestions when available and always supports manual entry.
- Each connection card offers an optional "Load models" action (`GET /api/v2/connections/{id}/models`) that bulk-adds endpoint models not yet configured; failures are non-blocking and never disable manual entry.
- Edit Model supports display name, enabled state, order, capabilities, and custom parameters.
- Destructive actions require confirmation.
- A single Back to Chat action returns to the chat page.

The chat picker uses configured-model UUIDs, groups models by connection, and remains compact when many models are configured.

## Testing

Required automated coverage:

- V016-to-V017 migration and rollback.
- API v2 pagination and standard error envelopes.
- API key non-disclosure and key rotation.
- Owner isolation and composite ownership constraint.
- SSRF address classes, DNS results, and redirect targets.
- Capability/custom-parameter merge semantics.
- Registry extensibility without orchestrator edits.
- Duplicate model IDs producing separate concurrent streams and persisted responses.
- Historical session rendering after configuration deletion.
- Settings and chat user journeys.

## Resolved Decisions

- No clean rebuild or destructive configuration loss.
- No adapter enumeration in the orchestrator.
- No key fragments in API responses.
- No configuration dependency on live provider model discovery; per-connection "load models" is an optional suggestion source only.
- No model-specific capability claims at protocol level.
- No breaking contract changes under `/api/v1`.
