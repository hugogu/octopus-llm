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

export interface ParallelStreamState {
  models: Record<string, ModelStreamState>;
  streaming: boolean;
  turnId: string | null;
}

const emptyStream = (): ParallelStreamState => ({ models: {}, streaming: false, turnId: null });

/** Accumulates multiple session/turn SSE streams without discarding background streams on navigation. */
export function useParallelStream() {
  const [streams, setStreams] = useState<Record<string, ParallelStreamState>>({});

  // Authoritative working copies are updated synchronously. React receives throttled snapshots,
  // keeping all SSE readers independent from rendering and from the currently selected session.
  const stateRef = useRef<Record<string, ParallelStreamState>>({});
  const flushTimersRef = useRef<Record<string, ReturnType<typeof setTimeout>>>({});

  const flushNow = useCallback((streamKey: string) => {
    const timer = flushTimersRef.current[streamKey];
    if (timer != null) {
      clearTimeout(timer);
      delete flushTimersRef.current[streamKey];
    }
    const stream = stateRef.current[streamKey];
    if (!stream) return;
    setStreams((current) => ({ ...current, [streamKey]: { ...stream, models: { ...stream.models } } }));
  }, []);

  const scheduleFlush = useCallback((streamKey: string) => {
    if (flushTimersRef.current[streamKey] != null) return;
    flushTimersRef.current[streamKey] = setTimeout(() => {
      delete flushTimersRef.current[streamKey];
      const stream = stateRef.current[streamKey];
      if (!stream) return;
      setStreams((current) => ({ ...current, [streamKey]: { ...stream, models: { ...stream.models } } }));
    }, FLUSH_INTERVAL_MS);
  }, []);

  const start = useCallback((streamKey: string, modelIds: string[], streaming = false) => {
    const init: Record<string, ModelStreamState> = {};
    for (const id of modelIds) {
      init[id] = { ...emptyState(), status: streaming ? 'streaming' : 'idle' };
    }
    const timer = flushTimersRef.current[streamKey];
    if (timer != null) {
      clearTimeout(timer);
      delete flushTimersRef.current[streamKey];
    }
    const stream = { ...emptyStream(), models: init, streaming };
    stateRef.current[streamKey] = stream;
    setStreams((current) => ({ ...current, [streamKey]: stream }));
  }, []);

  const clear = useCallback((streamKey: string) => {
    const timer = flushTimersRef.current[streamKey];
    if (timer != null) clearTimeout(timer);
    delete flushTimersRef.current[streamKey];
    delete stateRef.current[streamKey];
    setStreams((current) => {
      const next = { ...current };
      delete next[streamKey];
      return next;
    });
  }, []);

  const handleEvent = useCallback((streamKey: string, event: SseEventV2) => {
    const stream = stateRef.current[streamKey] ?? emptyStream();
    stateRef.current[streamKey] = stream;
    const current = stream.models;
    const prior = (id: string): ModelStreamState => current[id] ?? { ...emptyState(), status: 'streaming' };

    if (event.event === 'turn_created') {
      const next: Record<string, ModelStreamState> = {};
      for (const id of Object.keys(current)) {
        next[id] = { ...(current[id] ?? emptyState()), status: 'streaming' };
      }
      stream.turnId = event.turnId;
      stream.streaming = true;
      stream.models = next;
      flushNow(streamKey);
    } else if (event.event === 'capability_notice') {
      current[event.configuredModelId] = { ...prior(event.configuredModelId), capabilityNotice: event.notice };
      stream.streaming = true;
      scheduleFlush(streamKey);
    } else if (event.event === 'reasoning') {
      const cur = prior(event.configuredModelId);
      current[event.configuredModelId] = { ...cur, reasoning: cur.reasoning + event.delta, status: 'streaming' };
      stream.streaming = true;
      scheduleFlush(streamKey);
    } else if (event.event === 'token') {
      const cur = prior(event.configuredModelId);
      current[event.configuredModelId] = { ...cur, text: cur.text + event.delta, status: 'streaming' };
      stream.streaming = true;
      scheduleFlush(streamKey);
    } else if (event.event === 'model_complete') {
      current[event.configuredModelId] = {
        ...prior(event.configuredModelId),
        status: 'complete',
        inputTokens: event.inputTokens,
        outputTokens: event.outputTokens,
        latencyMs: event.latencyMs,
        responseId: event.responseId,
      };
      flushNow(streamKey);
    } else if (event.event === 'model_error') {
      current[event.configuredModelId] = {
        ...prior(event.configuredModelId),
        status: 'error',
        errorMessage: event.error,
        responseId: event.responseId,
      };
      flushNow(streamKey);
    } else if (event.event === 'all_complete') {
      stream.streaming = false;
      flushNow(streamKey);
    }
  }, [flushNow, scheduleFlush]);

  return { streams, start, clear, handleEvent };
}
