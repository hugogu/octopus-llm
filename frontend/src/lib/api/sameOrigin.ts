import type { NextRequest } from "next/server";

export function isSameOrigin(request: NextRequest): boolean {
  // Browsers set this forbidden header using the public URL, before proxy rewriting.
  const site = request.headers.get("sec-fetch-site");
  if (site !== null) return site === "same-origin";
  return request.headers.get("origin") === request.nextUrl.origin;
}
