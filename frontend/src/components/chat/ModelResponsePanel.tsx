"use client";

import { useState } from "react";
import { Info, ThumbsUp } from "lucide-react";
import type { CapabilityMatrix } from "@/lib/types/api";
import StreamingMarkdown from "./StreamingMarkdown";
import ThinkingBlock from "./ThinkingBlock";
import ExpandableContent from "./ExpandableContent";
import CopyButton from "@/components/ui/CopyButton";
import ResponseLikeButton from "./ResponseLikeButton";

interface ModelResponsePanelProps {
  modelId: string;
  displayName?: string;
  connectionLabel?: string | null;
  text: string;
  reasoning?: string;
  status: "idle" | "streaming" | "complete" | "error";
  errorMessage?: string;
  inputTokens?: number;
  outputTokens?: number;
  latencyMs?: number;
  capabilityNotice?: string;
  capabilityMatrix?: CapabilityMatrix;
  responseId?: string;
  likeCount?: number;
  likedByMe?: boolean;
  anonymousLikeCount?: number;
}

export default function ModelResponsePanel({
  modelId,
  displayName,
  connectionLabel,
  text,
  reasoning = "",
  status,
  errorMessage,
  inputTokens,
  outputTokens,
  latencyMs,
  capabilityNotice,
  capabilityMatrix,
  responseId,
  likeCount = 0,
  likedByMe = false,
  anonymousLikeCount = 0,
}: ModelResponsePanelProps) {
  const [showCaps, setShowCaps] = useState(false);

  return (
    <div className="flex min-w-0 flex-col overflow-hidden rounded-xl border border-stone-200 bg-white shadow-sm">
      <div className="flex items-center justify-between gap-2 border-b border-stone-100 bg-stone-50/60 px-4 py-2.5">
        <div className="flex min-w-0 items-center gap-2">
          <span
            className={`h-2 w-2 shrink-0 rounded-full ${
              status === "streaming"
                ? "animate-pulse bg-blue-500"
                : status === "complete"
                  ? "bg-green-500"
                  : status === "error"
                    ? "bg-red-500"
                    : "bg-stone-300"
            }`}
          />
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-stone-800">{displayName ?? modelId}</p>
            {connectionLabel ? <p className="truncate text-[11px] text-stone-400">{connectionLabel}</p> : null}
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <ResponseLikeButton responseId={responseId} initialCount={likeCount} initialLiked={likedByMe} />
          {anonymousLikeCount > 0 ? (
            <span
              className="inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-xs text-stone-400"
              title={`${anonymousLikeCount} anonymous thumbs up from shared viewers`}
            >
              <ThumbsUp className="h-3.5 w-3.5" />
              {anonymousLikeCount}
            </span>
          ) : null}
          {status === "complete" && (
            <span className="hidden text-xs text-stone-400 sm:block">
              {latencyMs !== undefined && `${(latencyMs / 1000).toFixed(1)}s`}
              {inputTokens !== undefined && ` · in ${inputTokens}`}
              {outputTokens !== undefined && ` · out ${outputTokens}`}
            </span>
          )}
          {(status === "complete" || status === "error") && text && (
            <CopyButton text={text} />
          )}
          {capabilityMatrix && (
            <button
              type="button"
              onClick={() => setShowCaps((v) => !v)}
              className="rounded-md p-1 text-stone-400 hover:bg-stone-100 hover:text-stone-700"
              title="Show capabilities"
            >
              <Info className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      </div>

      {showCaps && capabilityMatrix && (
        <div className="flex flex-col gap-1 border-b border-stone-100 bg-stone-50 px-4 py-2 text-xs text-stone-600">
          <p><strong>Input:</strong> {capabilityMatrix.input_modalities.join(", ")}</p>
          <p><strong>Output:</strong> {capabilityMatrix.output_modalities.join(", ")}</p>
          {capabilityMatrix.context_length_tokens && (
            <p><strong>Context:</strong> {capabilityMatrix.context_length_tokens.toLocaleString()} tokens</p>
          )}
          <div className="flex gap-2">
            {capabilityMatrix.supports_streaming && <span className="rounded bg-stone-200 px-1.5 py-0.5 text-stone-700">streaming</span>}
            {capabilityMatrix.supports_function_calling && <span className="rounded bg-stone-200 px-1.5 py-0.5 text-stone-700">functions</span>}
            {capabilityMatrix.supports_video_input && <span className="rounded bg-stone-200 px-1.5 py-0.5 text-stone-700">video</span>}
          </div>
        </div>
      )}

      {capabilityNotice && (
        <div className="border-b border-amber-100 bg-amber-50 px-4 py-1.5 text-xs text-amber-700">
          ⚠ {capabilityNotice}
        </div>
      )}

      <div className="min-h-[60px] flex-1 px-4 py-3 text-sm">
        {status === "error" ? (
          <span className="text-red-600">{errorMessage ?? "An error occurred"}</span>
        ) : (
          <>
            <ThinkingBlock
              reasoning={reasoning}
              autoOpen={status === "streaming" && !text}
            />
            <ExpandableContent forceExpanded={status === "streaming"}>
              <StreamingMarkdown content={text} debounceMs={100} />
            </ExpandableContent>
            {status === "streaming" && <span className="animate-pulse text-stone-400">▋</span>}
          </>
        )}
      </div>
    </div>
  );
}
