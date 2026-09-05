import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { DELETE, POST } from "./route";

describe("secure browser session route", () => {
  afterEach(() => vi.unstubAllEnvs());

  it.each(["same-origin", "same-site", "cross-site", "none"])("checks browser metadata behind an HTTPS proxy: %s", async (site) => {
    const headers = { Origin: "https://octopusllm.dev", "Sec-Fetch-Site": site, "Content-Type": "application/json" };
    const response = await POST(new NextRequest("http://0.0.0.0:3000/api/auth/session", {
      method: "POST", headers, body: JSON.stringify({ token: "test-token" }),
    }));
    expect(response.status).toBe(site === "same-origin" ? 200 : 403);
    expect(DELETE(new NextRequest("http://0.0.0.0:3000/api/auth/session", {
      method: "DELETE", headers,
    })).status).toBe(site === "same-origin" ? 200 : 403);
  });

  it("stores the JWT only in an HttpOnly, secure-in-production cookie", async () => {
    const request = new NextRequest("http://localhost/api/auth/session", {
      method: "POST",
      headers: { Origin: "http://localhost", "Content-Type": "application/json" },
      body: JSON.stringify({ token: "header.payload.signature", expiresAt: new Date(Date.now() + 60_000).toISOString() }),
    });

    const response = await POST(request);
    const cookies = response.headers.getSetCookie();

    expect(response.status).toBe(200);
    expect(cookies.find((cookie) => cookie.startsWith("auth_token="))).toContain("HttpOnly");
    expect(cookies.find((cookie) => cookie.startsWith("auth_token="))).toMatch(/SameSite=lax/i);
    expect(cookies.find((cookie) => cookie.startsWith("auth_session="))).not.toContain("header.payload.signature");
  });

  it("marks the credential cookie Secure in production", async () => {
    vi.stubEnv("NODE_ENV", "production");
    const request = new NextRequest("https://octopus.example/api/auth/session", {
      method: "POST",
      headers: { Origin: "https://octopus.example", "Content-Type": "application/json" },
      body: JSON.stringify({ token: "header.payload.signature" }),
    });

    const response = await POST(request);

    expect(response.headers.getSetCookie().find((cookie) => cookie.startsWith("auth_token="))).toContain("Secure");
  });

  it("rejects cross-origin attempts to create or clear a session", async () => {
    const request = new NextRequest("http://localhost/api/auth/session", {
      method: "POST",
      headers: { Origin: "https://attacker.example", "Content-Type": "application/json" },
      body: JSON.stringify({ token: "anything" }),
    });
    const deletion = new NextRequest("http://localhost/api/auth/session", {
      method: "DELETE",
      headers: { Origin: "https://attacker.example" },
    });

    await expect(POST(request)).resolves.toMatchObject({ status: 403 });
    expect(DELETE(deletion).status).toBe(403);
  });
});
