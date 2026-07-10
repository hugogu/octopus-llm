# Quickstart: Unified Tool Calling and Time Awareness

**Feature**: Unified Tool Calling and Time Awareness  
**Date**: 2026-07-10

## Purpose

This quickstart provides end-to-end validation scenarios to verify that time context is injected, built-in tools are invoked, and the unified interaction layer behaves consistently across multiple models.

## Prerequisites

- Backend is running with an up-to-date database.
- Flyway migration `V037__tool_invocations.sql` has been applied.
- At least one OpenAI-compatible model and one Anthropic model are configured and support tool calling.
- One model that does not support tool calling is configured for the graceful-degradation scenario.

## Scenario 1: Time-Aware Answer (No External Tool)

**Goal**: Verify that the current date/time is always injected and interpreted correctly.

**Steps**:
1. Create a new Quest.
2. Select any text model.
3. Send: `今天 A 股怎么样？`

**Expected result**:
- The response mentions today's date and refers to today's market conditions or session.
- The model does not ask "what is today?" or provide a generic answer.

## Scenario 2: Single-Step Tool Use (Stock Price)

**Goal**: Verify that a built-in tool is invoked and its result is included in the answer.

**Steps**:
1. Create a new Quest.
2. Select one model that supports tool calling.
3. Send: `当前贵州茅台的股价是多少？`

**Expected result**:
- A tool-status indicator appears in the message thread.
- The response contains a current stock price (or a clear "unavailable" message if the tool fails).
- A `tool_invocations` row is created with `tool_name = 'stock_quote'` and `status = success` (or `failed`/`timeout`).

## Scenario 3: Multi-Step Tool Use (Weather + Recommendation)

**Goal**: Verify that multiple sequential tool calls can complete within a single response.

**Steps**:
1. Create a new Quest.
2. Select one model that supports tool calling.
3. Send: `先查上海天气，再推荐穿搭`

**Expected result**:
- Two tool-status indicators appear (or one indicator that transitions through both calls).
- The final answer references the weather result and provides a clothing recommendation.
- Two `tool_invocations` rows exist for the turn, linked to the same `provider_response`.

## Scenario 4: Cross-Model Deduplication

**Goal**: Verify that identical tool invocations across models are deduplicated and share results.

**Steps**:
1. Create a new Quest.
2. Select two different models that both support tool calling.
3. Send: `今天上证指数是多少？`

**Expected result**:
- Both responses show the same stock value.
- Only one `tool_invocations` row is created for the turn.
- Two `provider_response_tool_invocations` rows link the single invocation to both `provider_responses`.
- Both responses show identical tool-status indicators.

## Scenario 5: Graceful Degradation for Unsupported Model

**Goal**: Verify that a model without tool-calling capability does not break the Quest.

**Steps**:
1. Create a new Quest.
2. Select one model that supports tool calling and one model that does not.
3. Send: `今天北京天气如何？`

**Expected result**:
- The supported model invokes the weather tool and returns a weather-based answer.
- The unsupported model returns an answer without invoking the tool (e.g., it explains that it cannot retrieve live data, or uses its training cutoff knowledge).
- The Quest continues normally; the unsupported model's response does not cause errors.

## Scenario 6: Tool Failure Handling

**Goal**: Verify that a failing tool does not cause a crash or hallucination.

**Steps**:
1. Configure the stock tool to use an invalid API base URL (or block the network endpoint).
2. Create a new Quest.
3. Select a model that supports tool calling.
4. Send: `当前腾讯股价是多少？`

**Expected result**:
- After the 15-second timeout and one retry, the tool status shows failure.
- The model receives the error and either attempts to answer without the data or surfaces a concise "data unavailable" message to the user.
- No exception is thrown to the user; the response completes.

## Scenario 7: Share/Revisit a Tool-Driven Quest

**Goal**: Verify that tool-call metadata is preserved for sharing and replay.

**Steps**:
1. Create a Quest with a tool-driven question (e.g., Scenario 2).
2. Share the Quest using the share button.
3. Open the share link in an incognito window or revisit the Quest from the Quest list.

**Expected result**:
- The shared/public view shows the tool status and the final answer.
- The `tool_invocations` and `provider_response_tool_invocations` records are still present and linked to the response.
- Analytics can reconstruct the exact tool call and result that contributed to the answer.

## Validation Checklist

- [ ] Scenario 1: Time-aware answer reflects today's date.
- [ ] Scenario 2: Stock tool invoked and result included in answer.
- [ ] Scenario 3: Multiple tool calls in one turn produce a coherent final answer.
- [ ] Scenario 4: Identical tool calls across two models are deduplicated (one `tool_invocations` row, two join rows).
- [ ] Scenario 5: Unsupported model degrades gracefully without breaking the Quest.
- [ ] Scenario 6: Tool failure surfaces a clear message without crashing.
- [ ] Scenario 7: Shared/revisited Quest preserves tool-call metadata.

## Notes

- Built-in tools may require external API keys configured in the deployment environment (e.g., search API, stock API, weather API, news API). These keys are deployment secrets and are not stored in the database.
- If a tool fails because of missing external configuration, the failure message should indicate that the tool is unavailable rather than returning a fabricated value.
