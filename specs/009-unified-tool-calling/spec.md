# Feature Specification: Unified Tool Calling and Time Awareness

**Feature Branch**: `009-unified-tool-calling`  
**Created**: 2026-07-10  
**Status**: Draft  
**Input**: User description: "你基于以上的设计来出本次的的spec吧。注意不同模型之间要有统一的交互层。这样应用层可以统一处理，适配层只需要把各个LLM的特化行为做统一化处理就可以了。"

## Clarifications

### Session 2026-07-10

- **Q**: When a user runs the same query against multiple models and two or more models request the same tool with the same arguments, how should the system execute those tool calls?  
  **A**: The unified layer deduplicates identical tool + argument combinations within a turn and executes them once, sharing the result across all requesting models. Each model still receives the result through its own adapter mapping so that output behavior is normalized while external API cost and latency are minimized.

- **Q**: What should the default timeout and retry behavior be for tool invocations?  
  **A**: Tool calls use a 15-second timeout and one retry with short exponential backoff. If the retry also fails, the failure is surfaced to the model and user as defined in FR-007.

- **Q**: How should the system handle user-provided data that may contain sensitive information (PII, credentials, or business secrets) when it is passed to external tools?  
  **A**: Tool arguments are passed to external tools as-is. The system does not apply automated redaction or masking. Users are responsible for not including sensitive information in prompts that trigger tool calls, and administrators control which tools are available to users.

- **Q**: Should the system always inject current time context, or only when it detects a time-sensitive question?  
  **A**: The system always injects the current date/time into the conversation context. This keeps the behavior simple, avoids missing implicit time references, and ensures every model has the same temporal baseline.

- **Q**: Which built-in external tools should be included in the first release?  
  **A**: The first release includes built-in tools for current time, web search, stock quote, weather, and news retrieval.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Time-Aware Answers (Priority: P1)

A user asks a question that depends on the current date or time, such as "今天 A 股怎么样" or "下周有什么安排". The system knows the current date and time and can interpret the user's intent relative to "now" without requiring external tools.

**Why this priority**: Many everyday questions are implicitly time-sensitive. Without knowing "today", the model cannot give meaningful answers to stock, weather, schedule, or news questions. This is the smallest slice that improves the core chat experience independently.

**Independent Test**: Can be fully tested by sending a prompt that references "today" and verifying that the response reflects the actual current date.

**Acceptance Scenarios**:

1. **Given** the user has started a new Quest, **When** they ask "今天 A 股怎么样", **Then** the model receives the current date/time and answers using today's market context.
2. **Given** the user asks "下周上海会下雨吗", **When** the model interprets "下周", **Then** it resolves the relative time based on the current system date.
3. **Given** the user asks a non-time-sensitive question, **When** the system injects time context, **Then** the answer is still accurate and not confused by the extra context.

---

### User Story 2 - External Tool Use (Priority: P2)

A user asks a question that requires information not present in the model's training data, such as real-time stock prices, weather, or current news. The system invokes an appropriate tool, retrieves the latest data, and returns a final answer that incorporates the tool result.

**Why this priority**: Real-time external retrieval extends the system from a pure conversational comparison tool to a useful assistant. It depends on the unified interaction layer but can be delivered as a separate slice after time-awareness is in place.

**Independent Test**: Can be fully tested by sending a prompt that requires live data and confirming that the response cites the tool's returned value.

**Acceptance Scenarios**:

1. **Given** the user asks "当前贵州茅台的股价是多少", **When** a stock-price tool is available, **Then** the system invokes the tool and returns the latest price in the answer.
2. **Given** the tool provider is temporarily unavailable, **When** the tool fails, **Then** the system informs the user that the data is unavailable instead of hallucinating a value.
3. **Given** the user asks a multi-step question such as "先查上海天气，再推荐穿搭", **When** the model issues multiple tool calls, **Then** the conversation proceeds through each call and returns a coherent final answer.

---

### User Story 3 - Consistent Cross-Model Behavior (Priority: P3)

A user runs the same query against multiple models in a single Quest. Each model may use tools or time context differently, but the application layer presents the process and results in a uniform way. The user sees which tools were invoked and can compare answers across models fairly.

**Why this priority**: The product is positioned as a multi-model comparison environment. A unified tool interaction layer ensures that differences in model outputs reflect model quality, not inconsistent tool handling.

**Independent Test**: Can be fully tested by selecting two different models for the same prompt and verifying that both responses are processed through the same tool-calling lifecycle and shown with the same status affordances.

**Acceptance Scenarios**:

1. **Given** a user selects two models that both support tool calling, **When** they ask a question requiring a stock lookup, **Then** both models can invoke the same tool and the user sees identical tool-call status indicators for each model.
2. **Given** one selected model supports tool calling and another does not, **When** the user asks a tool-dependent question, **Then** the system degrades gracefully for the unsupported model without breaking the Quest for the supported model.
3. **Given** two models return different tool arguments for the same query, **When** the unified interaction layer processes them, **Then** each tool is executed independently and the final answers are compared on equal footing.

---

### Edge Cases

- What happens when the model requests a tool that does not exist or is not enabled?
- What happens when a tool call exceeds the 15-second timeout or the retry also fails?
- What happens when the user has selected multiple models and one model supports tool calling while another does not?
- What happens when two models request the same tool with the same arguments during the same turn? (The unified interaction layer deduplicates and shares the single result.)
- What happens when two models request the same tool with different arguments during the same turn? (Each unique invocation is executed independently and only shared with the models that requested exactly those arguments.)
- What happens when a tool returns structured data that the model cannot consume?
- How does the system handle sensitive user information in tool arguments? (Tool arguments are passed to external tools as-is; administrators control tool availability through user permissions, and users are responsible for avoiding sensitive input.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST always inject the current date and time into the conversation context, so the model can interpret relative terms like "today" or "next week" correctly whenever they appear.
- **FR-002**: System MUST provide a unified interaction layer that represents tool calls, tool results, and final answers using a single model-independent format across all supported LLM providers.
- **FR-003**: The LLM adapter layer MUST translate provider-specific tool-calling protocols into the unified interaction layer format, and vice versa, without exposing provider differences to the application layer.
- **FR-004**: System MUST support a set of built-in tools for common external information needs: current time, web search, stock quote, weather, and news retrieval.
- **FR-005**: System MUST allow the model to request a tool invocation, execute the tool, and feed the result back into the conversation so the model can produce a final answer.
- **FR-006**: System MUST expose tool-invocation status to the user so they can see when a tool is being called and whether it succeeded or failed.
- **FR-007**: System MUST apply a 15-second timeout and one retry with short exponential backoff to each tool invocation. After the retry is exhausted, failures MUST be handled gracefully by returning a clear error explanation to the model and, if the model cannot recover, by surfacing a concise message to the user.
- **FR-008**: System MUST allow multiple models to participate in the same Quest; identical tool invocations requested by different models within the same turn MUST be deduplicated by the unified interaction layer and the result shared across the requesting models, while each model's tool events remain independently mapped through the adapter layer.
- **FR-009**: System MUST gate tool availability based on model capability and user permission, so unsupported models do not attempt tool calls and unauthorized users cannot invoke restricted tools.
- **FR-010**: System MUST persist tool-call metadata and results as part of the conversation history so that shared Quests and analytics can reproduce what was shown to the user.

### Assumptions

- The first release focuses on built-in tools administered by the platform. Third-party tool registries or MCP servers may be added later.
- Tool execution is performed on the application side; the model only decides whether to call a tool and with what arguments.
- Tool arguments are passed to external tools as-is; the system does not perform automated redaction or masking of sensitive data.
- Time injection is provided as context and does not require an external tool call unless the user explicitly asks for a time lookup from an authoritative source.

### Key Entities *(include if feature involves data)*

- **Tool Definition**: A declarative description of an external capability, including its name, purpose, parameter schema, and return schema.
- **Tool Invocation**: A request emitted by a model to execute a specific tool with a set of arguments.
- **Tool Result**: The output produced by executing a tool, which is returned to the model as part of the ongoing conversation context.
- **Unified Interaction Event**: An application-level record representing a tool call, tool result, or status update in a provider-independent format.
- **Time Context**: The current date, time, and timezone injected into the model context to enable time-sensitive reasoning.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users receive correct, date-aware answers to prompts containing relative time references (e.g., "today", "next week") in at least 95% of cases.
- **SC-002**: Users can ask a question requiring external data and receive an answer informed by tool results within 30 seconds end-to-end in 90% of attempts.
- **SC-003**: Tool-invocation behavior is consistent across all supported models: users see the same status indicators and can compare tool-driven answers fairly when two models are selected in the same Quest.
- **SC-004**: When a tool fails or times out, the system surfaces a clear explanation to the user instead of returning an incorrect or hallucinated value, with a failure-recovery rate of at least 99%.
- **SC-005**: Users can share or revisit a Quest and see the tool calls and results that contributed to each model response, with complete metadata preserved for 100% of tool-driven turns.

## Notes

- This feature deliberately separates the application-level unified interaction layer from provider-specific adapter logic. The adapter layer should only contain mappings between the unified format and each LLM's native tool-calling representation.
- The unified interaction layer is responsible for deduplicating identical tool invocations within a single turn across multiple models, while still preserving per-model independence for event ordering and adapter-specific mappings.
- Time awareness and external tool use are distinct capabilities, but they share the same underlying need for a model-independent context and event layer.
- The design should preserve the existing multi-model, streaming comparison experience; tool calls must not block or serialize responses in a way that removes the concurrent comparison value.
