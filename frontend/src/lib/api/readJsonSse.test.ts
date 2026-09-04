import { describe, expect, it } from "vitest";
import { readJsonSse } from "./readJsonSse";

describe("readJsonSse", () => {
  it("does not carry an event name from a data-less record", async () => {
    const events: Record<string, unknown>[] = [];
    const response = new Response(
      'event: stale\n\n' +
      'data: {"message":"fresh"}\n\n',
      { headers: { "Content-Type": "text/event-stream" } },
    );

    await readJsonSse(response, (event) => events.push(event));

    expect(events).toEqual([{ message: "fresh" }]);
  });
});
