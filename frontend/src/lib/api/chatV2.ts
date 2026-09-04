import { getToken } from "@/lib/api/auth";
import { apiUrl } from "@/lib/api/base";
import { readJsonSse } from "@/lib/api/readJsonSse";
import type {
  ChatSessionV2,
  GetSessionResponseV2,
  PageResponse,
  SseEventV2,
  SubmitTurnRequestV2,
} from "@/lib/types/api";

function headers(token?: string): Record<string, string> {
  const resolved = token ?? getToken();
  return {
    "Content-Type": "application/json",
    ...(resolved ? { Authorization: `Bearer ${resolved}` } : {}),
  };
}

async function checked(response: Response): Promise<Response> {
  if (response.ok) return response;
  const error = await response.json().catch(() => ({ message: response.statusText })) as {
    message?: string;
    details?: { turnId?: string };
  };
  throw Object.assign(new Error(error.message ?? `Request failed: ${response.status}`), {
    status: response.status,
    turnId: error.details?.turnId,
  });
}

export async function createSessionV2(
  body: { title?: string } = {},
  token?: string,
): Promise<ChatSessionV2> {
  const response = await checked(await fetch(apiUrl("/api/v2/chat/sessions"), {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify(body),
  }));
  return response.json() as Promise<ChatSessionV2>;
}

export async function listSessionsV2(
  page = 0,
  size = 50,
  token?: string,
): Promise<PageResponse<ChatSessionV2>> {
  const response = await checked(await fetch(
    apiUrl(`/api/v2/chat/sessions?page=${page}&size=${size}`),
    { headers: headers(token), cache: "no-store" },
  ));
  return response.json() as Promise<PageResponse<ChatSessionV2>>;
}

export async function getSessionV2(id: string, token?: string): Promise<GetSessionResponseV2> {
  const response = await checked(await fetch(
    apiUrl(`/api/v2/chat/sessions/${encodeURIComponent(id)}`),
    { headers: headers(token), cache: "no-store" },
  ));
  return response.json() as Promise<GetSessionResponseV2>;
}

export async function deleteSessionV2(id: string, token?: string): Promise<void> {
  await checked(await fetch(apiUrl(`/api/v2/chat/sessions/${encodeURIComponent(id)}`), {
    method: "DELETE",
    headers: headers(token),
  }));
}

export async function deleteTurnV2(sessionId: string, turnId: string, token?: string): Promise<void> {
  await checked(await fetch(
    apiUrl(
      `/api/v2/chat/sessions/${encodeURIComponent(sessionId)}` +
      `/turns/${encodeURIComponent(turnId)}`,
    ),
    { method: "DELETE", headers: headers(token) },
  ));
}

export async function deleteResponseV2(
  sessionId: string,
  turnId: string,
  responseId: string,
  token?: string,
): Promise<void> {
  await checked(await fetch(
    apiUrl(
      `/api/v2/chat/sessions/${encodeURIComponent(sessionId)}` +
      `/turns/${encodeURIComponent(turnId)}` +
      `/responses/${encodeURIComponent(responseId)}`,
    ),
    { method: "DELETE", headers: headers(token) },
  ));
}

export async function streamTurnV2(
  sessionId: string,
  body: SubmitTurnRequestV2,
  onEvent: (event: SseEventV2) => void,
  token?: string,
): Promise<void> {
  const response = await checked(await fetch(
    apiUrl(`/api/v2/chat/sessions/${encodeURIComponent(sessionId)}/turns`),
    {
      method: "POST",
      headers: { ...headers(token), Accept: "text/event-stream" },
      body: JSON.stringify(body),
    },
  ));
  await readModelSse(response, body.selectedConfiguredModelIds, onEvent);
}

export async function retryModelV2(
  sessionId: string,
  turnId: string,
  configuredModelId: string,
  clientRequestId: string,
  onEvent: (event: SseEventV2) => void,
  token?: string,
): Promise<void> {
  const response = await checked(await fetch(
    apiUrl(
      `/api/v2/chat/sessions/${encodeURIComponent(sessionId)}` +
      `/turns/${encodeURIComponent(turnId)}` +
      `/models/${encodeURIComponent(configuredModelId)}/retry`,
    ),
    {
      method: "POST",
      headers: { ...headers(token), Accept: "text/event-stream" },
      body: JSON.stringify({ clientRequestId }),
    },
  ));
  await readModelSse(response, [configuredModelId], onEvent);
}

async function readModelSse(
  response: Response,
  configuredModelIds: string[],
  onEvent: (event: SseEventV2) => void,
): Promise<void> {
  const terminalModels = new Set<string>();
  await readJsonSse<SseEventV2>(response, (event) => {
    if (event.event === "model_complete" || event.event === "model_error") {
      terminalModels.add(event.configuredModelId);
    }
    onEvent(event);
  });

  for (const configuredModelId of configuredModelIds) {
    if (!terminalModels.has(configuredModelId)) {
      onEvent({
        event: "model_error",
        configuredModelId,
        modelId: "",
        error: "The response stream ended before this model completed.",
        responseId: "",
      });
    }
  }
}
