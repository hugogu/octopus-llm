"use client";

import { useState, useCallback } from "react";
import type { ModelDefinition, UserModelConfig, SseEvent } from "@/lib/types/api";
import { createSession, streamTurn } from "@/lib/api/chat";
import { listModels } from "@/lib/api/models";
import { listModelConfigs } from "@/lib/api/userConfig";
import { getToken } from "@/lib/api/auth";
import ModelSelectorPanel from "@/components/chat/ModelSelectorPanel";
import ChatInput from "@/components/chat/ChatInput";
import ModelResponsePanel from "@/components/chat/ModelResponsePanel";
import { useParallelStream } from "@/components/chat/ParallelResponseGrid";
import { useEffect } from "react";

export default function ChatPage() {
  const [models, setModels] = useState<ModelDefinition[]>([]);
  const [configs, setConfigs] = useState<UserModelConfig[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const { models: panelStates, streaming, reset, handleEvent } = useParallelStream();

  useEffect(() => {
    const token = getToken();
    if (!token) return;
    (async () => {
      try {
        const [{ models: ms }, { modelConfigs }] = await Promise.all([
          listModels(),
          listModelConfigs(token),
        ]);

        setModels(ms);
        setConfigs(modelConfigs);
        const enabled = modelConfigs.filter((c) => c.isEnabled).map((c) => c.modelId);
        setSelectedIds(enabled.slice(0, 3));
      } catch (err) {
        console.error(err);
      }
    })();
  }, []);

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

  const handleSubmit = useCallback(async (promptText: string, attachments: import("@/lib/types/api").Attachment[]) => {
    if (selectedIds.length === 0) return;
    reset(selectedIds);

    try {
      let sid = sessionId;
      if (!sid) {
        const session = await createSession();
        sid = session.id;
        setSessionId(sid);
      }

      await streamTurn(
        sid,
        { promptText, selectedModelIds: selectedIds, attachments },
        (event: SseEvent) => handleEvent(event),
      );
    } catch (err) {
      console.error("Stream error:", err);
    }
  }, [selectedIds, sessionId, reset, handleEvent]);

  return (
    <div className="flex flex-col h-screen max-h-screen">
      <header className="border-b px-4 py-3 flex items-center justify-between">
        <h1 className="font-bold text-lg">Octopus LLM</h1>
        <a href="/settings/models" className="text-sm text-blue-600 hover:underline">
          Settings
        </a>
      </header>

      <div className="border-b px-4 py-2">
        <ModelSelectorPanel
          models={models}
          configs={configs}
          selectedIds={selectedIds}
          onChange={setSelectedIds}
        />
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4">
        {selectedIds.length > 0 && Object.keys(panelStates).length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {selectedIds.map((id) => {
              const state = panelStates[id];
              return (
                <ModelResponsePanel
                  key={id}
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
        ) : (
          <div className="flex items-center justify-center h-full text-gray-400 text-sm">
            {selectedIds.length === 0
              ? "Select models above to start chatting"
              : "Send a message to begin"}
          </div>
        )}
      </div>

      <div className="border-t px-4 py-3">
        <ChatInput onSubmit={handleSubmit} disabled={streaming} supportsAttachments={supportsAttachments} />
      </div>
    </div>
  );
}
