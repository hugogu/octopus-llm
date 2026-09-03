"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { AnonymousModelV2, AnonymousSseEvent } from "@/lib/types/api";
import {
  ANONYMOUS_CONVERSATIONS_KEY,
  createAnonymousConversation,
  createAnonymousTurn,
  emptyAnonymousEnvelope,
  markAnonymousConversationSynced,
  readAnonymousConversations,
  replaceAnonymousConversation,
  type AnonymousConversation,
  type AnonymousConversationEnvelope,
  type AnonymousConversationTurn,
  type AnonymousResponseSnapshot,
  writeAnonymousConversations,
} from "@/lib/utils/anonymousConversationStorage";

export function useAnonymousConversations() {
  const [envelope, setEnvelope] = useState<AnonymousConversationEnvelope>(emptyAnonymousEnvelope);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [storageWarning, setStorageWarning] = useState<string | null>(null);
  const envelopeRef = useRef(envelope);

  useEffect(() => {
    queueMicrotask(() => {
      const result = readAnonymousConversations();
      envelopeRef.current = result.envelope;
      setEnvelope(result.envelope);
      setStorageWarning(result.warning);
      setActiveId(result.envelope.conversations[0]?.id ?? null);
    });
  }, []);

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key !== ANONYMOUS_CONVERSATIONS_KEY) return;
      const result = readAnonymousConversations();
      envelopeRef.current = result.envelope;
      setEnvelope(result.envelope);
      setStorageWarning(result.warning);
      setActiveId((current) => current && result.envelope.conversations.some((item) => item.id === current)
        ? current
        : result.envelope.conversations[0]?.id ?? null);
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const persist = useCallback((next: AnonymousConversationEnvelope) => {
    envelopeRef.current = next;
    setEnvelope(next);
    setStorageWarning(writeAnonymousConversations(next));
  }, []);

  const activeConversation = useMemo(
    () => envelope.conversations.find((conversation) => conversation.id === activeId) ?? null,
    [activeId, envelope.conversations],
  );

  const createConversation = useCallback(() => {
    const conversation = createAnonymousConversation();
    persist(replaceAnonymousConversation(envelopeRef.current, conversation));
    setActiveId(conversation.id);
    return conversation;
  }, [persist]);

  const selectConversation = useCallback((id: string) => setActiveId(id), []);

  const deleteConversation = useCallback((id: string) => {
    const current = envelopeRef.current;
    persist({
      ...current,
      conversations: current.conversations.filter((conversation) => conversation.id !== id),
    });
    setActiveId((current) => current === id ? null : current);
  }, [persist]);

  const addTurn = useCallback((conversationId: string, promptText: string, models: AnonymousModelV2[]) => {
    const current = envelopeRef.current.conversations.find((conversation) => conversation.id === conversationId);
    if (!current) return null;
    const turn = createAnonymousTurn(promptText);
    const responseByModel = models.map<AnonymousResponseSnapshot>((model) => ({
      configuredModelId: model.id,
      modelId: model.modelId,
      modelDisplayName: model.displayName,
      protocol: model.protocol,
      status: "STREAMING",
      responseText: "",
    }));
    const withTurn: AnonymousConversation = {
      ...current,
      title: current.turns.length === 0 ? promptText.trim().slice(0, 60) || current.title : current.title,
      updatedAt: new Date().toISOString(),
      turns: [...current.turns, { ...turn, responses: responseByModel }],
    };
    persist(replaceAnonymousConversation(envelopeRef.current, withTurn));
    return { ...turn, responses: responseByModel };
  }, [persist]);

  const startTurn = useCallback((promptText: string, models: AnonymousModelV2[]) => {
    const conversation = createAnonymousConversation(promptText);
    const turn = createAnonymousTurn(promptText);
    const responses = models.map<AnonymousResponseSnapshot>((model) => ({
      configuredModelId: model.id,
      modelId: model.modelId,
      modelDisplayName: model.displayName,
      protocol: model.protocol,
      status: "STREAMING",
      responseText: "",
    }));
    const withTurn = { ...conversation, turns: [{ ...turn, responses }] };
    persist(replaceAnonymousConversation(envelopeRef.current, withTurn));
    setActiveId(conversation.id);
    return { conversation: withTurn, turn: { ...turn, responses } };
  }, [persist]);

  const applyEvent = useCallback((conversationId: string, turnId: string, event: AnonymousSseEvent) => {
    const current = envelopeRef.current.conversations.find((conversation) => conversation.id === conversationId);
    if (!current || event.event === "status" || event.event === "result" || event.event === "error") return;
    const nextTurns = current.turns.map((turn): AnonymousConversationTurn => {
      if (turn.id !== turnId || !("configuredModelId" in event)) return turn;
      const responseId = event.configuredModelId;
      const responses = turn.responses.map((response) => {
        if (response.configuredModelId !== responseId) return response;
        if (event.event === "token") return { ...response, responseText: response.responseText + event.text };
        if (event.event === "reasoning") return { ...response, reasoningText: (response.reasoningText ?? "") + event.text };
        if (event.event === "model_complete") return { ...response, status: "COMPLETE" as const };
        if (event.event === "model_error") return { ...response, status: "ERROR" as const, errorMessage: event.errorMessage };
        return response;
      });
      return { ...turn, responses };
    });
    persist(replaceAnonymousConversation(envelopeRef.current, { ...current, updatedAt: new Date().toISOString(), turns: nextTurns }));
  }, [persist]);

  const markSynced = useCallback((id: string, serverSessionId: string) => {
    persist(markAnonymousConversationSynced(envelopeRef.current, id, serverSessionId));
  }, [persist]);

  return {
    conversations: envelope.conversations,
    activeConversation,
    activeId,
    storageWarning,
    createConversation,
    selectConversation,
    deleteConversation,
    addTurn,
    startTurn,
    applyEvent,
    markSynced,
  };
}
