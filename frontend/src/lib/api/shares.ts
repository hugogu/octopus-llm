import { apiUrl } from "@/lib/api/base";
import type { PageResponse, ShareLink, SharedSession } from "@/lib/types/api";
import type { LikeState } from "@/lib/api/reactions";

async function checked<T>(responseValue: Response | Promise<Response>): Promise<T> {
  const response = await responseValue;
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw Object.assign(new Error(error.message ?? "Share request failed"), { status: response.status });
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export type ShareScope = "authenticated" | "public";

export function createShare(sessionId: string, token: string, scope: ShareScope = "authenticated"): Promise<ShareLink> {
  return checked(fetch(apiUrl(`/api/v2/chat/sessions/${encodeURIComponent(sessionId)}/shares`), {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ scope }),
  }));
}

export function listShares(sessionId: string, token: string): Promise<PageResponse<ShareLink>> {
  return checked(fetch(apiUrl(`/api/v2/chat/sessions/${encodeURIComponent(sessionId)}/shares?page=0&size=25`), {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  }));
}

export function revokeShare(sessionId: string, shareToken: string, token: string): Promise<void> {
  return checked(fetch(apiUrl(`/api/v2/chat/sessions/${encodeURIComponent(sessionId)}/shares/${encodeURIComponent(shareToken)}`), {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  }));
}

export function changeShareScope(
  sessionId: string,
  shareToken: string,
  scope: ShareScope,
  token: string,
): Promise<ShareLink> {
  return checked(fetch(apiUrl(`/api/v2/chat/sessions/${encodeURIComponent(sessionId)}/shares/${encodeURIComponent(shareToken)}`), {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ scope }),
  }));
}

export function getSharedSession(shareToken: string, token?: string): Promise<SharedSession> {
  return checked(fetch(apiUrl(`/api/v2/shared/${encodeURIComponent(shareToken)}`), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    credentials: "include",
    cache: "no-store",
  }));
}

export interface SharedSessionImportResult {
  sessionId: string;
  title: string | null;
  importedFromLabel: string;
}

export function newShareImportKey(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function importSharedSession(
  shareToken: string,
  idempotencyKey: string,
  token: string,
): Promise<SharedSessionImportResult> {
  return checked(fetch(apiUrl(`/api/v2/shared/${encodeURIComponent(shareToken)}/import`), {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Idempotency-Key": idempotencyKey },
    credentials: "include",
  }));
}

export function anonymousLike(shareToken: string, responseId: string): Promise<{
  responseId: string;
  anonymousLikeCount: number;
  likedByThisVisitor: boolean;
}> {
  return checked(fetch(apiUrl(`/api/v2/shared/${encodeURIComponent(shareToken)}/responses/${encodeURIComponent(responseId)}/like`), {
    method: "POST",
    credentials: "include",
  }));
}

export function sharedNamedLike(
  shareToken: string,
  responseId: string,
  token: string,
  liked: boolean,
): Promise<LikeState> {
  return checked(fetch(apiUrl(`/api/v2/shared/${encodeURIComponent(shareToken)}/responses/${encodeURIComponent(responseId)}/like`), {
    method: liked ? "PUT" : "DELETE",
    headers: { Authorization: `Bearer ${token}` },
    credentials: "include",
  }));
}
