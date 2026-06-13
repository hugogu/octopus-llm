import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useParallelStream } from "./useParallelStream";

describe("useParallelStream", () => {
  it("keys duplicate literal model IDs by configured-model UUID", () => {
    const { result } = renderHook(() => useParallelStream());
    act(() => result.current.reset(["configured-a", "configured-b"]));
    act(() => result.current.handleEvent({ event: "turn_created", turnId: "turn", sequenceNum: 1 }));
    act(() => result.current.handleEvent({
      event: "token",
      configuredModelId: "configured-a",
      modelId: "same-model",
      delta: "A",
    }));
    act(() => result.current.handleEvent({
      event: "token",
      configuredModelId: "configured-b",
      modelId: "same-model",
      delta: "B",
    }));

    expect(result.current.models["configured-a"]?.text).toBe("A");
    expect(result.current.models["configured-b"]?.text).toBe("B");
  });

  it("retains completion metadata on the configured model", () => {
    const { result } = renderHook(() => useParallelStream());
    act(() => result.current.reset(["configured-a"]));
    act(() => result.current.handleEvent({
      event: "model_complete",
      configuredModelId: "configured-a",
      modelId: "provider-id",
      inputTokens: 3,
      outputTokens: 7,
      latencyMs: 42,
      responseId: "response-a",
    }));

    expect(result.current.models["configured-a"]).toMatchObject({
      status: "complete",
      inputTokens: 3,
      outputTokens: 7,
      latencyMs: 42,
      responseId: "response-a",
    });
  });
});
