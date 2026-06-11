import type {
  ChatSession,
  CreateSessionRequest,
  CreateSessionResponse,
  ListSessionsResponse,
  GetSessionResponse,
  SubmitTurnRequest,
  SseEvent,
} from "@/lib/types/api";
import { getToken } from "@/lib/api/auth";
import { apiUrl } from "@/lib/api/base";

function authHeaders(token?: string): Record<string, string> {
  const t = token ?? getToken();
  return t ? { Authorization: `Bearer ${t}`, "Content-Type": "application/json" } : { "Content-Type": "application/json" };
}

export async function createSession(
  req?: CreateSessionRequest,
  token?: string,
): Promise<CreateSessionResponse> {
  const res = await fetch(apiUrl("/api/v1/chat/sessions"), {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(req ?? {}),
  });
  if (!res.ok) throw new Error(`Failed to create session: ${res.status}`);
  return res.json() as Promise<CreateSessionResponse>;
}

export async function listSessions(
  params?: { limit?: number; offset?: number },
  token?: string,
): Promise<ListSessionsResponse> {
  const url = new URL(apiUrl("/api/v1/chat/sessions"));
  if (params?.limit) url.searchParams.set("limit", String(params.limit));
  if (params?.offset) url.searchParams.set("offset", String(params.offset));
  const res = await fetch(url.toString(), {
    headers: authHeaders(token),
    cache: "no-store",
  });
  if (!res.ok) throw new Error(`Failed to list sessions: ${res.status}`);
  return res.json() as Promise<ListSessionsResponse>;
}

export async function getSession(id: string, token?: string): Promise<GetSessionResponse> {
  const res = await fetch(apiUrl(`/api/v1/chat/sessions/${encodeURIComponent(id)}`), {
    headers: authHeaders(token),
    cache: "no-store",
  });
  if (!res.ok) throw new Error(`Failed to get session: ${res.status}`);
  return res.json() as Promise<GetSessionResponse>;
}

export async function deleteSession(id: string, token?: string): Promise<void> {
  const res = await fetch(apiUrl(`/api/v1/chat/sessions/${encodeURIComponent(id)}`), {
    method: "DELETE",
    headers: authHeaders(token),
  });
  if (!res.ok) throw new Error(`Failed to delete session: ${res.status}`);
}

export async function streamTurn(
  sessionId: string,
  body: SubmitTurnRequest,
  onEvent: (event: SseEvent) => void,
  token?: string,
): Promise<void> {
  const res = await fetch(
    apiUrl(`/api/v1/chat/sessions/${encodeURIComponent(sessionId)}/turns`),
    {
      method: "POST",
      headers: {
        ...authHeaders(token),
        Accept: "text/event-stream",
      },
      body: JSON.stringify(body),
    },
  );

  if (!res.ok) {
    if (res.status === 409) {
      const data = await res.json() as { turnId?: string };
      throw Object.assign(new Error("Duplicate request"), { code: "DUPLICATE", turnId: data.turnId });
    }
    throw new Error(`Stream failed: ${res.status}`);
  }

  const reader = res.body?.getReader();
  if (!reader) throw new Error("No response body");

  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";

    for (const line of lines) {
      if (!line.startsWith("data:")) continue;
      const raw = line.slice(5).trim();
      if (!raw) continue;
      try {
        const event = JSON.parse(raw) as SseEvent;
        onEvent(event);
      } catch {
        // skip malformed lines
      }
    }
  }
}
