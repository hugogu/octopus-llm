'use client';

import { useState, useCallback, useRef } from 'react';
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
  responseId?: string;
}

const emptyState = (): Pick<ModelStreamState, 'text' | 'reasoning'> => ({ text: '', reasoning: '' });

// Flush accumulated token deltas to React state at most this often. Reading the SSE stream mutates a
// ref synchronously; rendering is throttled so the reader never blocks on React reconciliation. This
// keeps every model streaming concurrently even when output grows large (otherwise per-token setState
// makes renders the bottleneck and slower panels appear to freeze).
const FLUSH_INTERVAL_MS = 16;

/** Accumulates per-model SSE stream events into renderable panel state. */
export function useParallelStream() {
  const [models, setModels] = useState<Record<string, ModelStreamState>>({});
  const [streaming, setStreaming] = useState(false);
  const [turnId, setTurnId] = useState<string | null>(null);

  // Authoritative working copy, updated synchronously on every event. `models` (React state) is a
  // throttled snapshot of this, so the SSE read loop is decoupled from render cost.
  const stateRef = useRef<Record<string, ModelStreamState>>({});
  const flushTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const flushNow = useCallback(() => {
    if (flushTimerRef.current != null) {
      clearTimeout(flushTimerRef.current);
      flushTimerRef.current = null;
    }
    setModels({ ...stateRef.current });
  }, []);

  const scheduleFlush = useCallback(() => {
    if (flushTimerRef.current != null) return;
    flushTimerRef.current = setTimeout(() => {
      flushTimerRef.current = null;
      setModels({ ...stateRef.current });
    }, FLUSH_INTERVAL_MS);
  }, []);

  const reset = useCallback((modelIds: string[]) => {
    const init: Record<string, ModelStreamState> = {};
    for (const id of modelIds) {
      init[id] = { ...emptyState(), status: 'idle' };
    }
    if (flushTimerRef.current != null) {
      clearTimeout(flushTimerRef.current);
      flushTimerRef.current = null;
    }
    stateRef.current = init;
    setModels(init);
    setStreaming(false);
    setTurnId(null);
  }, []);

  const handleEvent = useCallback((event: SseEventV2) => {
    const current = stateRef.current;
    const prior = (id: string): ModelStreamState => current[id] ?? { ...emptyState(), status: 'streaming' };

    if (event.event === 'turn_created') {
      setTurnId(event.turnId);
      setStreaming(true);
      const next: Record<string, ModelStreamState> = {};
      for (const id of Object.keys(current)) {
        next[id] = { ...(current[id] ?? emptyState()), status: 'streaming' };
      }
      stateRef.current = next;
      flushNow();
    } else if (event.event === 'capability_notice') {
      current[event.configuredModelId] = { ...prior(event.configuredModelId), capabilityNotice: event.notice };
      scheduleFlush();
    } else if (event.event === 'reasoning') {
      const cur = prior(event.configuredModelId);
      current[event.configuredModelId] = { ...cur, reasoning: cur.reasoning + event.delta, status: 'streaming' };
      scheduleFlush();
    } else if (event.event === 'token') {
      const cur = prior(event.configuredModelId);
      current[event.configuredModelId] = { ...cur, text: cur.text + event.delta, status: 'streaming' };
      scheduleFlush();
    } else if (event.event === 'model_complete') {
      current[event.configuredModelId] = {
        ...prior(event.configuredModelId),
        status: 'complete',
        inputTokens: event.inputTokens,
        outputTokens: event.outputTokens,
        latencyMs: event.latencyMs,
        responseId: event.responseId,
      };
      flushNow();
    } else if (event.event === 'model_error') {
      current[event.configuredModelId] = {
        ...prior(event.configuredModelId),
        status: 'error',
        errorMessage: event.error,
        responseId: event.responseId,
      };
      flushNow();
    } else if (event.event === 'all_complete') {
      setStreaming(false);
      flushNow();
    }
  }, [flushNow, scheduleFlush]);

  return { models, streaming, turnId, reset, handleEvent };
}
