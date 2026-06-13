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

export function createShare(sessionId: string, token: string): Promise<ShareLink> {
  return checked(fetch(apiUrl(`/api/v2/chat/sessions/${encodeURIComponent(sessionId)}/shares`), {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
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

export function getSharedSession(shareToken: string): Promise<SharedSession> {
  return checked(fetch(apiUrl(`/api/v2/shared/${encodeURIComponent(shareToken)}`), {
    credentials: "include",
    cache: "no-store",
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
