"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Download, Trash2 } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import ChatInput from "@/components/chat/ChatInput";
import MarkdownRenderer from "@/components/chat/MarkdownRenderer";
import ModelResponsePanel from "@/components/chat/ModelResponsePanel";
import ModelSelectorPanel from "@/components/chat/ModelSelectorPanel";
import SessionSidebar from "@/components/chat/SessionSidebar";
import ShareConversationButton from "@/components/chat/ShareConversationButton";
import { getToken } from "@/lib/api/auth";
import {
  createSessionV2,
  getSessionV2,
  retryModelV2,
  streamTurnV2,
} from "@/lib/api/chatV2";
import { listConfiguredModels } from "@/lib/api/connections";
import { uploadMedia } from "@/lib/api/media";
import { useParallelStream } from "@/lib/hooks/useParallelStream";
import { usePreferences } from "@/lib/hooks/usePreferences";
import { useSessions } from "@/lib/hooks/useSessions";
import type {
  ChatTurnV2,
  ConfiguredModelV2,
  GetSessionResponseV2,
  SseEventV2,
} from "@/lib/types/api";
import {
  conversationFilename,
  conversationToMarkdown,
  downloadTextFile,
} from "@/lib/utils/exportConversation";

const SELECTED_MODELS_STORAGE_KEY = "octopus:selected-configured-model-ids";
const responseGridStyle = {
  display: "grid",
  gap: "0.75rem",
  gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 360px), 1fr))",
} as const;

interface DraftTurnState {
  promptText: string;
  selectedConfiguredModelIds: string[];
  turnId?: string;
}

const retryStreamKey = (turnId: string, configuredModelId: string) =>
  `retry:${turnId}:${configuredModelId}`;

export default function ChatPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const querySessionId = searchParams.get("session");
  const [models, setModels] = useState<ConfiguredModelV2[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [activeSession, setActiveSession] = useState<GetSessionResponseV2 | null>(null);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [modelsLoading, setModelsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [liveTurns, setLiveTurns] = useState<Record<string, DraftTurnState>>({});
  const [attachmentNotice, setAttachmentNotice] = useState<string | null>(null);
  const initializedSelectionRef = useRef(false);
  const sessionIdRef = useRef<string | null>(null);
  const { streams, start, clear, handleEvent } = useParallelStream();
  const { preferences, setLastSelectedModel } = usePreferences();
  const { sessions, loading: sessionsLoading, loadSessions, removeSession } = useSessions();

  const enabledModels = useMemo(() => models.filter((model) => model.isEnabled), [models]);
  const modelsById = useMemo(
    () => Object.fromEntries(models.map((model) => [model.id, model])),
    [models],
  );
  const liveTurn = sessionId ? liveTurns[sessionId] ?? null : null;
  const currentStream = sessionId ? streams[sessionId] : undefined;
  const panelStates = currentStream?.models ?? {};
  const streaming = currentStream?.streaming ?? false;

  useEffect(() => {
    sessionIdRef.current = sessionId;
  }, [sessionId]);

  const loadModels = useCallback(async () => {
    const token = getToken();
    if (!token) {
      setModelsLoading(false);
      return;
    }
    setModelsLoading(true);
    try {
      const response = await listConfiguredModels(token, undefined, 0, 100);
      setModels(response.items);
      setLoadError(null);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Failed to load configured models");
    } finally {
      setModelsLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => void loadModels());
  }, [loadModels]);

  useEffect(() => {
    queueMicrotask(() => {
      if (initializedSelectionRef.current || enabledModels.length === 0) return;
      const enabledIds = new Set(enabledModels.map((model) => model.id));
      const stored = window.localStorage.getItem(SELECTED_MODELS_STORAGE_KEY);
      if (stored) {
        try {
          const parsed = JSON.parse(stored);
          if (Array.isArray(parsed)) {
            setSelectedIds(parsed.filter((id): id is string => typeof id === "string" && enabledIds.has(id)));
            initializedSelectionRef.current = true;
            return;
          }
        } catch {
          window.localStorage.removeItem(SELECTED_MODELS_STORAGE_KEY);
        }
      }

      const preferred = preferences?.lastSelectedConfiguredModelId;
      setSelectedIds(preferred && enabledIds.has(preferred) ? [preferred] : enabledModels.slice(0, 3).map((m) => m.id));
      initializedSelectionRef.current = true;
    });
  }, [enabledModels, preferences]);

  useEffect(() => {
    if (!initializedSelectionRef.current) return;
    queueMicrotask(() => {
      const enabledIds = new Set(enabledModels.map((model) => model.id));
      setSelectedIds((current) => current.filter((id) => enabledIds.has(id)));
    });
  }, [enabledModels]);

  useEffect(() => {
    if (!initializedSelectionRef.current) return;
    window.localStorage.setItem(SELECTED_MODELS_STORAGE_KEY, JSON.stringify(selectedIds));
  }, [selectedIds]);

  const loadSessionData = useCallback(async (id: string) => {
    const token = getToken();
    if (!token) return;
    if (sessionIdRef.current === id) setSessionLoading(true);
    try {
      const session = await getSessionV2(id, token);
      if (sessionIdRef.current !== id) return;
      setActiveSession(session);
      const enabledIds = new Set(enabledModels.map((model) => model.id));
      const priorSelection = session.turns.at(-1)?.selectedConfiguredModelIds.filter((modelId) => enabledIds.has(modelId)) ?? [];
      if (priorSelection.length > 0) setSelectedIds(priorSelection);
      setLoadError(null);
    } catch (error) {
      if (sessionIdRef.current === id) {
        setLoadError(error instanceof Error ? error.message : "Failed to load conversation");
      }
    } finally {
      if (sessionIdRef.current === id) setSessionLoading(false);
    }
  }, [enabledModels]);

  useEffect(() => {
    queueMicrotask(() => {
      if (!querySessionId) {
        sessionIdRef.current = null;
        setSessionId(null);
        setActiveSession(null);
        return;
      }
      sessionIdRef.current = querySessionId;
      setSessionId(querySessionId);
      void loadSessionData(querySessionId);
    });
  }, [loadSessionData, querySessionId]);

  // Refresh like / anonymous-thumb counts read-only while viewing, so loves and 👍 added on a shared
  // link show up here without a manual reload. Skips while streaming to avoid clobbering live state.
  const refreshCounts = useCallback(async () => {
    const token = getToken();
    if (!token || !sessionId) return;
    try {
      setActiveSession(await getSessionV2(sessionId, token));
    } catch {
      // background refresh — ignore transient errors
    }
  }, [sessionId]);

  useEffect(() => {
    if (!sessionId) return;
    const maybeRefresh = () => {
      if (document.visibilityState === "visible" && !streaming) void refreshCounts();
    };
    const interval = window.setInterval(maybeRefresh, 20000);
    document.addEventListener("visibilitychange", maybeRefresh);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", maybeRefresh);
    };
  }, [sessionId, streaming, refreshCounts]);

  const supportsAttachments = selectedIds.some((id) => {
    const modalities = modelsById[id]?.capabilityMatrix.input_modalities ?? [];
    return modalities.includes("image") || modalities.includes("video");
  });
  const currentSessionMeta = sessionId
    ? sessions.find((session) => session.id === sessionId)
    : null;

  const handleNewSession = useCallback(() => {
    sessionIdRef.current = null;
    setSessionId(null);
    setActiveSession(null);
    router.replace("/chat");
  }, [router]);

  const handleSelectSession = useCallback((id: string) => {
    router.replace(`/chat?session=${encodeURIComponent(id)}`);
  }, [router]);

  const handleDeleteSession = useCallback(async (id: string) => {
    await removeSession(id);
    if (id === sessionId) handleNewSession();
  }, [handleNewSession, removeSession, sessionId]);

  const handleSubmit = useCallback(async (promptText: string, files: File[]) => {
    if (selectedIds.length === 0) return;
    setAttachmentNotice(null);

    // Capability gating (feature 007): only models that accept every attached media type are sent;
    // incapable models are skipped with a notice, and the send is blocked if none are capable.
    const attachedTypes = Array.from(new Set(files.map((f) =>
      f.type.startsWith("video/") ? "video" : f.type.startsWith("audio/") ? "audio" : "image",
    )));
    let sendIds = selectedIds;
    let attachmentRefs: { media_id: string; order: string }[] = [];
    if (files.length > 0) {
      const capableIds = selectedIds.filter((id) => {
        const modalities = modelsById[id]?.capabilityMatrix.input_modalities ?? [];
        return attachedTypes.every((t) => modalities.includes(t));
      });
      const excluded = selectedIds.filter((id) => !capableIds.includes(id));
      if (capableIds.length === 0) {
        setAttachmentNotice(
          `None of the selected models accept ${attachedTypes.join(", ")} input — remove the attachment or pick a capable model.`,
        );
        return;
      }
      if (excluded.length > 0) {
        const names = excluded.map((id) => modelsById[id]?.displayName ?? id).join(", ");
        setAttachmentNotice(`${names} can't accept ${attachedTypes.join(", ")} input and were skipped for this message.`);
      }
      const uploadToken = getToken();
      if (!uploadToken) return;
      try {
        const uploaded = await Promise.all(files.map((file) => uploadMedia(file, uploadToken)));
        attachmentRefs = uploaded.map((ref, i) => ({ media_id: ref.media_id, order: String(i) }));
      } catch (err) {
        setAttachmentNotice(err instanceof Error ? err.message : "Upload failed");
        return;
      }
      sendIds = capableIds;
    }

    try {
      let id = sessionId;
      if (!id) {
        const session = await createSessionV2();
        id = session.id;
        sessionIdRef.current = id;
        setSessionId(id);
        setActiveSession({ id, title: session.title, turns: [] });
        router.replace(`/chat?session=${encodeURIComponent(id)}`);
        await loadSessions();
      }

      start(id, sendIds);
      setLiveTurns((current) => ({
        ...current,
        [id]: { promptText, selectedConfiguredModelIds: [...sendIds] },
      }));
      await streamTurnV2(
        id,
        {
          promptText,
          selectedConfiguredModelIds: sendIds,
          attachments: attachmentRefs,
          clientRequestId: crypto.randomUUID(),
        },
        (event: SseEventV2) => {
          handleEvent(id, event);
          if (event.event === "turn_created") {
            setLiveTurns((current) => ({
              ...current,
              [id]: { ...(current[id] ?? { promptText, selectedConfiguredModelIds: [...selectedIds] }), turnId: event.turnId },
            }));
          }
        },
      );
      const token = getToken();
      if (token) {
        const session = await getSessionV2(id, token);
        if (sessionIdRef.current === id) setActiveSession(session);
      }
      setLiveTurns((current) => {
        const next = { ...current };
        delete next[id];
        return next;
      });
      clear(id);
      await loadSessions();
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Chat request failed");
    }
  }, [clear, handleEvent, loadSessions, modelsById, router, selectedIds, sessionId, start]);

  const handleRetry = useCallback(async (turnId: string, configuredModelId: string) => {
    if (!sessionId) return;
    const id = sessionId;
    const key = retryStreamKey(turnId, configuredModelId);
    start(key, [configuredModelId], true);
    try {
      await retryModelV2(
        id,
        turnId,
        configuredModelId,
        crypto.randomUUID(),
        (event) => handleEvent(key, event),
      );
      const token = getToken();
      if (token) {
        const session = await getSessionV2(id, token);
        if (sessionIdRef.current === id) setActiveSession(session);
      }
      clear(key);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Model retry failed");
      clear(key);
    }
  }, [clear, handleEvent, sessionId, start]);

  const renderTurn = useCallback((turn: ChatTurnV2) => (
    <section key={turn.id} className="space-y-3">
      <div className="ml-auto w-fit max-w-3xl rounded-2xl bg-[#30302e] px-4 py-3 text-white shadow-sm">
        <MarkdownRenderer content={turn.promptText} className="text-sm [&_p]:mb-0 [&_*]:text-white" />
      </div>
      <div style={responseGridStyle}>
        {turn.responses.map((response) => {
          const key = retryStreamKey(turn.id, response.configuredModelId);
          const retry = streams[key];
          const retryState = retry?.models[response.configuredModelId];
          return (
            <ModelResponsePanel
              key={`${turn.id}:${response.configuredModelId}`}
              modelId={response.modelId}
              displayName={response.modelDisplayName}
              connectionLabel={response.connectionLabel}
              text={retryState?.text ?? response.responseText ?? ""}
              reasoning={retryState?.reasoning ?? response.reasoningText ?? ""}
              status={retryState?.status ?? response.status}
              errorMessage={retryState?.errorMessage ?? response.errorMessage ?? undefined}
              inputTokens={retryState?.inputTokens ?? response.inputTokens ?? undefined}
              outputTokens={retryState?.outputTokens ?? response.outputTokens ?? undefined}
              cacheReadTokens={retryState?.cacheReadTokens ?? response.cacheReadTokens}
              cacheWriteTokens={retryState?.cacheWriteTokens ?? response.cacheWriteTokens}
              latencyMs={retryState?.latencyMs ?? response.latencyMs}
              capabilityMatrix={modelsById[response.configuredModelId]?.capabilityMatrix}
              responseId={retryState?.responseId ?? response.responseId}
              likeCount={response.likeCount}
              likedByMe={response.likedByMe}
              anonymousLikeCount={response.anonymousLikeCount}
              retrying={retry?.streaming ?? false}
              onRetry={() => void handleRetry(turn.id, response.configuredModelId)}
            />
          );
        })}
      </div>
    </section>
  ), [handleRetry, modelsById, streams]);

  const hasConversation = (activeSession?.turns.length ?? 0) > 0 || liveTurn !== null;
  const handleDelete = useCallback(() => {
    if (!sessionId) return;
    if (!window.confirm("Are you sure you want to delete this conversation?")) return;
    void handleDeleteSession(sessionId);
  }, [handleDeleteSession, sessionId]);
  const handleExport = useCallback(() => {
    if (!activeSession) return;
    downloadTextFile(
      conversationFilename(currentSessionMeta?.title ?? activeSession.title),
      conversationToMarkdown(activeSession),
    );
  }, [activeSession, currentSessionMeta]);

  return (
    <div className="flex h-screen max-h-screen bg-[#faf9f5]">
      <SessionSidebar
        sessions={sessions}
        currentSessionId={sessionId}
        onSelectSession={handleSelectSession}
        onDeleteSession={handleDeleteSession}
        onNewSession={handleNewSession}
        loading={sessionsLoading}
      />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-b border-stone-200 px-6 py-3">
          <div className="flex items-center justify-between gap-4">
            <div className="min-w-0">
              <h1 className="truncate text-base font-semibold text-stone-900">
                {currentSessionMeta?.title || activeSession?.title || "New conversation"}
              </h1>
              <p className="truncate text-xs text-stone-500">Compare configured endpoints in one thread</p>
            </div>
            <div className="flex shrink-0 items-center gap-1.5">
              <ModelSelectorPanel
                models={models}
                selectedIds={selectedIds}
                onChange={(ids) => {
                  setSelectedIds(ids);
                  void setLastSelectedModel(ids[0] ?? null);
                }}
              />
              {sessionId ? (
                <>
                  <span className="mx-0.5 h-5 w-px bg-stone-200" aria-hidden />
                  <ShareConversationButton sessionId={sessionId} />
                  <button
                    type="button"
                    onClick={handleExport}
                    className="flex items-center gap-1.5 rounded-lg border border-stone-200 bg-white px-3 py-1.5 text-xs font-medium text-stone-600 transition hover:text-stone-900"
                  >
                    <Download className="h-3.5 w-3.5" /> Export
                  </button>
                  <button
                    type="button"
                    onClick={handleDelete}
                    className="flex items-center gap-1.5 rounded-lg border border-stone-200 bg-white px-3 py-1.5 text-xs font-medium text-stone-600 transition hover:border-red-200 hover:bg-red-50 hover:text-red-600"
                  >
                    <Trash2 className="h-3.5 w-3.5" /> Delete
                  </button>
                </>
              ) : null}
            </div>
          </div>
        </header>

        <div className="flex-1 overflow-y-auto px-6">
          <div className="flex w-full flex-col gap-6 py-6">
            {loadError ? (
              <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadError}</div>
            ) : null}
            {sessionLoading || modelsLoading ? (
              <div className="space-y-4">
                <div className="ml-auto h-24 max-w-3xl animate-pulse rounded-2xl bg-stone-200" />
                <div className="h-52 animate-pulse rounded-2xl bg-stone-200" />
              </div>
            ) : hasConversation ? (
              <>
                {activeSession?.turns
                  .filter((turn) => turn.id !== liveTurn?.turnId)
                  .map(renderTurn)}
                {liveTurn ? (
                  <section className="space-y-3">
                    <div className="ml-auto w-fit max-w-3xl rounded-2xl bg-[#30302e] px-4 py-3 text-white shadow-sm">
                      <MarkdownRenderer content={liveTurn.promptText} className="text-sm [&_p]:mb-0 [&_*]:text-white" />
                    </div>
                    <div style={responseGridStyle}>
                      {liveTurn.selectedConfiguredModelIds.map((id) => {
                        const model = modelsById[id];
                        const state = panelStates[id];
                        return (
                          <ModelResponsePanel
                            key={`draft:${id}`}
                            modelId={model?.modelId ?? id}
                            displayName={model?.displayName ?? id}
                            connectionLabel={model?.connectionLabel}
                            text={state?.text ?? ""}
                            reasoning={state?.reasoning ?? ""}
                            status={state?.status ?? "idle"}
                            errorMessage={state?.errorMessage}
                            inputTokens={state?.inputTokens}
                            outputTokens={state?.outputTokens}
                            cacheReadTokens={state?.cacheReadTokens}
                            cacheWriteTokens={state?.cacheWriteTokens}
                            latencyMs={state?.latencyMs}
                            capabilityNotice={state?.capabilityNotice}
                            capabilityMatrix={model?.capabilityMatrix}
                            responseId={state?.responseId}
                          />
                        );
                      })}
                    </div>
                  </section>
                ) : null}
              </>
            ) : selectedIds.length === 0 ? (
              <div className="flex min-h-[50vh] items-center justify-center rounded-3xl border border-dashed border-stone-300 bg-white/70 px-8 text-center text-sm text-stone-500">
                Configure and select at least one model to start chatting.
              </div>
            ) : (
              <div className="flex min-h-[50vh] items-center justify-center rounded-3xl border border-dashed border-stone-300 bg-white/70 px-8 text-center">
                <div className="max-w-xl space-y-3">
                  <p className="text-sm font-semibold uppercase tracking-[0.18em] text-stone-400">Ready to chat</p>
                  <p className="text-sm text-stone-600">
                    {selectedIds.map((id) => modelsById[id]?.displayName ?? id).join(", ")}
                  </p>
                  <p className="text-sm text-stone-500">Responses will stream into this conversation.</p>
                </div>
              </div>
            )}
          </div>
        </div>

        <div className="border-t border-stone-200 bg-[#faf9f5] px-6 py-3">
          {attachmentNotice && (
            <div className="mx-auto mb-2 w-full max-w-3xl rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
              {attachmentNotice}
            </div>
          )}
          <ChatInput onSubmit={handleSubmit} disabled={streaming || selectedIds.length === 0} supportsAttachments={supportsAttachments} />
        </div>
      </div>
    </div>
  );
}
