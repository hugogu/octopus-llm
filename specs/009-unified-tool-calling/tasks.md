# Tasks: Unified Tool Calling and Time Awareness

**Input**: Design documents from `/specs/009-unified-tool-calling/`
**Prerequisites**: `plan.md`, `spec.md`, `data-model.md`, `contracts/chat-tool-events.md`, `research.md`

**Tests**: A focused set of backend integration tests is included because the tool loop and deduplication are core correctness requirements, even though the specification did not explicitly request TDD.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify existing project setup and prepare the feature branch.

- [ ] T001 [P] Verify backend Gradle and frontend Node.js build environments are ready on branch `009-unified-tool-calling`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before any user story can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T002 Create Flyway migration `V037__tool_invocations.sql` in `backend/src/main/resources/db/migration/`
- [ ] T003 [P] Create JPA entities `ToolInvocation` and `ProviderResponseToolInvocation` in `backend/src/main/kotlin/com/octopusllm/tool/`
- [ ] T004 [P] Create `Tool.kt`, `ToolDefinition.kt`, and `ToolResult.kt` interfaces in `backend/src/main/kotlin/com/octopusllm/tool/`
- [ ] T005 [P] Create `UnifiedInteractionEvent.kt` sealed class in `backend/src/main/kotlin/com/octopusllm/tool/`
- [ ] T006 Create `ToolRegistry.kt` and `ToolExecutor.kt` with timeout, retry, and deduplication in `backend/src/main/kotlin/com/octopusllm/tool/`
- [ ] T007 [P] Extend `LlmRequest.kt` in `backend/src/main/kotlin/com/octopusllm/llm/` with `systemPrompt` and `tools` fields
- [ ] T008 [P] Extend `LlmStreamEvent.kt` in `backend/src/main/kotlin/com/octopusllm/llm/` with `ToolCall`, `ToolResult`, and `ToolStatus` variants
- [ ] T009 Update `CapabilityMatrix` usage to gate tool availability per model in `backend/src/main/kotlin/com/octopusllm/llm/`

**Checkpoint**: Foundation ready — database schema, tool core, and LLM event/request models are in place. User story implementation can now begin.

---

## Phase 3: User Story 1 - Time-Aware Answers (Priority: P1) 🎯 MVP

**Goal**: The system always injects the current date/time into the conversation context so models can answer time-sensitive questions like "今天 A 股怎么样" without external tools.

**Independent Test**: Send a prompt containing "today" and verify the response references the actual current date and today's context.

### Tests for User Story 1

- [ ] T010 [P] [US1] Add backend integration test for time-aware answer in `backend/src/test/kotlin/com/octopusllm/chat/ChatServiceTimeAwarenessTest.kt`

### Implementation for User Story 1

- [ ] T011 [US1] Update `ChatService.kt` to always inject `TimeContext` into `LlmRequest.systemPrompt`
- [ ] T012 [P] [US1] Update `OpenAiCompatAdapter.kt` to emit the system prompt when `supports_system_prompt` is true
- [ ] T013 [P] [US1] Update `AnthropicAdapter.kt` to emit the system prompt when `supports_system_prompt` is true
- [ ] T014 [US1] Update `MiniMaxAdapter.kt` to emit the system prompt or fallback when `supports_system_prompt` is true

**Checkpoint**: User Story 1 should be fully functional and testable independently.

---

## Phase 4: User Story 2 - External Tool Use (Priority: P2)

**Goal**: The system invokes built-in tools (current time, web search, stock quote, weather, news) to answer questions requiring real-time external data, with timeout/retry and graceful failure handling.

**Independent Test**: Send a prompt that requires live data (e.g., stock price) and confirm the tool is invoked and the returned value appears in the final answer.

### Tests for User Story 2

- [ ] T015 [P] [US2] Add backend integration test for single tool use in `backend/src/test/kotlin/com/octopusllm/tool/ToolUseIntegrationTest.kt`
- [ ] T016 [P] [US2] Add backend integration test for multi-step tool use in `backend/src/test/kotlin/com/octopusllm/tool/MultiStepToolIntegrationTest.kt`
- [ ] T017 [P] [US2] Add backend integration test for tool failure handling in `backend/src/test/kotlin/com/octopusllm/tool/ToolFailureIntegrationTest.kt`

### Implementation for User Story 2

- [ ] T018 [P] [US2] Implement `CurrentTimeTool` in `backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt`
- [ ] T019 [P] [US2] Implement `WebSearchTool` in `backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt`
- [ ] T020 [P] [US2] Implement `StockQuoteTool` in `backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt`
- [ ] T021 [P] [US2] Implement `WeatherTool` in `backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt`
- [ ] T022 [P] [US2] Implement `NewsTool` in `backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt`
- [ ] T023 [US2] Wire built-in tools into `ToolRegistry` at application startup in `backend/src/main/kotlin/com/octopusllm/tool/ToolRegistry.kt`
- [ ] T024 [P] [US2] Update `OpenAiCompatAdapter.kt` to translate provider tool calls/results to/from unified events
- [ ] T025 [P] [US2] Update `AnthropicAdapter.kt` to translate provider tool calls/results to/from unified events
- [ ] T026 [US2] Update `MiniMaxAdapter.kt` to capability-gate tool availability without tool translation if unsupported
- [ ] T027 [US2] Implement tool execution loop in `ChatService.kt` that feeds tool results back to model streams
- [ ] T028 [US2] Add persistence of tool invocations and join records via `ToolInvocationService.kt` in `backend/src/main/kotlin/com/octopusllm/tool/`
- [ ] T029 [P] [US2] Add frontend SSE parsing for `tool_call`, `tool_result`, and `tool_status` events in `frontend/src/lib/api/chatV2.ts`
- [ ] T030 [P] [US2] Create `ToolStatusIndicator.tsx` in `frontend/src/components/chat/`
- [ ] T031 [US2] Update `MessageThread.tsx` to render tool status chips

**Checkpoint**: User Stories 1 AND 2 should both work independently.

---

## Phase 5: User Story 3 - Consistent Cross-Model Behavior (Priority: P3)

**Goal**: When multiple models are selected in a single Quest, tool invocations are deduplicated and shared across models, while the UI presents consistent tool-status indicators per model and gracefully degrades for unsupported models.

**Independent Test**: Select two models for the same tool-dependent prompt and verify that identical tool calls share one execution result, and both responses show the same status affordances.

### Tests for User Story 3

- [ ] T032 [P] [US3] Add backend integration test for cross-model tool deduplication in `backend/src/test/kotlin/com/octopusllm/tool/CrossModelToolDeduplicationTest.kt`
- [ ] T033 [P] [US3] Add backend integration test for graceful degradation of unsupported models in `backend/src/test/kotlin/com/octopusllm/tool/UnsupportedModelToolTest.kt`

### Implementation for User Story 3

- [ ] T034 [US3] Implement per-turn deduplication of identical tool invocations in `backend/src/main/kotlin/com/octopusllm/tool/ToolExecutor.kt`
- [ ] T035 [US3] Update `ConcurrentLlmOrchestrator.kt` to distribute shared tool results to all requesting models without blocking other streams
- [ ] T036 [US3] Update `ResponseGroup.tsx` to show per-model tool status and results
- [ ] T037 [US3] Add capability-based graceful degradation for models that do not support tool calling in `backend/src/main/kotlin/com/octopusllm/llm/ConcurrentLlmOrchestrator.kt`

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories.

- [ ] T038 [P] Add contract tests for SSE tool events in `backend/src/test/kotlin/com/octopusllm/contract/ChatToolEventsContractTest.kt`
- [ ] T039 [P] Add structured logging for tool invocation latency and status in `backend/src/main/kotlin/com/octopusllm/tool/ToolExecutor.kt`
- [ ] T040 [P] Run `quickstart.md` validation scenarios end-to-end
- [ ] T041 Run backend build (`cd backend && ./gradlew build`) and frontend build/lint/tests (`cd frontend && npm run build && npm run lint && npm run test:run`)
- [ ] T042 Update feature documentation and README references

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion. Blocks all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational phase completion.
  - User stories can then proceed in parallel (if staffed).
  - Or sequentially in priority order (P1 → P2 → P3).
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational phase — no dependencies on other stories.
- **User Story 2 (P2)**: Can start after Foundational phase — depends on the unified event/request model from Phase 2; may integrate with US1 but should be independently testable.
- **User Story 3 (P3)**: Can start after Foundational phase and after basic tool execution works (US2) — depends on `ToolExecutor` and adapter translations.

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation.
- Models before services.
- Services before endpoints/UI integration.
- Core implementation before cross-model integration.
- Story complete before moving to next priority.

### Parallel Opportunities

- All Setup tasks marked `[P]` can run in parallel.
- Foundational tasks marked `[P]` can run in parallel where they touch different files (e.g., entities, event classes, request classes, adapter capability gates).
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows).
- All tests for a user story marked `[P]` can run in parallel.
- Built-in tool implementations (`T018`–`T022`) are fully parallel.
- Adapter translations (`T024`–`T026`) are parallel across different adapter files.
- Frontend event parsing (`T029`) and `ToolStatusIndicator` (`T030`) are parallel.

---

## Parallel Example: User Story 2

```bash
# Launch all built-in tool implementations together:
Task: T018 [P] [US2] Implement CurrentTimeTool in backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt
Task: T019 [P] [US2] Implement WebSearchTool in backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt
Task: T020 [P] [US2] Implement StockQuoteTool in backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt
Task: T021 [P] [US2] Implement WeatherTool in backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt
Task: T022 [P] [US2] Implement NewsTool in backend/src/main/kotlin/com/octopusllm/tool/BuiltInTools.kt

# Launch adapter translations together:
Task: T024 [P] [US2] Update OpenAiCompatAdapter.kt
Task: T025 [P] [US2] Update AnthropicAdapter.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (critical — blocks all stories).
3. Complete Phase 3: User Story 1 (time-aware answers).
4. **STOP and VALIDATE**: Test User Story 1 independently with the quickstart scenario.
5. Deploy/demo if ready.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready.
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!).
3. Add User Story 2 → Test independently → Deploy/Demo.
4. Add User Story 3 → Test independently → Deploy/Demo.
5. Each story adds value without breaking previous stories.

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together.
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2 (built-in tools + adapter translation)
   - Developer C: User Story 3 (deduplication + frontend per-model status)
3. Stories complete and integrate independently.

---

## Notes

- `[P]` tasks = different files, no dependencies.
- `[Story]` label maps task to specific user story for traceability.
- Each user story should be independently completable and testable.
- Verify tests fail before implementing.
- Commit after each task or logical group.
- Stop at any checkpoint to validate a story independently.
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence.
