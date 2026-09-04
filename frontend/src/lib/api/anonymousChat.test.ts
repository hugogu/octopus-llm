import { afterEach, describe, expect, it, vi } from "vitest";
import { listAnonymousModels, streamAnonymousTurn } from "./anonymousChat";
import { resetBrowserStorage, sseResponse } from "@/test/setup";

describe("anonymous chat API", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    resetBrowserStorage();
  });

  it("loads a paginated public catalogue without credentials", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await listAnonymousModels();
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain("/api/v2/anonymous/models?page=0&size=100");
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).has("authorization")).toBe(false);
  });

  it("parses named SSE events and preserves model-specific failures", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sseResponse([
      { event: "status", data: { state: "STARTED" } },
      { event: "token", data: { configuredModelId: "m1", text: "hello" } },
      { event: "model_complete", data: { configuredModelId: "m1", status: "COMPLETE" } },
      { event: "model_error", data: { configuredModelId: "m2", status: "ERROR", errorCode: "PROVIDER_ERROR", errorMessage: "safe" } },
      { event: "result", data: { state: "COMPLETE" } },
    ])));
    const events: unknown[] = [];
    await streamAnonymousTurn({
      clientConversationId: "c1",
      clientRequestId: "r1",
      promptText: "hello",
      selectedConfiguredModelIds: ["m1", "m2"],
      history: [],
    }, (event) => events.push(event));
    expect(events).toEqual([
      { event: "status", state: "STARTED" },
      { event: "token", configuredModelId: "m1", text: "hello" },
      { event: "model_complete", configuredModelId: "m1", status: "COMPLETE" },
      { event: "model_error", configuredModelId: "m2", status: "ERROR", errorCode: "PROVIDER_ERROR", errorMessage: "safe" },
      { event: "result", state: "COMPLETE" },
    ]);
  });

  it("flushes an unterminated final event and closes models missing a terminal event", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      'event: token\ndata: {"configuredModelId":"m1","text":"hello"}',
      { status: 200, headers: { "Content-Type": "text/event-stream" } },
    )));
    const events: AnonymousSseEvent[] = [];

    await streamAnonymousTurn({
      clientConversationId: "c1",
      clientRequestId: "r1",
      promptText: "hello",
      selectedConfiguredModelIds: ["m1", "m2"],
      history: [],
    }, (event) => events.push(event));

    expect(events[0]).toEqual({ event: "token", configuredModelId: "m1", text: "hello" });
    expect(events[1]).toMatchObject({
      event: "model_error",
      configuredModelId: "m1",
      errorCode: "INCOMPLETE_STREAM",
    });
    expect(events[2]).toMatchObject({
      event: "model_error",
      configuredModelId: "m2",
      errorCode: "INCOMPLETE_STREAM",
    });
  });
});
