"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import ChatInput from "@/components/chat/ChatInput";
import ChatWorkspace from "@/components/chat/ChatWorkspace";
import AnonymousChatNotice from "@/components/chat/AnonymousChatNotice";
import MarkdownRenderer from "@/components/chat/MarkdownRenderer";
import ModelSelectorPanel, { type ModelSelectorModel } from "@/components/chat/ModelSelectorPanel";
import ResponseGroup, { type ResponsePanelData } from "@/components/chat/ResponseGroup";
import SessionSidebar from "@/components/chat/SessionSidebar";
import { listAllAnonymousModels, streamAnonymousTurn } from "@/lib/api/anonymousChat";
import { useAnonymousConversations } from "@/lib/hooks/useAnonymousConversations";
import type {
  AnonymousModelV2,
  AnonymousSseEvent,
  CapabilityMatrix,
} from "@/lib/types/api";
import type {
  AnonymousConversation,
  AnonymousConversationTurn,
} from "@/lib/utils/anonymousConversationStorage";

const SELECTED_MODELS_STORAGE_KEY = "octopus:selected-anonymous-model-ids";

function capabilityMatrix(model: AnonymousModelV2): CapabilityMatrix {
  return {
    input_modalities: model.capabilities.vision ? ["text", "image"] : ["text"],
    output_modalities: ["text"],
    context_length_tokens: null,
    supports_streaming: model.capabilities.streaming,
    supports_function_calling: false,
    supports_system_prompt: true,
    supports_video_input: false,
  };
}

function selectorModel(model: AnonymousModelV2): ModelSelectorModel {
  return {
    id: model.id,
    displayName: model.displayName,
    isEnabled: true,
    builtin: true,
    connectionLabel: null,
    protocol: model.protocol,
    capabilityMatrix: capabilityMatrix(model),
  };
}

function responseStatus(status: AnonymousConversationTurn["responses"][number]["status"]): ResponsePanelData["status"] {
  return status.toLowerCase() as ResponsePanelData["status"];
}

function localSession(conversation: AnonymousConversation) {
  return {
    id: conversation.id,
    title: conversation.title,
    createdAt: conversation.createdAt,
    updatedAt: conversation.updatedAt,
  };
}

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

  const selectorModels = useMemo(() => models.map(selectorModel), [models]);
  const modelsById = useMemo(() => Object.fromEntries(models.map((model) => [model.id, model])), [models]);
  const selectorModelsById = useMemo(
    () => Object.fromEntries(selectorModels.map((model) => [model.id, model])),
    [selectorModels],
  );
  const selectedModels = selectedIds.map((id) => modelsById[id]).filter((model): model is AnonymousModelV2 => Boolean(model));
  const localSessions = useMemo(() => conversations.map(localSession), [conversations]);

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

  const renderTurn = useCallback((turn: AnonymousConversationTurn) => (
    <section key={turn.id} className="space-y-3">
      <div className="ml-auto w-fit max-w-3xl rounded-2xl bg-[#30302e] px-4 py-3 text-white shadow-sm">
        <MarkdownRenderer content={turn.promptText} className="text-sm [&_p]:mb-0 [&_*]:text-white" />
      </div>
      <ResponseGroup
        panels={turn.responses.map((response): ResponsePanelData => ({
          key: `${turn.id}:${response.configuredModelId}`,
          modelId: response.modelId,
          displayName: response.modelDisplayName,
          text: response.responseText,
          reasoning: response.reasoningText ?? "",
          status: responseStatus(response.status),
          errorMessage: response.errorMessage,
          capabilityMatrix: selectorModelsById[response.configuredModelId]?.capabilityMatrix,
        }))}
      />
    </section>
  ), [selectorModelsById]);

  return (
    <ChatWorkspace
      sidebar={(
        <SessionSidebar
          sessions={localSessions}
          currentSessionId={activeId}
          onSelectSession={selectConversation}
          onDeleteSession={deleteConversation}
          onNewSession={() => void createConversation()}
          variant="anonymous"
        />
      )}
      title={activeConversation?.title ?? "New conversation"}
      subtitle="Compare approved models without signing in"
      actions={(
        <>
          <span className="rounded-full bg-stone-200 px-2.5 py-1 text-xs text-stone-600">Guest mode</span>
          <ModelSelectorPanel
            models={selectorModels}
            selectedIds={selectedIds}
            onChange={setSelectedIds}
            manageHref={null}
            emptyMessage="No public models are available right now."
          />
          <Link href="/register?returnTo=%2Fchat" className="rounded-lg border border-[#c96442] px-3 py-1.5 text-xs font-medium text-[#a04a32] transition hover:bg-[#fff5ef]">
            Create account
          </Link>
        </>
      )}
      composer={(
        <ChatInput
          onSubmit={(promptText) => void send(promptText)}
          disabled={streaming || selectedModels.length === 0 || loading}
          supportsAttachments={false}
          supportsAudio={false}
        />
      )}
      testId="anonymous-chat"
    >
      <AnonymousChatNotice storageWarning={storageWarning} />
      {error ? <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div> : null}
      {loading ? (
        <div className="space-y-4">
          <div className="ml-auto h-24 max-w-3xl animate-pulse rounded-2xl bg-stone-200" />
          <div className="h-52 animate-pulse rounded-2xl bg-stone-200" />
        </div>
      ) : activeConversation?.turns.length ? (
        activeConversation.turns.map(renderTurn)
      ) : (
        <div className="flex min-h-[50vh] items-center justify-center rounded-3xl border border-dashed border-stone-300 bg-white/70 px-8 text-center text-sm text-stone-500">
          {models.length > 0 ? "Select a public model and ask a question to begin." : "No public models are available right now."}
        </div>
      )}
    </ChatWorkspace>
  );
}
