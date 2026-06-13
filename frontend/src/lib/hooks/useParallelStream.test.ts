import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useParallelStream } from "./useParallelStream";

describe("useParallelStream", () => {
  it("keys duplicate literal model IDs by configured-model UUID", async () => {
    const { result } = renderHook(() => useParallelStream());
    act(() => result.current.start("session-a", ["configured-a", "configured-b"]));
    act(() => result.current.handleEvent("session-a", { event: "turn_created", turnId: "turn", sequenceNum: 1 }));
    act(() => result.current.handleEvent("session-a", {
      event: "token",
      configuredModelId: "configured-a",
      modelId: "same-model",
      delta: "A",
    }));
    act(() => result.current.handleEvent("session-a", {
      event: "token",
      configuredModelId: "configured-b",
      modelId: "same-model",
      delta: "B",
    }));

    // Token updates are flushed to React state on a short throttle (reading is decoupled from render).
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 30)); });

    expect(result.current.streams["session-a"]?.models["configured-a"]?.text).toBe("A");
    expect(result.current.streams["session-a"]?.models["configured-b"]?.text).toBe("B");
  });

  it("retains completion metadata on the configured model", () => {
    const { result } = renderHook(() => useParallelStream());
    act(() => result.current.start("session-a", ["configured-a"]));
    act(() => result.current.handleEvent("session-a", {
      event: "model_complete",
      configuredModelId: "configured-a",
      modelId: "provider-id",
      inputTokens: 3,
      outputTokens: 7,
      latencyMs: 42,
      responseId: "response-a",
    }));

    expect(result.current.streams["session-a"]?.models["configured-a"]).toMatchObject({
      status: "complete",
      inputTokens: 3,
      outputTokens: 7,
      latencyMs: 42,
      responseId: "response-a",
    });
  });

  it("keeps a background session stream when another session starts", async () => {
    const { result } = renderHook(() => useParallelStream());
    act(() => result.current.start("session-a", ["model-a"]));
    act(() => result.current.handleEvent("session-a", {
      event: "token",
      configuredModelId: "model-a",
      modelId: "provider-a",
      delta: "still here",
    }));
    act(() => result.current.start("session-b", ["model-b"]));

    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 30)); });

    expect(result.current.streams["session-a"]?.models["model-a"]?.text).toBe("still here");
    expect(result.current.streams["session-b"]?.models["model-b"]?.status).toBe("idle");
  });
});
