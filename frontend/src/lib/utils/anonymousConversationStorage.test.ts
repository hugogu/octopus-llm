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

  it("returns a warning instead of throwing when storage is unavailable", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new DOMException("quota", "QuotaExceededError"); });
    expect(writeAnonymousConversations(emptyAnonymousEnvelope())).toContain("full");
  });
});
