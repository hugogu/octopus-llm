'use client';

import { useState, useCallback } from 'react';
import type { SseEventV2 } from '@/lib/types/api';

export interface ModelStreamState {
  text: string;
  reasoning: string;
  status: 'idle' | 'streaming' | 'complete' | 'error';
  errorMessage?: string;
  inputTokens?: number;
  outputTokens?: number;
  latencyMs?: number;
  capabilityNotice?: string;
}

const emptyState = (): Pick<ModelStreamState, 'text' | 'reasoning'> => ({ text: '', reasoning: '' });

/** Accumulates per-model SSE stream events into renderable panel state. */
export function useParallelStream() {
  const [models, setModels] = useState<Record<string, ModelStreamState>>({});
  const [streaming, setStreaming] = useState(false);
  const [turnId, setTurnId] = useState<string | null>(null);

  const reset = useCallback((modelIds: string[]) => {
    const init: Record<string, ModelStreamState> = {};
    for (const id of modelIds) {
      init[id] = { ...emptyState(), status: 'idle' };
    }
    setModels(init);
    setStreaming(false);
    setTurnId(null);
  }, []);

  const handleEvent = useCallback((event: SseEventV2) => {
    if (event.event === 'turn_created') {
      setTurnId(event.turnId);
      setStreaming(true);
      setModels((prev) => {
        const next = { ...prev };
        for (const id of Object.keys(next)) {
          next[id] = { ...(next[id] ?? emptyState()), status: 'streaming' };
        }
        return next;
      });
    } else if (event.event === 'capability_notice') {
      setModels((prev) => ({
        ...prev,
        [event.configuredModelId]: {
          ...(prev[event.configuredModelId] ?? { ...emptyState(), status: 'streaming' }),
          capabilityNotice: event.notice,
        },
      }));
    } else if (event.event === 'reasoning') {
      setModels((prev) => ({
        ...prev,
        [event.configuredModelId]: {
          ...(prev[event.configuredModelId] ?? { ...emptyState(), status: 'streaming' }),
          reasoning: (prev[event.configuredModelId]?.reasoning ?? '') + event.delta,
          status: 'streaming',
        },
      }));
    } else if (event.event === 'token') {
      setModels((prev) => ({
        ...prev,
        [event.configuredModelId]: {
          ...(prev[event.configuredModelId] ?? { ...emptyState(), status: 'streaming' }),
          text: (prev[event.configuredModelId]?.text ?? '') + event.delta,
          status: 'streaming',
        },
      }));
    } else if (event.event === 'model_complete') {
      setModels((prev) => ({
        ...prev,
        [event.configuredModelId]: {
          ...(prev[event.configuredModelId] ?? { ...emptyState(), status: 'complete' }),
          status: 'complete',
          inputTokens: event.inputTokens,
          outputTokens: event.outputTokens,
          latencyMs: event.latencyMs,
        },
      }));
    } else if (event.event === 'model_error') {
      setModels((prev) => ({
        ...prev,
        [event.configuredModelId]: {
          ...(prev[event.configuredModelId] ?? { ...emptyState(), status: 'error' }),
          status: 'error',
          errorMessage: event.error,
        },
      }));
    } else if (event.event === 'all_complete') {
      setStreaming(false);
    }
  }, []);

  return { models, streaming, turnId, reset, handleEvent };
}
