import { apiUrl } from "@/lib/api/base";
import {
  anonymousConversationDigest,
  readAnonymousConversations,
  removeAnonymousConversation,
  writeAnonymousConversations,
  type AnonymousConversation,
} from "@/lib/utils/anonymousConversationStorage";

interface SyncResponse {
  items: Array<{
    sourceConversationId: string;
    status: "IMPORTED" | "ALREADY_IMPORTED" | "SKIPPED" | "FAILED";
    sessionId: string | null;
    reasonCode: string | null;
    message: string;
  }>;
}

function toRequest(conversation: AnonymousConversation, sourceDigest: string) {
  return {
    sourceConversationId: conversation.id,
    sourceDigest,
    title: conversation.title,
    createdAt: conversation.createdAt,
    updatedAt: conversation.updatedAt,
    turns: conversation.turns.map((turn) => ({
      sourceTurnId: turn.id,
      clientRequestId: turn.clientRequestId,
      promptText: turn.promptText,
      createdAt: turn.createdAt,
      responses: turn.responses.map((response) => ({
        configuredModelId: response.configuredModelId,
        modelId: response.modelId,
        modelDisplayName: response.modelDisplayName,
        protocol: response.protocol,
        status: response.status,
        responseText: response.responseText,
        ...(response.reasoningText ? { reasoningText: response.reasoningText } : {}),
        ...(response.errorMessage ? { errorMessage: response.errorMessage } : {}),
      })),
    })),
  };
}

export async function syncAnonymousConversations(token: string): Promise<SyncResponse> {
  const current = readAnonymousConversations();
  const conversations = current.envelope.conversations.filter((conversation) => conversation.syncStatus === "LOCAL_ONLY");
  if (conversations.length === 0) return { items: [] };
  const payload = await Promise.all(conversations.map(async (conversation) => toRequest(conversation, await anonymousConversationDigest(conversation))));
  const response = await fetch(apiUrl("/api/v2/anonymous/conversations/sync"), {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ conversations: payload }),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText })) as { message?: string };
    throw Object.assign(new Error(error.message ?? "Conversation synchronization failed."), { status: response.status });
  }
  const result = await response.json() as SyncResponse;
  let envelope = current.envelope;
  for (const item of result.items) {
    if ((item.status === "IMPORTED" || item.status === "ALREADY_IMPORTED") && item.sessionId) {
      envelope = removeAnonymousConversation(envelope, item.sourceConversationId);
    }
  }
  writeAnonymousConversations(envelope);
  return result;
}
