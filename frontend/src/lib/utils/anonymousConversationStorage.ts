export const ANONYMOUS_CONVERSATIONS_KEY = "octopus.anonymous-conversations.v1";
export const ANONYMOUS_CONVERSATION_SCHEMA_VERSION = 1;
export const MAX_ANONYMOUS_STORAGE_BYTES = 5 * 1024 * 1024;

export type AnonymousSyncStatus = "LOCAL_ONLY" | "SYNCED";

export interface AnonymousResponseSnapshot {
  configuredModelId: string;
  modelId: string;
  modelDisplayName: string;
  protocol: string;
  status: "STREAMING" | "COMPLETE" | "ERROR";
  responseText: string;
  reasoningText?: string;
  errorMessage?: string;
}

export interface AnonymousConversationTurn {
  id: string;
  clientRequestId: string;
  promptText: string;
  createdAt: string;
  responses: AnonymousResponseSnapshot[];
}

export interface AnonymousConversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  syncStatus: AnonymousSyncStatus;
  serverSessionId?: string;
  turns: AnonymousConversationTurn[];
}

export interface AnonymousConversationEnvelope {
  schemaVersion: 1;
  conversations: AnonymousConversation[];
}

export type StorageReadResult = {
  envelope: AnonymousConversationEnvelope;
  warning: string | null;
};

function storage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage;
  } catch {
    return null;
  }
}

function uuid(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (character) => {
      const random = Math.random() * 16 | 0;
      const value = character === "x" ? random : random & 0x3 | 0x8;
      return value.toString(16);
    });
}

export function emptyAnonymousEnvelope(): AnonymousConversationEnvelope {
  return { schemaVersion: ANONYMOUS_CONVERSATION_SCHEMA_VERSION, conversations: [] };
}

function isResponse(value: unknown): value is AnonymousResponseSnapshot {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return typeof item.configuredModelId === "string"
    && typeof item.modelId === "string"
    && typeof item.modelDisplayName === "string"
    && typeof item.protocol === "string"
    && (item.status === "STREAMING" || item.status === "COMPLETE" || item.status === "ERROR")
    && typeof item.responseText === "string";
}

function isConversation(value: unknown): value is AnonymousConversation {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return typeof item.id === "string"
    && typeof item.title === "string"
    && typeof item.createdAt === "string"
    && typeof item.updatedAt === "string"
    && (item.syncStatus === "LOCAL_ONLY" || item.syncStatus === "SYNCED")
    && Array.isArray(item.turns)
    && item.turns.every((turn) => {
      if (!turn || typeof turn !== "object") return false;
      const candidate = turn as Record<string, unknown>;
      return typeof candidate.id === "string"
        && typeof candidate.clientRequestId === "string"
        && typeof candidate.promptText === "string"
        && typeof candidate.createdAt === "string"
        && Array.isArray(candidate.responses)
        && candidate.responses.every(isResponse);
    });
}

function isEnvelope(value: unknown): value is AnonymousConversationEnvelope {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return item.schemaVersion === ANONYMOUS_CONVERSATION_SCHEMA_VERSION
    && Array.isArray(item.conversations)
    && item.conversations.every(isConversation);
}

export function readAnonymousConversations(): StorageReadResult {
  const empty = emptyAnonymousEnvelope();
  const store = storage();
  if (!store) return { envelope: empty, warning: "Browser storage is unavailable; this conversation will not survive refresh." };
  const raw = store.getItem(ANONYMOUS_CONVERSATIONS_KEY);
  if (!raw) return { envelope: empty, warning: null };
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!isEnvelope(parsed)) throw new Error("invalid envelope");
    return { envelope: parsed, warning: null };
  } catch {
    return { envelope: empty, warning: "Local conversation storage was unreadable. A fresh local copy was started." };
  }
}

export function writeAnonymousConversations(envelope: AnonymousConversationEnvelope): string | null {
  const store = storage();
  if (!store) return "Browser storage is unavailable; this conversation remains only in memory.";
  const serialized = JSON.stringify(envelope);
  if (new Blob([serialized]).size > MAX_ANONYMOUS_STORAGE_BYTES) {
    return "Local conversation storage is full. The conversation remains available until this page is closed.";
  }
  try {
    store.setItem(ANONYMOUS_CONVERSATIONS_KEY, serialized);
    return null;
  } catch {
    return "Local conversation storage is full. The conversation remains available until this page is closed.";
  }
}

export function createAnonymousConversation(promptText = ""): AnonymousConversation {
  const now = new Date().toISOString();
  return {
    id: uuid(),
    title: promptText.trim().slice(0, 60) || "New conversation",
    createdAt: now,
    updatedAt: now,
    syncStatus: "LOCAL_ONLY",
    turns: [],
  };
}

export function createAnonymousTurn(promptText: string): AnonymousConversationTurn {
  return {
    id: uuid(),
    clientRequestId: uuid(),
    promptText,
    createdAt: new Date().toISOString(),
    responses: [],
  };
}

/** Stable JSON used for server-side conflict detection; object keys are sorted recursively. */
export function canonicalAnonymousConversation(conversation: AnonymousConversation): string {
  return stableStringify({
    id: conversation.id,
    title: conversation.title,
    createdAt: conversation.createdAt,
    updatedAt: conversation.updatedAt,
    turns: conversation.turns,
  });
}

export async function anonymousConversationDigest(conversation: AnonymousConversation): Promise<string> {
  const bytes = new TextEncoder().encode(canonicalAnonymousConversation(conversation));
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function replaceAnonymousConversation(
  envelope: AnonymousConversationEnvelope,
  conversation: AnonymousConversation,
): AnonymousConversationEnvelope {
  return {
    ...envelope,
    conversations: [conversation, ...envelope.conversations.filter((item) => item.id !== conversation.id)],
  };
}

export function markAnonymousConversationSynced(
  envelope: AnonymousConversationEnvelope,
  id: string,
  serverSessionId: string,
): AnonymousConversationEnvelope {
  return {
    ...envelope,
    conversations: envelope.conversations.map((conversation) => conversation.id === id
      ? { ...conversation, syncStatus: "SYNCED", serverSessionId, updatedAt: new Date().toISOString() }
      : conversation),
  };
}

export function removeAnonymousConversation(
  envelope: AnonymousConversationEnvelope,
  id: string,
): AnonymousConversationEnvelope {
  return { ...envelope, conversations: envelope.conversations.filter((conversation) => conversation.id !== id) };
}

function stableStringify(value: unknown): string {
  if (value === null || typeof value !== "object") return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  const entries = Object.entries(value as Record<string, unknown>)
    .filter(([, entry]) => entry !== undefined)
    .sort(([a], [b]) => a.localeCompare(b));
  return `{${entries.map(([key, entry]) => `${JSON.stringify(key)}:${stableStringify(entry)}`).join(",")}}`;
}
