import { afterEach, describe, expect, it, vi } from "vitest";
import { streamTurnV2 } from "./chatV2";

describe("chat stream API", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("marks selected models incomplete when the SSE response closes early", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      'data: {"event":"token","configuredModelId":"m1","modelId":"provider-m1","delta":"hello"}\n\n',
      { status: 200, headers: { "Content-Type": "text/event-stream" } },
    )));
    const events: unknown[] = [];

    await streamTurnV2(
      "session-1",
      { promptText: "hello", selectedConfiguredModelIds: ["m1", "m2"] },
      (event) => events.push(event),
      "token",
    );

    expect(events[0]).toMatchObject({ event: "token", configuredModelId: "m1", delta: "hello" });
    expect(events[1]).toMatchObject({
      event: "model_error",
      configuredModelId: "m1",
      error: "The response stream ended before this model completed.",
    });
    expect(events[2]).toMatchObject({
      event: "model_error",
      configuredModelId: "m2",
      error: "The response stream ended before this model completed.",
    });
  });
});
