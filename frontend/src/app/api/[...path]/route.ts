import { NextRequest } from "next/server";

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

// Browser-only request headers that must not be forwarded upstream. The browser talks to this
// route same-origin; the hop to the backend is server-to-server, so the browser's Origin/Referer
// are meaningless there. Forwarding them makes the backend's CORS filter evaluate (and reject) an
// otherwise same-origin request. Auth is via Bearer token and backend CSRF is disabled, so dropping
// these is safe. See SecurityConfig.corsConfigurationSource on the backend.
const browserOnlyRequestHeaders = new Set(["origin", "referer"]);

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

async function proxy(request: NextRequest, context: RouteContext): Promise<Response> {
  const { path } = await context.params;
  const upstreamPath = ["api", ...path].join("/");
  const upstreamUrl = new URL(upstreamPath, `${backendBaseUrl}/`);
  upstreamUrl.search = request.nextUrl.search;

  const headers = new Headers();
  request.headers.forEach((value, key) => {
    const lowerKey = key.toLowerCase();
    if (!hopByHopHeaders.has(lowerKey) && !browserOnlyRequestHeaders.has(lowerKey)) {
      headers.set(key, value);
    }
  });

  const init: RequestInit = {
    method: request.method,
    headers,
    redirect: "manual",
    cache: "no-store",
  };

  if (!["GET", "HEAD"].includes(request.method)) {
    // Forward the raw bytes — NOT request.text(), which UTF-8-decodes and corrupts binary bodies
    // such as multipart media uploads (feature 007). arrayBuffer preserves binary and JSON alike;
    // the Content-Type header (incl. the multipart boundary) is forwarded above.
    init.body = await request.arrayBuffer();
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
