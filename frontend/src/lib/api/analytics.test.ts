import { afterEach, describe, expect, it, vi } from "vitest";
import { getPublicModelAnalytics } from "./analytics";

describe("analytics API", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("serializes public filters and bounded page arguments", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      items: [], page: 1, size: 25, totalElements: 0, totalPages: 0,
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await getPublicModelAnalytics({ protocol: "openai-compatible", modelId: "gpt", page: 1, size: 25 });
    expect(String(fetchMock.mock.calls.at(0)?.at(0))).toContain(
      "/api/v2/analytics/public/by-model?protocol=openai-compatible&modelId=gpt&page=1&size=25",
    );
  });
});
