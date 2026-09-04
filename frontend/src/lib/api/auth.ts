import type { LoginResponse, RegisterRequest, VerifyEmailRequest } from "@/lib/types/api";
import { apiUrl } from "@/lib/api/base";

const AUTH_SESSION_COOKIE = "auth_session";
const LEGACY_AUTH_TOKEN_COOKIE = "auth_token";

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ code: "UNKNOWN", message: res.statusText }));
    throw Object.assign(new Error(err.message ?? "Request failed"), { code: err.code, status: res.status });
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export async function register(req: RegisterRequest): Promise<{ message: string }> {
  return post("/api/v1/auth/register", req);
}

export async function verifyEmail(req: VerifyEmailRequest): Promise<{ message: string }> {
  return post("/api/v1/auth/verify-email", req);
}

export async function login(req: { email: string; password: string }): Promise<LoginResponse> {
  const data = await post<LoginResponse>("/api/v1/auth/login", req);
  await replaceAuthToken(data.token, data.expiresAt);
  return data;
}

export async function requestPasswordReset(email: string): Promise<{ status: string }> {
  return post("/api/v1/auth/password-reset/request", { email });
}

/**
 * Moves a newly-issued JWT into a same-origin HttpOnly cookie. The token exists in JavaScript only
 * for this response-handling step; subsequent API calls use the opaque `auth_session` marker and
 * the proxy attaches the HttpOnly cookie to the backend request.
 */
export async function replaceAuthToken(token: string, expiresAt?: string): Promise<void> {
  if (typeof window === "undefined") return;
  const response = await fetch("/api/auth/session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, expiresAt }),
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error("Unable to establish a secure session");
  }
}

export async function logout(): Promise<void> {
  if (typeof window === "undefined") return;
  await fetch(apiUrl("/api/v1/auth/logout"), { method: "POST" }).catch(() => {});
  await fetch("/api/auth/session", { method: "DELETE", cache: "no-store" }).catch(() => {});
}

/** Returns a non-sensitive session marker, or a legacy JWT only until migration completes. */
export function getToken(): string | null {
  if (typeof document === "undefined") return null;
  const marker = readCookie(AUTH_SESSION_COOKIE);
  if (marker === "1") return marker;

  // Existing deployments may have a short-lived JavaScript-readable JWT from before this change.
  // Keep it working just long enough for `migrateLegacySession` to replace it with HttpOnly storage.
  return readCookie(LEGACY_AUTH_TOKEN_COOKIE);
}

/** One-time compatibility path for a pre-hardening session cookie. */
export async function migrateLegacySession(): Promise<void> {
  if (typeof document === "undefined" || readCookie(AUTH_SESSION_COOKIE) === "1") return;
  const legacyToken = readCookie(LEGACY_AUTH_TOKEN_COOKIE);
  if (!legacyToken) return;
  await replaceAuthToken(legacyToken, jwtExpiry(legacyToken));
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]+)`));
  return match?.[1] ?? null;
}

function jwtExpiry(token: string): string | undefined {
  const payload = token.split(".")[1];
  if (!payload) return undefined;
  try {
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    const decoded = JSON.parse(atob(padded)) as { exp?: unknown };
    if (typeof decoded.exp !== "number" || !Number.isFinite(decoded.exp)) return undefined;
    const expiresAt = new Date(decoded.exp * 1000);
    return Number.isNaN(expiresAt.getTime()) ? undefined : expiresAt.toISOString();
  } catch {
    return undefined;
  }
}
