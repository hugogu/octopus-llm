import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { DELETE, GET, PATCH, POST, PUT } from "./route";

describe("same-origin API proxy", () => {
  afterEach(() => vi.unstubAllGlobals());
  afterEach(() => delete process.env.TRUSTED_INGRESS_TOKEN);

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

  it("does not forward client-controlled forwarding headers and uses only the HttpOnly session cookie", async () => {
    let captured: RequestInit | undefined;
    vi.stubGlobal("fetch", vi.fn().mockImplementation((_url: unknown, init: RequestInit) => {
      captured = init;
      return Promise.resolve(new Response("{}", { status: 200 }));
    }));
    const request = new NextRequest("http://localhost/api/v2/me", {
      headers: {
        Authorization: "Bearer browser-visible-value",
        Cookie: "auth_token=http-only-jwt; auth_session=1",
        Forwarded: "for=203.0.113.7",
        "X-Forwarded-For": "203.0.113.7, 198.51.100.8",
        "X-Real-IP": "203.0.113.7",
      },
    });

    await GET(request, { params: Promise.resolve({ path: ["v2", "me"] }) });

    const headers = new Headers(captured?.headers);
    expect(headers.get("authorization")).toBe("Bearer http-only-jwt");
    expect(headers.get("forwarded")).toBeNull();
    expect(headers.get("x-forwarded-for")).toBeNull();
    expect(headers.get("x-real-ip")).toBeNull();
  });

  it("forwards a single client IP only when the trusted ingress authenticates it", async () => {
    process.env.TRUSTED_INGRESS_TOKEN = "proxy-only-secret";
    let captured: RequestInit | undefined;
    vi.stubGlobal("fetch", vi.fn().mockImplementation((_url: unknown, init: RequestInit) => {
      captured = init;
      return Promise.resolve(new Response("{}", { status: 200 }));
    }));
    const request = new NextRequest("http://localhost/api/v2/anonymous/models", {
      headers: {
        "X-Forwarded-For": "203.0.113.7",
        "X-Octopus-Ingress-Token": "proxy-only-secret",
      },
    });

    await GET(request, { params: Promise.resolve({ path: ["v2", "anonymous", "models"] }) });

    expect(new Headers(captured?.headers).get("x-forwarded-for")).toBe("203.0.113.7");
  });

  it("rejects cross-origin write requests that would use the HttpOnly session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const request = new NextRequest("http://localhost/api/v2/chat/sessions", {
      method: "POST",
      headers: {
        Cookie: "auth_token=http-only-jwt; auth_session=1",
        Origin: "https://attacker.example",
      },
    });

    const response = await POST(request, { params: Promise.resolve({ path: ["v2", "chat", "sessions"] }) });

    expect(response.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
