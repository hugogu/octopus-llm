import { apiUrl } from "@/lib/api/base";
import { readJsonSse } from "@/lib/api/readJsonSse";
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
  const terminalModels = new Set<string>();
  await readJsonSse<AnonymousSseEvent>(
    response,
    (event) => {
      if (event.event === "model_complete" || event.event === "model_error") {
        terminalModels.add(event.configuredModelId);
      }
      onEvent(event);
    },
    {
      onMalformed: () => onEvent({
        event: "error",
        code: "MALFORMED_STREAM",
        message: "The response stream was invalid.",
      }),
    },
  );

  // A disconnected proxy can end a successful HTTP response without delivering the final model
  // event. Resolve those cards explicitly instead of leaving them permanently in streaming state.
  for (const configuredModelId of body.selectedConfiguredModelIds) {
    if (!terminalModels.has(configuredModelId)) {
      onEvent({
        event: "model_error",
        configuredModelId,
        status: "ERROR",
        errorCode: "INCOMPLETE_STREAM",
        errorMessage: "The response stream ended before this model completed.",
      });
    }
  }
}
