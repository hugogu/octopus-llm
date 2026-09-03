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
    ["GET", GET, ["v2", "anonymous", "models"]],
    ["POST", POST, ["v2", "anonymous", "conversations", "sync"]],
    ["POST", POST, ["v2", "admin", "model-bulk-operations", "preview"]],
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

  it("streams a binary multipart body upstream unbuffered, preserving path, query, headers, and bytes", async () => {
    // Bytes that are not valid UTF-8, to prove nothing decodes/re-encodes the body (feature 008
    // migration import is a binary multipart/ZIP upload streamed through the proxy).
    const payload = new Uint8Array([0xff, 0x00, 0x10, 0x42, 0x7f, 0x80, 0xfe, 0x01]);
    let captured: (RequestInit & { duplex?: string }) | undefined;
    const fetchMock = vi.fn().mockImplementation((_url: unknown, init: RequestInit) => {
      captured = init;
      return Promise.resolve(new Response("{}", { status: 200 }));
    });
    vi.stubGlobal("fetch", fetchMock);

    const path = ["v2", "admin", "migration", "import"];
    const request = new NextRequest(`http://localhost/api/${path.join("/")}?x=1`, {
      method: "POST",
      body: payload,
      headers: { "Content-Type": "multipart/form-data; boundary=abc123" },
    });
    await POST(request, { params: Promise.resolve({ path }) });

    // Exact upstream path + query preserved.
    expect(String(fetchMock.mock.calls.at(0)?.at(0))).toBe(`http://127.0.0.1:8080/api/${path.join("/")}?x=1`);
    // Streamed, not buffered: body is a ReadableStream forwarded with duplex: "half".
    expect(captured?.duplex).toBe("half");
    expect(captured?.body).toBeInstanceOf(ReadableStream);
    // Content-Type (incl. multipart boundary) forwarded verbatim.
    expect(new Headers(captured?.headers).get("content-type")).toBe("multipart/form-data; boundary=abc123");
    // Exact bytes preserved end to end.
    const forwarded = new Uint8Array(await new Response(captured?.body as ReadableStream).arrayBuffer());
    expect(Array.from(forwarded)).toEqual(Array.from(payload));
  });
});
