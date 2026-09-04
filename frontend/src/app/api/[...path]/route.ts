import { NextRequest, NextResponse } from "next/server";
import { isIP } from "node:net";

const backendBaseUrl =
  process.env.INTERNAL_API_URL
  ?? process.env.BROWSER_API_URL
  ?? process.env.NEXT_PUBLIC_API_URL
  ?? "http://127.0.0.1:8080";

const hopByHopHeaders = new Set([
  "connection",
  "content-length",
  "host",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);

// Browser-only and client-controlled forwarding headers must not reach the backend. The browser
// talks to this route same-origin; Origin/Referer are meaningless on the server-to-server hop, and
// trusting a caller-provided X-Forwarded-For lets it choose another user's rate-limit bucket.
const browserOnlyRequestHeaders = new Set([
  "origin",
  "referer",
  "forwarded",
  "x-forwarded-for",
  "x-forwarded-host",
  "x-forwarded-proto",
  "x-real-ip",
  "x-octopus-ingress-token",
]);

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

async function proxy(request: NextRequest, context: RouteContext): Promise<Response> {
  const { path } = await context.params;
  const upstreamPath = ["api", ...path].join("/");
  const upstreamUrl = new URL(upstreamPath, `${backendBaseUrl}/`);
  upstreamUrl.search = request.nextUrl.search;

  const sessionToken = request.cookies.get("auth_token")?.value;
  if (sessionToken && !isSafeMethod(request.method) && !isSameOrigin(request)) {
    return NextResponse.json({ message: "Same-origin request required" }, { status: 403 });
  }

  const headers = new Headers();
  request.headers.forEach((value, key) => {
    const lowerKey = key.toLowerCase();
    if (!hopByHopHeaders.has(lowerKey) && !browserOnlyRequestHeaders.has(lowerKey)) {
      headers.set(key, value);
    }
  });

  // The browser never receives the JWT after the session route stores it as HttpOnly. Ignore any
  // Authorization value supplied by client JavaScript and inject only the same-origin session cookie.
  headers.delete("authorization");
  if (sessionToken) headers.set("Authorization", `Bearer ${sessionToken}`);

  // A direct browser request must never pick a different user's rate-limit bucket by supplying
  // X-Forwarded-For. Deployments that have a trusted edge proxy can opt in by having that proxy
  // overwrite X-Forwarded-For with one literal client IP and attach the configured private token.
  const clientIp = trustedClientIp(request);
  if (clientIp) headers.set("X-Forwarded-For", clientIp);

  const init: RequestInit = {
    method: request.method,
    headers,
    redirect: "manual",
    cache: "no-store",
  };

  if (!["GET", "HEAD"].includes(request.method)) {
    // Stream the raw body straight through instead of buffering it with arrayBuffer(): migration
    // artifacts (feature 008) can be very large, and buffering the whole upload/download in proxy
    // memory does not scale. Passing the ReadableStream preserves binary (multipart media, ZIP) and
    // JSON alike; undici requires duplex: "half" when the body is a stream. The Content-Type header
    // (incl. the multipart boundary) is forwarded above.
    init.body = request.body;
    (init as RequestInit & { duplex: "half" }).duplex = "half";
  }

  const upstream = await fetch(upstreamUrl, init);
  const responseHeaders = new Headers();
  upstream.headers.forEach((value, key) => {
    if (!hopByHopHeaders.has(key.toLowerCase())) {
      responseHeaders.set(key, value);
    }
  });

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}

function isSafeMethod(method: string): boolean {
  return method === "GET" || method === "HEAD" || method === "OPTIONS";
}

function isSameOrigin(request: NextRequest): boolean {
  return request.headers.get("origin") === request.nextUrl.origin;
}

function trustedClientIp(request: NextRequest): string | null {
  const ingressToken = process.env.TRUSTED_INGRESS_TOKEN;
  if (!ingressToken || request.headers.get("x-octopus-ingress-token") !== ingressToken) return null;
  const forwardedFor = request.headers.get("x-forwarded-for")?.trim();
  return forwardedFor && isIP(forwardedFor) !== 0 ? forwardedFor : null;
}

export async function GET(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function PUT(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function PATCH(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function DELETE(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function OPTIONS(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}
