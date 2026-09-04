import { afterEach, describe, expect, it, vi } from "vitest";
import { logout, migrateLegacySession } from "./auth";

describe("browser authentication", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "auth_token=; path=/; max-age=0";
    document.cookie = "auth_session=; path=/; max-age=0";
  });

  it("preserves the expiry of an unpadded base64url legacy JWT during migration", async () => {
    const expiresAt = "2030-01-01T00:00:00.000Z";
    const payload = btoa(JSON.stringify({ exp: Date.parse(expiresAt) / 1000, nonce: "xx" }))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");
    expect(payload.length % 4).not.toBe(0);
    document.cookie = `auth_token=header.${payload}.signature; path=/`;
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await migrateLegacySession();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(JSON.parse(String(request.body))).toMatchObject({ expiresAt });
  });

  it("always asks the proxy to revoke the backend session", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await logout();

    expect(fetchMock.mock.calls[0]).toEqual([`${window.location.origin}/api/v1/auth/logout`, { method: "POST" }]);
    expect(fetchMock.mock.calls[1]).toEqual(["/api/auth/session", { method: "DELETE", cache: "no-store" }]);
  });
});
