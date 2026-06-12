# Spec 003 — Protocol / Connection / ConfiguredModel Redesign

**Date:** 2026-06-12  
**Branch:** 003-protocol-connection-model  
**Status:** Draft

---

## Problem

The current model-configuration layer has three intertwined issues:

1. **Provider is an overloaded concept.** "OpenAI", "Moonshot", and "Kimi" are all OpenAI-compatible endpoints — identical wire protocol, different base URLs. The current schema forces three separate provider entries, three sync flows, and confuses users who just want to point a key at a URL.

2. **Adding a model ID requires adding a new DB record to a shared table.** `model_definitions` is a global catalogue that mixes CATALOGUE seeds, DISCOVERED rows, and CUSTOM entries. A user wanting to try a new model-id on an existing endpoint must file a new `UserModelConfig` row — and has no way to delete it.

3. **`ProviderModelSyncService` is fragile.** Discovery via live API calls creates phantom models, fails silently when providers change their list endpoint, and leaves stale rows. Its only purpose is populating `model_definitions` with strings the user could type themselves.

---

## Goals

- Introduce **Protocol** as the wire-level abstraction replacing provider as the primary grouping.
- Introduce **Connection** (replaces `provider_api_keys`) — one endpoint URL + one encrypted key.
- Introduce **ConfiguredModel** (replaces `user_model_configs` + per-user `model_definitions`) — a model-id string bound to a connection, fully owned by the user, deletable.
- Delete `ProviderModelSyncService` and its database discovery path.
- Redesign the Settings page to reflect the new three-level structure.

---

## Non-goals

- Migrating existing data. Tables are dropped and recreated (clean rebuild).
- Multi-user / multi-tenant beyond current user scoping.
- Custom protocol implementation — protocols are a closed, code-level enumeration.

---

## Domain Model

### Protocol (static code constants — no DB table)

A protocol is a wire format. There are exactly three, defined in a Kotlin `object`:

| Id | Description | Adapter class |
|----|-------------|---------------|
| `openai-compatible` | OpenAI Chat Completions API, including reasoning via `_additionalProperties` | `OpenAiCompatAdapter` |
| `anthropic` | Anthropic Messages API with native thinking blocks | `AnthropicAdapter` |
| `minimax` | MiniMax proprietary SSE format | `MinimaxAdapter` |

Each protocol carries a **capability baseline** — the set of capabilities guaranteed by the wire format (e.g. `openai-compatible` supports streaming, function calls, vision; `anthropic` supports thinking). Individual models can override the baseline.

Provider names (openai, moonshot, deepseek, kimi, zhipu, anthropic, minimax …) become **display labels** on Connections, not first-class entities. The catalogue still groups suggested models under human-readable provider names, but this is cosmetic.

### Connection (DB table: `connections`)

One connection = one (base URL, API key, protocol) triple.

```
id            UUID PK
user_id       UUID FK → users
protocol      VARCHAR(50) NOT NULL  -- 'openai-compatible' | 'anthropic' | 'minimax'
label         VARCHAR(255)          -- user-chosen, e.g. "My Kimi key"
base_url      VARCHAR(500) NOT NULL -- e.g. "https://api.moonshot.cn/v1"
encrypted_key BYTEA NOT NULL
key_iv        BYTEA NOT NULL
created_at    TIMESTAMPTZ
updated_at    TIMESTAMPTZ
```

A single user can have multiple connections to the same protocol (e.g. a personal OpenAI key and a work Azure-OpenAI endpoint).

### ConfiguredModel (DB table: `configured_models`)

One row = one model available to the user in the chat picker.

```
id                    UUID PK
user_id               UUID FK → users
connection_id         UUID FK → connections ON DELETE CASCADE
model_id              VARCHAR(255) NOT NULL  -- literal string sent in API calls, e.g. "kimi-k2"
display_name          VARCHAR(255) NOT NULL  -- shown in UI
capability_overrides  JSONB NOT NULL DEFAULT '{}'
custom_params         JSONB NOT NULL DEFAULT '{}'
is_enabled            BOOLEAN NOT NULL DEFAULT TRUE
sort_order            INT NOT NULL DEFAULT 0
created_at            TIMESTAMPTZ
updated_at            TIMESTAMPTZ
```

`capability_overrides` is merged on top of the protocol baseline at runtime. Only keys present in the override object are replaced.

Users can **delete** any `ConfiguredModel` row. Deleting a `Connection` cascades to all its `ConfiguredModel` rows.

### Catalogue (static Kotlin object — no DB table)

`ModelCatalogue` replaces the seeded `model_definitions` table. It is a Kotlin `object` that returns a list of catalogue entries:

```kotlin
data class CatalogueEntry(
    val modelId: String,
    val displayName: String,
    val protocol: String,
    val suggestedBaseUrl: String,
    val providerLabel: String,      // cosmetic grouping label, e.g. "Moonshot"
    val capabilityOverrides: Map<String, Any?> = emptyMap(),
    val customParams: Map<String, Any?> = emptyMap(),
)
```

The catalogue is used only to pre-fill the "Add model" form. It is not authoritative — users can type any model-id.

`ProviderModelSyncService` is **deleted**.

---

## Database Migration

The migration is a **clean rebuild** — drop old tables, create new ones. No data migration.

Migration `V017__protocol_connection_model_rebuild.sql`, executed in this order:

1. `ALTER TABLE provider_responses DROP CONSTRAINT IF EXISTS provider_responses_model_id_fkey` — `provider_responses.model_id` holds a FK to `model_definitions(id)`; PostgreSQL refuses to drop `model_definitions` until this FK is removed. The column is kept as a plain `VARCHAR` for historical logging.
2. `ALTER TABLE user_preferences DROP COLUMN IF EXISTS last_selected_model_id` — stored a model-id string which has no equivalent in the new scheme; the "last selected model" feature is re-added in a later migration using `configured_model_id UUID`.
3. `ALTER TABLE chat_sessions DROP COLUMN IF EXISTS selected_model_id` — same stale-string problem; re-added later as `UUID`.
4. Drop tables (in FK-safe order): `user_model_configs`, `model_definitions`, `provider_api_keys`
5. Create `connections`
6. Create `configured_models`

All Flyway migrations prior to V017 remain intact for history.

---

## Backend Changes

### New packages / files

| File | Purpose |
|------|---------|
| `model/Protocol.kt` | Sealed class + object with 3 instances; capability baseline per protocol |
| `model/ModelCatalogue.kt` | Static catalogue entries |
| `connection/Connection.kt` | JPA entity for `connections` |
| `connection/ConnectionRepository.kt` | Spring Data |
| `connection/ConfiguredModel.kt` | JPA entity for `configured_models` |
| `connection/ConfiguredModelRepository.kt` | Spring Data |
| `connection/ConnectionService.kt` | CRUD + key encryption/decryption |
| `connection/ConnectionController.kt` | REST endpoints |

### Deleted files

- `userconfig/ProviderApiKey.kt`, `ProviderApiKeyRepository.kt`, `ProviderModelSyncService.kt`
- `userconfig/UserModelConfig.kt`, `UserModelConfigRepository.kt`
- `model/ModelDefinition.kt`, `ModelDefinitionRepository.kt`, `ModelCatalogueService.kt`
- `model/ModelCatalogueController.kt` (replaced by `ConnectionController`)
- `llm/ProviderDefaults.kt` (replaced by `Protocol` object)

### REST API

All endpoints are under `/api/v1/`.

#### Protocols (public, no auth)

```
GET /protocols
→ { protocols: [{ id, displayName, defaultBaseUrl, capabilities }] }
```

#### Connections (auth required)

```
GET    /connections                    → { connections: [...ConnectionResponse] }
POST   /connections                    ← AddConnectionRequest
PATCH  /connections/{id}               ← PatchConnectionRequest
DELETE /connections/{id}               → 204
```

`AddConnectionRequest`:
```json
{
  "protocol": "openai-compatible",
  "label": "My Kimi key",
  "baseUrl": "https://api.kimi.com/v1",
  "apiKey": "sk-..."
}
```

`PatchConnectionRequest` (all fields optional):
```json
{ "label": "Renamed", "baseUrl": "https://..." }
```

`ConnectionResponse`:
```json
{
  "id": "uuid",
  "protocol": "openai-compatible",
  "label": "My Kimi key",
  "baseUrl": "https://api.kimi.com/v1",
  "maskedKey": "sk-...••••",
  "modelCount": 3,
  "createdAt": "...",
  "updatedAt": "..."
}
```

#### Configured Models (auth required)

```
GET    /configured-models              → { models: [...ConfiguredModelResponse] }
POST   /configured-models              ← AddModelRequest
PATCH  /configured-models/{id}         ← PatchModelRequest
DELETE /configured-models/{id}         → 204
```

`AddModelRequest`:
```json
{
  "connectionId": "uuid",
  "modelId": "kimi-k2",
  "displayName": "Kimi K2",
  "capabilityOverrides": { "maxContextTokens": 131072 },
  "customParams": {},
  "isEnabled": true
}
```

`PatchModelRequest` (all fields optional):
```json
{ "displayName": "Kimi K2 Pro", "isEnabled": false, "capabilityOverrides": {}, "sortOrder": 2 }
```

`ConfiguredModelResponse`:
```json
{
  "id": "uuid",
  "connectionId": "uuid",
  "protocol": "openai-compatible",
  "baseUrl": "https://api.kimi.com/v1",
  "modelId": "kimi-k2",
  "displayName": "Kimi K2",
  "capabilityOverrides": {},
  "customParams": {},
  "isEnabled": true,
  "sortOrder": 0,
  "createdAt": "...",
  "updatedAt": "..."
}
```

`GET /configured-models` accepts an optional query parameter:
- `?enabled=true` — returns only enabled models (used by the chat picker)

#### Catalogue (public, no auth)

```
GET /catalogue
→ { entries: [...CatalogueEntry] }
  optionally: ?protocol=openai-compatible
```

### Orchestrator changes

`ConcurrentLlmOrchestrator` currently uses `AdapterRegistry` (a Spring `@Component` with a `Map<String, LlmAdapter>`) to look up an adapter by `providerId`. After this change:

- **`AdapterRegistry` is deleted.** `OpenAiCompatAdapter`, `AnthropicAdapter`, and `MinimaxAdapter` are directly injected as constructor parameters into the orchestrator.
- **`OpenAiCompatAdapter` no longer receives a `defaultBaseUrl` at construction time** — the base URL always comes from `Connection.baseUrl` at runtime via the `baseUrlOverride` parameter (which already exists on the `stream()` method).
- The adapter lookup switches from `providerId` to `protocol` via a `when` expression in the orchestrator.

`ModelDispatchTarget` fields:
- `configuredModelId: UUID` (for logging / response attribution)
- `modelId: String` (the API model string sent in API calls)
- `protocol: String`
- `baseUrl: String`
- `decryptedApiKey: String`
- `capabilityMatrix: CapabilityMatrix` (merged: protocol baseline + overrides)
- `customParams: Map<String, Any?>`

```kotlin
private fun adapterFor(protocol: String): LlmAdapter = when (protocol) {
    "openai-compatible" -> openAiCompatAdapter
    "anthropic" -> anthropicAdapter
    "minimax" -> minimaxAdapter
    else -> throw IllegalArgumentException("Unknown protocol: $protocol")
}
```

### Chat service changes

`ChatService.submitTurn()` currently accepts a list of `modelId` strings and resolves them through `UserModelConfig` → `ModelDefinition` → `ProviderApiKey`. After this change:

- `SubmitTurnRequest.selectedModelIds: List<String>` → **`selectedConfiguredModelIds: List<UUID>`** (breaking change to the REST contract; frontend chat picker is updated in sync).
- The service resolves each UUID through `ConfiguredModel` → `Connection` to obtain `modelId`, `protocol`, `baseUrl`, and the decrypted key.
- `chat_turns.selected_model_ids TEXT[]` **keeps its current type** — the service stores the resolved model-id strings (e.g. `"kimi-k2"`) into this column, preserving the historical record. No schema change required for this column.

---

## Frontend Changes

### Deleted components

- `ApiKeyForm.tsx`, `ApiKeyBaseUrlEditor.tsx`, `CustomModelForm.tsx`, `ModelCard.tsx`, `ModelConfigControls.tsx`
- `ModelsSettingsPage.tsx` (full rewrite)

### New components

| Component | Purpose |
|-----------|---------|
| `settings/connections/ConnectionCard.tsx` | Card showing one connection: protocol badge, base URL, masked key, model count; edit/delete |
| `settings/connections/AddConnectionDialog.tsx` | Modal: protocol selector → base URL (pre-filled from protocol default) → API key → label. If `GET /protocols` fails, the dialog shows an error banner and the submit button remains disabled. |
| `settings/connections/ModelRow.tsx` | One row inside a connection card: enabled dot, model-id (mono), display name, capability tags, edit / delete |
| `settings/connections/AddModelDialog.tsx` | Modal: model-id input or catalogue picker → display name → capability overrides |
| `settings/connections/EditModelDialog.tsx` | Modal: edit display name, toggle enabled, edit capability overrides |

### Settings page layout

`/settings/models` page:

```
[Add connection]                              ← top-right button

┌─ Connection card ───────────────────────────────────────────────────┐
│ Label: "My OpenAI key"    [openai-compatible]    api.openai.com/v1  │
│ ──────────────────────────────────────────────────────────────────  │
│ ● gpt-4o          GPT-4o        vision context:128k   [Edit][Del]   │
│ ● gpt-4o-mini     GPT-4o Mini   vision               [Edit][Del]   │
│ [Add model]                                                         │
│                                                        [Edit][Del]  │
└─────────────────────────────────────────────────────────────────────┘

┌─ Connection card ───────────────────────────────────────────────────┐
│ Label: "Kimi key"         [openai-compatible]    api.kimi.com/v1    │
│ ──────────────────────────────────────────────────────────────────  │
│ ● kimi-k2         Kimi K2       context:131k          [Edit][Del]   │
│ [Add model]                                                         │
│                                                        [Edit][Del]  │
└─────────────────────────────────────────────────────────────────────┘
```

### Chat model picker

The picker calls `GET /configured-models` (filtered `isEnabled=true`) and groups results by `connectionId` / `protocol`. Model identity used in `submitTurn` changes from `modelId: string` to `configuredModelId: UUID`.

### API client changes

New file: `src/lib/api/connections.ts` (replaces `userConfig.ts` methods for keys/models).

---

## Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Delete connection with active models | Cascade delete — ConfiguredModel rows removed by DB FK |
| Delete model while session in progress | No runtime impact; next message won't include the model |
| Invalid base URL on add connection | Backend `normalizeBaseUrl()` validates http/https; 400 |
| Duplicate model-id on same connection | Allowed — user can have two rows for same model-id if desired |
| Protocol unknown | 400 from backend |

---

## Testing

### Backend

- `ConnectionServiceTest`: add/patch/delete connection, key encryption round-trip
- `ConnectionControllerTest`: REST contract (MockMvc), auth guard
- `ConfiguredModelServiceTest`: add/patch/delete, cascade on connection delete
- `OrchestratorTest`: `adapterFor("openai-compatible")` selects correct adapter

### Frontend

- `ConnectionCard.test.tsx`: renders protocol badge, model count, delete confirm
- `AddConnectionDialog.test.tsx`: base URL pre-fills from protocol default; submits
- `ModelRow.test.tsx`: enabled toggle calls PATCH; delete button calls DELETE

### `capability_overrides` null-removal semantics

When patching a model, a key set to `null` in `capabilityOverrides` **removes** that key from the stored JSONB (reverts to the protocol baseline). A key present with a non-null value replaces the stored value. Keys not mentioned in the patch are left unchanged. The backend applies this via a merge-then-strip approach (`jsonb_strip_nulls` on the merged object).

### API key rotation

`PatchConnectionRequest` allows editing `label` and `baseUrl` but **not the API key**. Rotating a compromised key requires deleting the connection and re-creating it (all `ConfiguredModel` rows cascade-delete and must be re-added). This is intentional for the initial implementation; key rotation can be added later as `PUT /connections/{id}/key`.

---

## Open Questions

None — all design decisions resolved in brainstorming session and spec review.

---

## Implementation Plan

See `specs/003-protocol-connection-model/plan.md` (generated by writing-plans skill after this spec is approved).
