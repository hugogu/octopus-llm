"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { MessageSquare, Plus, Trash2 } from "lucide-react";
import ChatInput from "@/components/chat/ChatInput";
import MarkdownRenderer from "@/components/chat/MarkdownRenderer";
import AnonymousChatNotice from "@/components/chat/AnonymousChatNotice";
import { listAllAnonymousModels, streamAnonymousTurn } from "@/lib/api/anonymousChat";
import { useAnonymousConversations } from "@/lib/hooks/useAnonymousConversations";
import type { AnonymousModelV2, AnonymousSseEvent } from "@/lib/types/api";

const SELECTED_MODELS_STORAGE_KEY = "octopus:selected-anonymous-model-ids";

export default function AnonymousChatPage() {
  const [models, setModels] = useState<AnonymousModelV2[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const {
    conversations,
    activeConversation,
    activeId,
    storageWarning,
    createConversation,
    selectConversation,
    deleteConversation,
    addTurn,
    startTurn,
    applyEvent,
  } = useAnonymousConversations();

  useEffect(() => {
    listAllAnonymousModels()
      .then(setModels)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Unable to load public models."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (models.length === 0) return;
    const allowed = new Set(models.map((model) => model.id));
    queueMicrotask(() => {
      try {
        const stored = JSON.parse(window.localStorage.getItem(SELECTED_MODELS_STORAGE_KEY) ?? "null") as unknown;
        if (Array.isArray(stored)) {
          const restored = stored.filter((id): id is string => typeof id === "string" && allowed.has(id));
          if (restored.length > 0) {
            setSelectedIds(restored);
            return;
          }
        }
      } catch {
        // A malformed selection is harmless; use the first public models.
      }
      setSelectedIds(models.slice(0, 3).map((model) => model.id));
    });
  }, [models]);

  useEffect(() => {
    if (selectedIds.length === 0) return;
    try {
      window.localStorage.setItem(SELECTED_MODELS_STORAGE_KEY, JSON.stringify(selectedIds));
    } catch {
      // Conversation storage surfaces the durable-storage warning separately.
    }
  }, [selectedIds]);

  const modelsById = useMemo(() => Object.fromEntries(models.map((model) => [model.id, model])), [models]);
  const selectedModels = selectedIds.map((id) => modelsById[id]).filter((model): model is AnonymousModelV2 => Boolean(model));

  const toggleModel = useCallback((id: string) => {
    setSelectedIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  }, []);

  const send = useCallback(async (promptText: string) => {
    if (selectedModels.length === 0 || streaming) return;
    setError(null);
    const priorConversation = activeConversation;
    const started = priorConversation ? null : startTurn(promptText, selectedModels);
    const localTurn = priorConversation
      ? addTurn(priorConversation.id, promptText, selectedModels)
      : started?.turn;
    const conversationId = priorConversation?.id ?? started?.conversation.id;
    if (!localTurn || !conversationId) {
      setError("Unable to create a local conversation.");
      return;
    }
    const history = (priorConversation?.turns ?? []).flatMap((turn) => [
      { role: "USER" as const, content: turn.promptText },
      ...turn.responses
        .filter((response) => response.status === "COMPLETE" && response.responseText)
        .map((response) => ({ role: "ASSISTANT" as const, content: response.responseText })),
    ]);
    setStreaming(true);
    try {
      await streamAnonymousTurn(
        {
          clientConversationId: conversationId,
          clientRequestId: localTurn.clientRequestId,
          promptText,
          selectedConfiguredModelIds: selectedModels.map((model) => model.id),
          history,
        },
        (event: AnonymousSseEvent) => applyEvent(conversationId, localTurn.id, event),
      );
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : "Anonymous chat failed.");
      selectedModels.forEach((model) => applyEvent(conversationId, localTurn.id, {
        event: "model_error",
        configuredModelId: model.id,
        status: "ERROR",
        errorCode: "REQUEST_FAILED",
        errorMessage: "This anonymous request could not be completed.",
      }));
    } finally {
      setStreaming(false);
    }
  }, [activeConversation, addTurn, applyEvent, selectedModels, startTurn, streaming]);

  return (
    <div className="flex h-screen max-h-screen bg-[#faf9f5]" data-testid="anonymous-chat">
      <aside className="hidden w-64 shrink-0 flex-col border-r border-stone-200 bg-[#f5f4ee] sm:flex">
        <button type="button" onClick={() => createConversation()} className="m-3 inline-flex items-center justify-center rounded-lg bg-[#c96442] px-3 py-2 text-sm font-medium text-white">
          <Plus className="mr-2 h-4 w-4" /> New conversation
        </button>
        <div className="flex-1 overflow-y-auto p-2">
          {conversations.map((conversation) => (
            <div key={conversation.id} className={`group mb-1 flex items-center gap-2 rounded-lg p-2.5 ${conversation.id === activeId ? "border border-stone-200 bg-white" : "hover:bg-white/70"}`}>
              <button type="button" onClick={() => selectConversation(conversation.id)} className="flex min-w-0 flex-1 items-center gap-2 text-left">
                <MessageSquare className="h-4 w-4 shrink-0 text-stone-400" />
                <span className="truncate text-sm text-stone-800">{conversation.title}</span>
              </button>
              <button type="button" onClick={() => deleteConversation(conversation.id)} aria-label="Delete local conversation" className="rounded p-1 text-stone-400 opacity-0 hover:bg-red-50 hover:text-red-600 group-hover:opacity-100">
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}
        </div>
        <div className="border-t border-stone-200 p-3 text-xs text-stone-500">Local-only history · not shareable</div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-b border-stone-200 px-4 py-3 sm:px-6">
          <div className="mx-auto flex max-w-5xl flex-col gap-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h1 className="text-base font-semibold text-stone-900">{activeConversation?.title ?? "Anonymous conversation"}</h1>
                <p className="text-xs text-stone-500">Try approved models without signing in</p>
              </div>
              <span className="rounded-full bg-stone-200 px-2.5 py-1 text-xs text-stone-600">Guest mode</span>
            </div>
            <div className="flex flex-wrap gap-2" aria-label="Public models">
              {loading ? <span className="text-sm text-stone-500">Loading public models…</span> : models.map((model) => (
                <label key={model.id} className={`inline-flex cursor-pointer items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs ${selectedIds.includes(model.id) ? "border-[#c96442] bg-[#fff5ef] text-[#9d452d]" : "border-stone-200 bg-white text-stone-600"}`}>
                  <input type="checkbox" checked={selectedIds.includes(model.id)} onChange={() => toggleModel(model.id)} className="accent-[#c96442]" />
                  {model.displayName}
                </label>
              ))}
            </div>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto px-4 py-5 sm:px-6">
          <div className="mx-auto max-w-5xl space-y-4">
            <AnonymousChatNotice storageWarning={storageWarning} />
            {error ? <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div> : null}
            {activeConversation?.turns.map((turn) => (
              <section key={turn.id} className="space-y-3">
                <div className="ml-auto w-fit max-w-3xl rounded-2xl bg-[#30302e] px-4 py-3 text-sm text-white">{turn.promptText}</div>
                <div className="grid gap-3 lg:grid-cols-2">
                  {turn.responses.map((response) => (
                    <article key={`${turn.id}:${response.configuredModelId}`} className="rounded-2xl border border-stone-200 bg-white p-4 shadow-sm">
                      <div className="mb-2 flex items-center justify-between gap-2">
                        <h2 className="text-sm font-semibold text-stone-800">{response.modelDisplayName}</h2>
                        <span className={`text-xs ${response.status === "ERROR" ? "text-red-600" : response.status === "STREAMING" ? "text-amber-600" : "text-stone-400"}`}>{response.status.toLowerCase()}</span>
                      </div>
                      {response.reasoningText ? <p className="mb-2 whitespace-pre-wrap text-xs text-stone-500">{response.reasoningText}</p> : null}
                      <MarkdownRenderer content={response.responseText || (response.status === "STREAMING" ? "…" : "")} className="text-sm" />
                      {response.errorMessage ? <p className="mt-2 text-xs text-red-600">{response.errorMessage}</p> : null}
                    </article>
                  ))}
                </div>
              </section>
            ))}
            {!activeConversation?.turns.length ? <div className="flex min-h-[42vh] items-center justify-center rounded-3xl border border-dashed border-stone-300 bg-white/70 text-sm text-stone-500">Select a public model and ask a question to begin.</div> : null}
          </div>
        </main>
        <div className="border-t border-stone-200 bg-[#faf9f5] px-4 py-3 sm:px-6">
          <ChatInput onSubmit={(prompt) => void send(prompt)} disabled={streaming || selectedModels.length === 0 || loading} supportsAttachments={false} supportsAudio={false} />
        </div>
      </div>
    </div>
  );
}
