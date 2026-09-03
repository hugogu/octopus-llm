import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  anonymousConversationDigest,
  canonicalAnonymousConversation,
  createAnonymousConversation,
  createAnonymousTurn,
  emptyAnonymousEnvelope,
  readAnonymousConversations,
  replaceAnonymousConversation,
  writeAnonymousConversations,
  type AnonymousConversation,
} from "./anonymousConversationStorage";

describe("anonymous conversation storage", () => {
  beforeEach(() => window.localStorage.clear());

  it("round-trips a versioned envelope and recovers from corruption", () => {
    const conversation = createAnonymousConversation("Hello");
    conversation.turns.push(createAnonymousTurn("Hello"));
    const envelope = replaceAnonymousConversation(emptyAnonymousEnvelope(), conversation);
    expect(writeAnonymousConversations(envelope)).toBeNull();
    expect(readAnonymousConversations().envelope.conversations[0]?.id).toBe(conversation.id);
    window.localStorage.setItem("octopus.anonymous-conversations.v1", "not-json");
    expect(readAnonymousConversations().envelope.conversations).toEqual([]);
    expect(readAnonymousConversations().warning).toContain("unreadable");
  });

  it("canonicalizes object key order for stable conflict digests", async () => {
    const conversation = createAnonymousConversation("Hello");
    const reordered = { ...conversation, turns: [...conversation.turns] };
    expect(canonicalAnonymousConversation(conversation)).toBe(canonicalAnonymousConversation(reordered));
    expect(await anonymousConversationDigest(conversation)).toMatch(/^[0-9a-f]{64}$/);
  });

  it("matches the backend digest compatibility vector", async () => {
    const conversation: AnonymousConversation = {
      id: "00000000-0000-0000-0000-000000000001",
      title: "Local",
      createdAt: "2026-09-02T00:00:00.000Z",
      updatedAt: "2026-09-02T00:01:00.000Z",
      syncStatus: "LOCAL_ONLY",
      turns: [{
        id: "00000000-0000-0000-0000-000000000002",
        clientRequestId: "00000000-0000-0000-0000-000000000003",
        promptText: "hello",
        createdAt: "2026-09-02T00:00:00.000Z",
        responses: [{
          configuredModelId: "00000000-0000-0000-0000-000000000004",
          modelId: "model",
          modelDisplayName: "Model",
          protocol: "openai-compatible",
          status: "COMPLETE",
          responseText: "world",
        }],
      }],
    };

    await expect(anonymousConversationDigest(conversation)).resolves.toBe(
      "3d3f4fe8e161ae3502d1e7dc75a471b1ab59e9ff59039d8f583a427e80236845",
    );
  });

  it("returns a warning instead of throwing when storage is unavailable", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new DOMException("quota", "QuotaExceededError"); });
    expect(writeAnonymousConversations(emptyAnonymousEnvelope())).toContain("full");
  });
});
