import { apiUrl } from "@/lib/api/base";

export interface LikeState {
  responseId: string;
  likeCount: number;
  likedByMe: boolean;
}

async function mutate(responseId: string, token: string, method: "PUT" | "DELETE"): Promise<LikeState> {
  const response = await fetch(apiUrl(`/api/v2/responses/${encodeURIComponent(responseId)}/like`), {
    method,
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(error.message ?? "Reaction update failed");
  }
  return response.json() as Promise<LikeState>;
}

export function likeResponse(responseId: string, token: string): Promise<LikeState> {
  return mutate(responseId, token, "PUT");
}

export function unlikeResponse(responseId: string, token: string): Promise<LikeState> {
  return mutate(responseId, token, "DELETE");
}
