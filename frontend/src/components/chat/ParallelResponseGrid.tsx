"use client";

import { useState, useCallback } from "react";
import type { SseEvent } from "@/lib/types/api";
import ModelResponsePanel from "./ModelResponsePanel";

interface ModelState {
  text: string;
  status: "idle" | "streaming" | "complete" | "error";
  errorMessage?: string;
  inputTokens?: number;
  outputTokens?: number;
  latencyMs?: number;
  capabilityNotice?: string;
}

interface ParallelResponseGridProps {
  selectedModelIds: string[];
  modelDisplayNames: Record<string, string>;
  onStreamComplete?: (turnId: string) => void;
}

export function useParallelStream() {
  const [models, setModels] = useState<Record<string, ModelState>>({});
  const [streaming, setStreaming] = useState(false);
  const [turnId, setTurnId] = useState<string | null>(null);

  const reset = useCallback((modelIds: string[]) => {
    const init: Record<string, ModelState> = {};
    for (const id of modelIds) {
      init[id] = { text: "", status: "idle" };
    }
    setModels(init);
    setStreaming(false);
    setTurnId(null);
  }, []);

  const handleEvent = useCallback((event: SseEvent) => {
    if (event.event === "turn_created") {
      setTurnId(event.turnId);
      setStreaming(true);
      setModels((prev) => {
        const next = { ...prev };
        for (const id of Object.keys(next)) {
          next[id] = { ...(next[id] ?? { text: "" }), status: "streaming" };
        }
        return next;
      });
    } else if (event.event === "capability_notice") {
      setModels((prev) => ({
        ...prev,
        [event.modelId]: {
          ...(prev[event.modelId] ?? { text: "", status: "streaming" }),
          capabilityNotice: event.notice,
        },
      }));
    } else if (event.event === "token") {
      setModels((prev) => ({
        ...prev,
        [event.modelId]: {
          ...(prev[event.modelId] ?? { text: "", status: "streaming" }),
          text: (prev[event.modelId]?.text ?? "") + event.delta,
          status: "streaming",
        },
      }));
    } else if (event.event === "model_complete") {
      setModels((prev) => ({
        ...prev,
        [event.modelId]: {
          ...(prev[event.modelId] ?? { text: "" }),
          status: "complete",
          inputTokens: event.inputTokens,
          outputTokens: event.outputTokens,
          latencyMs: event.latencyMs,
        },
      }));
    } else if (event.event === "model_error") {
      setModels((prev) => ({
        ...prev,
        [event.modelId]: {
          ...(prev[event.modelId] ?? { text: "" }),
          status: "error",
          errorMessage: event.error,
        },
      }));
    } else if (event.event === "all_complete") {
      setStreaming(false);
    }
  }, []);

  return { models, streaming, turnId, reset, handleEvent };
}

export default function ParallelResponseGrid({
  selectedModelIds,
  modelDisplayNames,
}: ParallelResponseGridProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
      {selectedModelIds.map((id) => (
        <ModelResponsePanel
          key={id}
          modelId={id}
          displayName={modelDisplayNames[id] ?? id}
          text=""
          status="idle"
        />
      ))}
    </div>
  );
}
