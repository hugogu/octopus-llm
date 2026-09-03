import { apiUrl } from "@/lib/api/base";
import type {
  AnonymousChatRequest,
  AnonymousModelV2,
  AnonymousSseEvent,
  PageResponse,
} from "@/lib/types/api";

async function checked(response: Response): Promise<Response> {
  if (response.ok) return response;
  const error = await response.json().catch(() => ({ message: response.statusText })) as {
    code?: string;
    message?: string;
  };
  throw Object.assign(new Error(error.message ?? "Request failed"), {
    status: response.status,
    code: error.code,
  });
}

export async function listAnonymousModels(page = 0, size = 100): Promise<PageResponse<AnonymousModelV2>> {
  const response = await checked(await fetch(
    apiUrl(`/api/v2/anonymous/models?page=${page}&size=${size}`),
    { cache: "no-store" },
  ));
  return response.json() as Promise<PageResponse<AnonymousModelV2>>;
}

/** Fetches the complete public catalogue while respecting the backend's 100-item page limit. */
export async function listAllAnonymousModels(): Promise<AnonymousModelV2[]> {
  const first = await listAnonymousModels(0, 100);
  if (first.totalPages <= 1) return first.items;
  const pages = await Promise.all(
    Array.from({ length: first.totalPages - 1 }, (_, index) => listAnonymousModels(index + 1, 100)),
  );
  return [first, ...pages].flatMap((page) => page.items);
}

export async function streamAnonymousTurn(
  body: AnonymousChatRequest,
  onEvent: (event: AnonymousSseEvent) => void,
): Promise<void> {
  const response = await checked(await fetch(apiUrl("/api/v2/anonymous/chat/turns"), {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
    body: JSON.stringify(body),
  }));
  await readSse(response, onEvent);
}

async function readSse(
  response: Response,
  onEvent: (event: AnonymousSseEvent) => void,
): Promise<void> {
  const reader = response.body?.getReader();
  if (!reader) throw new Error("No response body");

  const decoder = new TextDecoder();
  let buffer = "";
  let eventName: string | undefined;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";
    for (const line of lines) {
      if (line.startsWith("event:")) {
        eventName = line.slice(6).trim();
        continue;
      }
      if (!line.startsWith("data:")) continue;
      const data = line.slice(5).trim();
      if (!data) continue;
      try {
        const payload = JSON.parse(data) as Record<string, unknown>;
        onEvent({ event: eventName ?? "error", ...payload } as AnonymousSseEvent);
      } catch {
        onEvent({ event: "error", code: "MALFORMED_STREAM", message: "The response stream was invalid." });
      } finally {
        eventName = undefined;
      }
    }
  }
}
