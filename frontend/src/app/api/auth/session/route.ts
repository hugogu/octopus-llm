import { NextRequest, NextResponse } from "next/server";

const AUTH_COOKIE = "auth_token";
const SESSION_MARKER_COOKIE = "auth_session";
const DEFAULT_MAX_AGE_SECONDS = 60 * 60;

type SessionRequest = {
  token?: unknown;
  expiresAt?: unknown;
};

export async function POST(request: NextRequest): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return NextResponse.json({ message: "Same-origin request required" }, { status: 403 });
  }

  const body = await request.json().catch(() => null) as SessionRequest | null;
  if (typeof body?.token !== "string" || body.token.trim().length === 0) {
    return NextResponse.json({ message: "A token is required" }, { status: 400 });
  }

  const maxAge = maxAgeFrom(body.expiresAt);
  const response = NextResponse.json({ status: "ok" });
  response.cookies.set(AUTH_COOKIE, body.token, secureCookieOptions(maxAge, true));
  // This marker lets the existing client-side UX distinguish an authenticated browser without
  // exposing reusable credentials. It is never accepted as backend authentication.
  response.cookies.set(SESSION_MARKER_COOKIE, "1", secureCookieOptions(maxAge, false));
  return response;
}

export function DELETE(request: NextRequest): NextResponse {
  if (!isSameOrigin(request)) {
    return NextResponse.json({ message: "Same-origin request required" }, { status: 403 });
  }
  const response = NextResponse.json({ status: "ok" });
  response.cookies.set(AUTH_COOKIE, "", secureCookieOptions(0, true));
  response.cookies.set(SESSION_MARKER_COOKIE, "", secureCookieOptions(0, false));
  return response;
}

function isSameOrigin(request: NextRequest): boolean {
  const origin = request.headers.get("origin");
  return origin === request.nextUrl.origin;
}

function maxAgeFrom(expiresAt: unknown): number {
  if (typeof expiresAt !== "string") return DEFAULT_MAX_AGE_SECONDS;
  const remaining = Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000);
  return Number.isFinite(remaining) ? Math.max(0, remaining) : DEFAULT_MAX_AGE_SECONDS;
}

function secureCookieOptions(maxAge: number, httpOnly: boolean) {
  return {
    httpOnly,
    maxAge,
    path: "/",
    sameSite: "lax" as const,
    secure: process.env.NODE_ENV === "production",
  };
}
