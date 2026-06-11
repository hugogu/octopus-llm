"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import type {
  Attachment,
  ChatTurn,
  GetSessionResponse,
  ModelDefinition,
  UserModelConfig,
  ApiKeyMeta,
  SseEvent,
} from "@/lib/types/api";
import { createSession, getSession, streamTurn } from "@/lib/api/chat";
import { listModels } from "@/lib/api/models";
import { listModelConfigs, listApiKeys } from "@/lib/api/userConfig";
import { getToken } from "@/lib/api/auth";
import ModelSelectorPanel from "@/components/chat/ModelSelectorPanel";
import ChatInput from "@/components/chat/ChatInput";
import ModelResponsePanel from "@/components/chat/ModelResponsePanel";
import MarkdownRenderer from "@/components/chat/MarkdownRenderer";
import SessionSidebar from "@/components/chat/SessionSidebar";
import { useParallelStream } from "@/components/chat/ParallelResponseGrid";
import { usePreferences } from "@/lib/hooks/usePreferences";
import { useSessions } from "@/lib/hooks/useSessions";

const SELECTED_MODELS_STORAGE_KEY = "octopus:selected-model-ids";

interface DraftTurnState {
  promptText: string;
  selectedModelIds: string[];
}

export default function ChatPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const querySessionId = searchParams.get("session");
  const [models, setModels] = useState<ModelDefinition[]>([]);
  const [configs, setConfigs] = useState<UserModelConfig[]>([]);
  const [apiKeys, setApiKeys] = useState<ApiKeyMeta[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [activeSession, setActiveSession] = useState<GetSessionResponse | null>(null);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [draftTurn, setDraftTurn] = useState<DraftTurnState | null>(null);
  const initializedSelectionRef = useRef(false);
  const { models: panelStates, streaming, reset, handleEvent } = useParallelStream();
  const { preferences, setLastSelectedModel } = usePreferences();
  const { sessions, loading: sessionsLoading, loadSessions, removeSession } = useSessions();

  const loadData = useCallback(async () => {
    const token = getToken();
    if (!token) return;
    try {
      const [{ models: ms }, { modelConfigs }, { apiKeys: keys }] = await Promise.all([
        listModels(),
        listModelConfigs(token),
        listApiKeys(token),
      ]);

      setModels(ms);
      setConfigs(modelConfigs);
      setApiKeys(keys);
    } catch (err) {
      console.error(err);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (initializedSelectionRef.current) return;

    const enabled = configs.filter((c) => c.isEnabled).map((c) => c.modelId);
    if (enabled.length === 0) return;

    const storedSelectionRaw = window.localStorage.getItem(SELECTED_MODELS_STORAGE_KEY);
    if (storedSelectionRaw !== null) {
      try {
        const storedSelection = JSON.parse(storedSelectionRaw);
        if (Array.isArray(storedSelection)) {
          setSelectedIds(storedSelection.filter((id): id is string => (
            typeof id === "string" && enabled.includes(id)
          )));
          initializedSelectionRef.current = true;
          return;
        }
      } catch {
        window.localStorage.removeItem(SELECTED_MODELS_STORAGE_KEY);
      }
    }

    const lastModelId = preferences?.lastSelectedModelId;
    if (lastModelId && enabled.includes(lastModelId)) {
      setSelectedIds([lastModelId]);
    } else {
      setSelectedIds(enabled.slice(0, 3));
    }
    initializedSelectionRef.current = true;
  }, [configs, preferences]);

  useEffect(() => {
    if (!initializedSelectionRef.current) return;

    const enabled = new Set(configs.filter((c) => c.isEnabled).map((c) => c.modelId));
    setSelectedIds((current) => {
      const filtered = current.filter((id) => enabled.has(id));
      return filtered.length === current.length ? current : filtered;
    });
  }, [configs]);

  useEffect(() => {
    if (!initializedSelectionRef.current) return;
    window.localStorage.setItem(SELECTED_MODELS_STORAGE_KEY, JSON.stringify(selectedIds));
  }, [selectedIds]);

  const loadSessionData = useCallback(async (sid: string) => {
    const token = getToken();
    if (!token) return;

    setSessionLoading(true);
    try {
      const session = await getSession(sid, token);
      setActiveSession(session);

      const enabledSet = new Set(configs.filter((config) => config.isEnabled).map((config) => config.modelId));
      const latestSelection = session.turns.at(-1)?.selectedModelIds.filter((id) => enabledSet.has(id)) ?? [];
      if (latestSelection.length > 0) {
        setSelectedIds(latestSelection);
      }
    } catch (err) {
      console.error("Failed to load session:", err);
    } finally {
      setSessionLoading(false);
    }
  }, [configs]);

  useEffect(() => {
    if (!querySessionId) {
      setSessionId(null);
      setActiveSession(null);
      setDraftTurn(null);
      setSessionLoading(false);
      reset([]);
      return;
    }

    setSessionId(querySessionId);
    void loadSessionData(querySessionId);
  }, [querySessionId, loadSessionData, reset]);

  const displayNames = Object.fromEntries(
    models.map((m) => [m.id, m.displayName]),
  );
  const capabilityMatrices = Object.fromEntries(
    models.map((m) => [m.id, m.capabilityMatrix]),
  );

  const supportsAttachments = selectedIds.some((id) => {
    const model = models.find((m) => m.id === id);
    const caps = model?.capabilityMatrix;
    return caps?.input_modalities.some((mod) => mod === "image" || mod === "video");
  });

  const currentSessionMeta = sessionId
    ? sessions.find((session) => session.id === sessionId)
    : null;

  const handleNewSession = useCallback(() => {
    setSessionId(null);
    setActiveSession(null);
    setDraftTurn(null);
    reset([]);
    router.replace("/chat");
  }, [reset, router]);

  const handleSelectSession = useCallback((sid: string) => {
    setDraftTurn(null);
    reset([]);
    router.replace(`/chat?session=${encodeURIComponent(sid)}`);
  }, [reset, router]);

  const handleDeleteSession = useCallback(async (sid: string) => {
    await removeSession(sid);
    if (sid === sessionId) {
      handleNewSession();
    }
  }, [removeSession, sessionId, handleNewSession]);

  const handleSubmit = useCallback(async (promptText: string, attachments: Attachment[]) => {
    if (selectedIds.length === 0) return;
    reset(selectedIds);
    setDraftTurn({ promptText, selectedModelIds: selectedIds });

    try {
      let sid = sessionId;
      if (!sid) {
        const session = await createSession({ selectedModelId: selectedIds[0] });
        sid = session.id;
        setSessionId(sid);
        setActiveSession({ id: sid, title: session.title, turns: [] });
        router.replace(`/chat?session=${encodeURIComponent(sid)}`);
        await loadSessions();
      }

      await streamTurn(
        sid,
        { promptText, selectedModelIds: selectedIds, attachments },
        (event: SseEvent) => handleEvent(event),
      );

      const token = getToken();
      if (token) {
        const session = await getSession(sid, token);
        setActiveSession(session);
      }
      setDraftTurn(null);
      await loadSessions();
    } catch (err) {
      console.error("Stream error:", err);
    }
  }, [selectedIds, sessionId, reset, handleEvent, loadSessions, router]);

  const renderTurn = useCallback((turn: ChatTurn) => (
    <section key={turn.id} className="space-y-3">
      <div className="ml-auto max-w-3xl rounded-2xl bg-gray-900 px-4 py-3 text-white shadow-sm">
        <p className="mb-1 text-xs font-semibold uppercase tracking-[0.18em] text-gray-300">You</p>
        <MarkdownRenderer content={turn.promptText} className="text-sm [&_p]:mb-0 [&_*]:text-white" />
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        {turn.responses.map((resp) => (
          <ModelResponsePanel
            key={`${turn.id}:${resp.modelId}`}
            modelId={resp.modelId}
            displayName={displayNames[resp.modelId] ?? resp.modelId}
            text={resp.responseText ?? ""}
            status={resp.status === "complete" ? "complete" : "error"}
            errorMessage={resp.errorMessage ?? undefined}
            inputTokens={resp.inputTokens ?? undefined}
            outputTokens={resp.outputTokens ?? undefined}
            latencyMs={resp.latencyMs}
            capabilityMatrix={capabilityMatrices[resp.modelId]}
          />
        ))}
      </div>
    </section>
  ), [capabilityMatrices, displayNames]);

  const hasConversation = (activeSession?.turns.length ?? 0) > 0 || draftTurn !== null;

  return (
    <div className="flex h-screen max-h-screen">
      <SessionSidebar
        sessions={sessions}
        currentSessionId={sessionId}
        onSelectSession={handleSelectSession}
        onDeleteSession={handleDeleteSession}
        onNewSession={handleNewSession}
        loading={sessionsLoading}
      />

      <div className="flex-1 flex flex-col min-w-0">
        <header className="border-b bg-white px-4 py-3">
          <div className="flex items-center justify-between gap-4">
            <div>
              <h1 className="font-bold text-lg text-gray-950">Octopus LLM</h1>
              <p className="text-sm text-gray-500">
                {currentSessionMeta?.title || activeSession?.title || (sessionId ? "Conversation loaded" : "Compare selected models in one thread")}
              </p>
            </div>
            {sessionId ? (
              <span className="rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700">
                Continuing current conversation
              </span>
            ) : null}
          </div>
        </header>

        <div className="border-b bg-gray-50/80 px-4 py-3">
          <ModelSelectorPanel
            models={models}
            configs={configs}
            apiKeys={apiKeys}
            selectedIds={selectedIds}
            onChange={(ids) => {
              setSelectedIds(ids);
              void setLastSelectedModel(ids[0] ?? null).catch((err) => {
                console.error("Failed to save model preference:", err);
              });
            }}
          />
        </div>

        <div className="flex-1 overflow-y-auto bg-gradient-to-b from-gray-50 to-white px-4">
          <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 py-6">
            {sessionLoading ? (
              <div className="space-y-4">
                <div className="ml-auto h-24 max-w-3xl animate-pulse rounded-2xl bg-gray-200" />
                <div className="grid gap-3 xl:grid-cols-2">
                  <div className="h-52 animate-pulse rounded-2xl bg-gray-200" />
                  <div className="h-52 animate-pulse rounded-2xl bg-gray-200" />
                </div>
              </div>
            ) : hasConversation ? (
              <>
                {activeSession?.turns.map(renderTurn)}

                {draftTurn ? (
                  <section className="space-y-3">
                    <div className="ml-auto max-w-3xl rounded-2xl bg-gray-900 px-4 py-3 text-white shadow-sm">
                      <p className="mb-1 text-xs font-semibold uppercase tracking-[0.18em] text-gray-300">You</p>
                      <MarkdownRenderer content={draftTurn.promptText} className="text-sm [&_p]:mb-0 [&_*]:text-white" />
                    </div>

                    <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
                      {draftTurn.selectedModelIds.map((id) => {
                        const state = panelStates[id];
                        return (
                          <ModelResponsePanel
                            key={`draft:${id}`}
                            modelId={id}
                            displayName={displayNames[id] ?? id}
                            text={state?.text ?? ""}
                            status={state?.status ?? "idle"}
                            errorMessage={state?.errorMessage}
                            inputTokens={state?.inputTokens}
                            outputTokens={state?.outputTokens}
                            latencyMs={state?.latencyMs}
                            capabilityNotice={state?.capabilityNotice}
                            capabilityMatrix={capabilityMatrices[id]}
                          />
                        );
                      })}
                    </div>
                  </section>
                ) : null}
              </>
            ) : selectedIds.length === 0 ? (
              <div className="flex min-h-[50vh] items-center justify-center rounded-3xl border border-dashed border-gray-300 bg-white/80 px-8 text-center text-sm text-gray-500">
                Pick at least one model, then start a conversation.
              </div>
            ) : (
              <div className="flex min-h-[50vh] items-center justify-center rounded-3xl border border-dashed border-gray-300 bg-white/80 px-8 text-center">
                <div className="max-w-xl space-y-3">
                  <p className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-400">
                    Ready to chat
                  </p>
                  <MarkdownRenderer
                    content={`Selected models: ${selectedIds.map((id) => `**${displayNames[id] ?? id}**`).join(", ")}`}
                    className="text-sm"
                  />
                  <p className="text-sm text-gray-500">
                    Ask a question below and responses will stream into this same conversation view.
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>

        <div className="border-t bg-white px-4 py-3">
          <ChatInput onSubmit={handleSubmit} disabled={streaming} supportsAttachments={supportsAttachments} />
        </div>
      </div>
    </div>
  );
}
