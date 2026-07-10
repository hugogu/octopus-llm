'use client';

import { useState, useCallback, useRef } from 'react';
import type { SseEventV2, ToolCallState, ToolCallStatus } from '@/lib/types/api';

export interface ModelStreamState {
  text: string;
  reasoning: string;
  status: 'idle' | 'streaming' | 'complete' | 'error';
  errorMessage?: string;
  inputTokens?: number;
  outputTokens?: number;
  cacheReadTokens?: number | null;
  cacheWriteTokens?: number | null;
  latencyMs?: number;
  capabilityNotice?: string;
  responseId?: string;
  // Tool invocations for this model in the current turn (feature 009), accumulated by callId.
  toolCalls?: ToolCallState[];
}

/** Merges a tool event into the per-model list, upserting by callId while preserving order. */
function upsertToolCall(
  list: ToolCallState[] | undefined,
  callId: string,
  patch: Omit<Partial<ToolCallState>, 'callId'> & { toolName: string },
): ToolCallState[] {
  const existing = list ?? [];
  if (!existing.some((call) => call.callId === callId)) {
    return [...existing, { callId, status: 'pending', ...patch }];
  }
  return existing.map((call) => (call.callId === callId ? { ...call, ...patch } : call));
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
    const models = stream.models;
    const prior = (id: string): ModelStreamState => models[id] ?? { ...emptyState(), status: 'streaming' };
    const withModel = (id: string, patch: Partial<ModelStreamState>): ParallelStreamState => ({
      ...stream,
      streaming: true,
      models: { ...models, [id]: { ...prior(id), ...patch } },
    });

    let next: ParallelStreamState;
    let immediate = false;

    if (event.event === 'turn_created') {
      const reset: Record<string, ModelStreamState> = {};
      for (const id of Object.keys(models)) {
        reset[id] = { ...(models[id] ?? emptyState()), status: 'streaming' };
      }
      next = { ...stream, turnId: event.turnId, streaming: true, models: reset };
      immediate = true;
    } else if (event.event === 'capability_notice') {
      next = withModel(event.configuredModelId, { capabilityNotice: event.notice });
    } else if (event.event === 'reasoning') {
      next = withModel(event.configuredModelId, {
        reasoning: prior(event.configuredModelId).reasoning + event.delta,
        status: 'streaming',
      });
    } else if (event.event === 'token') {
      next = withModel(event.configuredModelId, {
        text: prior(event.configuredModelId).text + event.delta,
        status: 'streaming',
      });
    } else if (event.event === 'model_complete') {
      next = withModel(event.configuredModelId, {
        status: 'complete',
        inputTokens: event.inputTokens,
        outputTokens: event.outputTokens,
        cacheReadTokens: event.cacheReadTokens,
        cacheWriteTokens: event.cacheWriteTokens,
        latencyMs: event.latencyMs,
        responseId: event.responseId,
      });
      immediate = true;
    } else if (event.event === 'model_error') {
      next = withModel(event.configuredModelId, {
        status: 'error',
        errorMessage: event.error,
        responseId: event.responseId,
      });
      immediate = true;
    } else if (event.event === 'tool_call') {
      next = withModel(event.configuredModelId, {
        toolCalls: upsertToolCall(prior(event.configuredModelId).toolCalls, event.callId, {
          toolName: event.toolName,
          arguments: event.arguments,
          status: 'pending',
        }),
        status: 'streaming',
      });
    } else if (event.event === 'tool_status') {
      next = withModel(event.configuredModelId, {
        toolCalls: upsertToolCall(prior(event.configuredModelId).toolCalls, event.callId, {
          toolName: event.toolName,
          status: event.status as ToolCallStatus,
        }),
      });
    } else if (event.event === 'tool_result') {
      next = withModel(event.configuredModelId, {
        toolCalls: upsertToolCall(prior(event.configuredModelId).toolCalls, event.callId, {
          toolName: event.toolName,
          status: event.status as ToolCallStatus,
          result: event.result,
          error: event.error,
        }),
      });
      immediate = true;
    } else if (event.event === 'all_complete') {
      next = { ...stream, streaming: false };
      immediate = true;
    } else {
      return;
    }

    stateRef.current = { ...stateRef.current, [streamKey]: next };
    if (immediate) flushNow(streamKey);
    else scheduleFlush(streamKey);
  }, [flushNow, scheduleFlush]);

  return { streams, start, clear, handleEvent };
}
