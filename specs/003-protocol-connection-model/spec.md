# Feature Specification: Protocol, Connection, and Configured Model

**Feature Branch**: `003-protocol-connection-model`  
**Created**: 2026-06-12  
**Status**: Ready for planning  
**Input**: Replace provider-centric model configuration with protocol-based connections and user-owned configured models.

## User Scenarios & Testing

### User Story 1 - Configure an endpoint and models (Priority: P1)

An authenticated user creates a connection by choosing a supported protocol, entering an endpoint URL and API key, then adds one or more model IDs to that connection. The user can edit or delete both connections and models without requiring a shared catalogue update.

**Why this priority**: A usable connection and at least one configured model are prerequisites for chat.

**Independent Test**: Create a connection, add an arbitrary model ID, reload Settings, and verify both records remain visible while the API key is never returned.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they create a connection with a valid public HTTPS endpoint and key, **Then** the connection is stored with the key encrypted and the response contains no key characters.
2. **Given** a connection owned by the user, **When** they add an arbitrary model ID, **Then** it appears under that connection and can be enabled, edited, reordered, or deleted.
3. **Given** two connections, **When** the same model ID is added to both, **Then** both configured models coexist with distinct UUID identities.
4. **Given** an endpoint resolving to loopback, link-local, multicast, or private network space, **When** a connection is created or updated, **Then** the API rejects it.
5. **Given** a configured model or connection owned by another user, **When** a user attempts to read or mutate it, **Then** the API returns a non-disclosing not-found response.

---

### User Story 2 - Chat using configured model identities (Priority: P1)

An authenticated user selects enabled configured models and submits a prompt. Calls are dispatched concurrently through the adapter registered for each connection protocol, and streamed events are attributed by configured-model UUID.

**Why this priority**: This preserves the core comparison workflow while allowing duplicate model IDs on different endpoints.

**Independent Test**: Configure the same model ID on two mock connections, submit one turn selecting both UUIDs, and verify two independent streams and response records.

**Acceptance Scenarios**:

1. **Given** multiple enabled configured models, **When** a turn is submitted, **Then** all provider calls start concurrently and partial results stream without waiting for the slowest provider.
2. **Given** duplicate model IDs on different connections, **When** both are selected, **Then** SSE events and persisted responses remain distinct by configured-model UUID.
3. **Given** a configured model is deleted after a response is saved, **When** the session is loaded, **Then** its immutable model ID, display name, protocol, and connection label snapshots remain available.
4. **Given** a disabled, missing, or foreign configured-model UUID, **When** it is submitted, **Then** no provider call is made and the request is rejected.
5. **Given** an adapter is added for a new protocol, **When** it is registered, **Then** the orchestrator requires no source-code change.

---

### User Story 3 - Use catalogue suggestions without discovery (Priority: P2)

A user can browse paginated, code-maintained catalogue suggestions to pre-fill a model form, while retaining the ability to type any model ID.

**Why this priority**: Suggestions improve setup speed but are not required for custom endpoints.

**Independent Test**: Filter the catalogue by protocol, select an entry to pre-fill a model form, and separately add a model ID that is not in the catalogue.

**Acceptance Scenarios**:

1. **Given** a selected protocol, **When** catalogue entries are requested, **Then** a paginated list of matching suggestions is returned.
2. **Given** catalogue service failure, **When** the Add Model dialog opens, **Then** manual model ID entry remains available.
3. **Given** a custom model ID, **When** it is submitted, **Then** no live provider model-list request is required.

---

### User Story 4 - Preserve existing user configuration (Priority: P1)

Existing provider keys and model configurations are migrated to connections and configured models during deployment.

**Why this priority**: A production migration must not destroy paid credentials or disable existing chat setups.

**Independent Test**: Seed the V016 schema with representative provider keys and model configs, run V017, and verify equivalent connections and configured models exist with unchanged encrypted key bytes.

**Acceptance Scenarios**:

1. **Given** an existing provider key, **When** V017 runs, **Then** a connection is created with the mapped protocol and effective base URL.
2. **Given** an existing model configuration with a key, **When** V017 runs, **Then** a configured model is created and linked to the migrated connection.
3. **Given** historical chat turns and responses, **When** V017 runs, **Then** their prompt and response data remain readable and immutable.
4. **Given** migration validation failure, **When** deployment runs, **Then** the transaction rolls back without dropping source tables.

### Edge Cases

- A hostname resolves to both public and private addresses, changes after validation, or redirects to a disallowed address.
- A connection URL contains credentials, fragments, non-default ports, an unsupported scheme, or an invalid hostname.
- A user submits duplicate configured-model UUIDs in one turn.
- A configured model or connection is deleted while a provider stream is already running.
- A catalogue capability value is absent or differs from the protocol's conservative defaults.
- An existing model config has no API key; it is skipped with a migration audit count rather than producing an unusable configured model.
- Pagination parameters are invalid or exceed the maximum page size.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST define supported protocols in code and expose their identifiers and conservative capability defaults.
- **FR-002**: Each protocol adapter MUST register through a protocol-keyed adapter registry; orchestration code MUST NOT enumerate concrete adapters.
- **FR-003**: The system MUST store each connection with an owner, protocol, normalized endpoint URL, encrypted API key, optional label, and timestamps.
- **FR-004**: Connection API responses MUST NOT contain API key plaintext, prefixes, suffixes, ciphertext, IVs, or reversible fingerprints.
- **FR-005**: Connection creation and updates MUST reject unsafe outbound destinations, including loopback, link-local, private, multicast, unspecified, and cloud metadata addresses after DNS resolution and on redirects.
- **FR-006**: Production connections MUST use HTTPS. HTTP MAY be allowed only for explicitly enabled local development hosts.
- **FR-007**: Users MUST be able to rotate a connection key without deleting its configured models.
- **FR-008**: The system MUST store configured models as user-owned records bound to one connection, with UUID identity, model ID, display name, capability overrides, custom parameters, enabled state, order, and timestamps.
- **FR-009**: The database MUST enforce that a configured model and its connection have the same owner.
- **FR-010**: Users MUST be able to create, edit, reorder, enable, disable, and delete configured models, including editing custom parameters.
- **FR-011**: Duplicate model IDs MUST be allowed across and within connections because UUID is the operational identity.
- **FR-012**: The catalogue MUST be static application data used only for form suggestions; arbitrary model IDs MUST remain valid.
- **FR-013**: The configuration workflow MUST NOT depend on live provider model discovery; an optional per-connection "load models" lookup MAY pre-fill suggestions, and arbitrary manual model IDs MUST remain valid when the lookup fails or is unsupported.
- **FR-014**: A submitted chat turn MUST accept configured-model UUIDs and resolve only enabled records owned by the authenticated user.
- **FR-015**: Concurrent stream events MUST carry `configuredModelId`; `modelId` MUST retain its literal provider model meaning.
- **FR-016**: Persisted provider responses MUST retain immutable snapshots of configured-model UUID, model ID, display name, protocol, and connection label.
- **FR-017**: The system MUST permit two responses with the same model ID in one turn when their configured-model UUIDs differ.
- **FR-018**: Deleting configuration MUST NOT remove or mutate historical turns or response snapshots.
- **FR-019**: Breaking connection, configured-model, chat request, and SSE contracts MUST be exposed under `/api/v2`.
- **FR-020**: Deployment MUST coordinate the removal of affected v1 model, configuration, and chat endpoints as a major-version cutover; unaffected v1 authentication endpoints MAY remain available.
- **FR-021**: Every collection endpoint MUST support bounded pagination and stable ordering.
- **FR-022**: Personal connection and configured-model endpoints MUST require authentication and enforce owner scoping.
- **FR-023**: V017 MUST migrate existing provider keys and usable model configurations before old tables are removed.
- **FR-024**: V017 MUST preserve historical chat data and execute transactionally with validation checks before destructive cleanup.
- **FR-025**: Capability defaults MUST be conservative; model-specific features such as vision, function calling, reasoning, and context length MUST be enabled by catalogue or user overrides.
- **FR-026**: New and changed APIs MUST use the standard error schema `{code, message, details}`.
- **FR-027**: Logs and analytics MUST identify protocol, model ID, configured-model UUID, latency, token counts, error code, and anonymized user without logging keys or sensitive custom parameters.
- **FR-028**: The Settings UI MUST provide one primary entry point for connection/model management and MUST keep manual model entry usable when catalogue loading fails.

### Key Entities

- **Protocol Definition**: Static protocol metadata and conservative capabilities; associated with one adapter registration.
- **Connection**: User-owned endpoint and encrypted credential for one protocol.
- **Configured Model**: UUID-addressed model configuration bound to one connection.
- **Catalogue Entry**: Optional static suggestion for creating a configured model.
- **Provider Response Snapshot**: Immutable response attribution independent of mutable or deleted configuration.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A user can create a connection and arbitrary configured model without a provider discovery request.
- **SC-002**: API and integration tests confirm that no connection response contains any substring from the submitted API key.
- **SC-003**: Security tests reject all documented private and metadata destination classes, including redirect targets.
- **SC-004**: An integration test streams and persists two responses for the same model ID on two connections without key collisions.
- **SC-005**: A V016-to-V017 migration test preserves 100% of provider keys with usable model configs and 100% of historical chat turns/responses.
- **SC-006**: All list endpoints enforce a maximum page size of 100 and return deterministic order.
- **SC-007**: Backend build, migration tests, frontend type-check, frontend tests, and Docker build all pass.
- **SC-008**: Adding a test protocol adapter requires no modification to `ConcurrentLlmOrchestrator`.
