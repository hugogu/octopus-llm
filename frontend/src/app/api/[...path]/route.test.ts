import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { DELETE, GET, PATCH, POST, PUT } from "./route";

describe("same-origin API proxy", () => {
  afterEach(() => vi.unstubAllGlobals());

  it.each([
    ["GET", GET, ["v2", "analytics", "public", "by-model"]],
    ["POST", POST, ["v2", "chat", "sessions", "s1", "shares"]],
    ["PUT", PUT, ["v2", "responses", "r1", "like"]],
    ["PATCH", PATCH, ["v2", "me"]],
    ["DELETE", DELETE, ["v2", "shared", "opaque", "responses", "r1", "like"]],
  ] as const)("preserves the complete upstream path for %s", async (method, handler, path) => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const request = new NextRequest(`http://localhost/api/${path.join("/")}?page=2`, {
      method,
      body: method === "GET" ? undefined : "{}",
      headers: { "Content-Type": "application/json" },
    });
    await handler(request, { params: Promise.resolve({ path: [...path] }) });
    expect(String(fetchMock.mock.calls.at(0)?.at(0))).toBe(`http://127.0.0.1:8080/api/${path.join("/")}?page=2`);
  });
});
