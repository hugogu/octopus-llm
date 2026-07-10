"use client";

import { useState } from "react";
import { Maximize2, Minimize2, RotateCcw, ThumbsUp, Trash2 } from "lucide-react";
import type { CapabilityMatrix, ToolCallState } from "@/lib/types/api";
import StreamingMarkdown from "./StreamingMarkdown";
import ThinkingBlock from "./ThinkingBlock";
import ToolStatusIndicator from "./ToolStatusIndicator";
import ExpandableContent from "./ExpandableContent";
import CopyButton from "@/components/ui/CopyButton";
import ResponseLikeButton from "./ResponseLikeButton";
import ResponseDetails from "./ResponseDetails";
import { confirmDialog } from "@/lib/ui/confirm";

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
  cacheReadTokens?: number | null;
  cacheWriteTokens?: number | null;
  latencyMs?: number;
  capabilityNotice?: string;
  capabilityMatrix?: CapabilityMatrix;
  responseId?: string;
  toolCalls?: ToolCallState[];
  likeCount?: number;
  likedByMe?: boolean;
  anonymousLikeCount?: number;
  onRetry?: () => void;
  retrying?: boolean;
  /** Controlled like state (mutually exclusive within a group); see {@link ResponseGroup}. */
  likeControlled?: { count: number; liked: boolean; busy?: boolean; onToggle: () => void };
  /** Show the maximize toggle (only when a group has more than one response). */
  canMaximize?: boolean;
  /** Whether this panel is currently maximized within its group. */
  maximized?: boolean;
  onToggleMaximize?: () => void;
  onDelete?: () => Promise<void>;
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
  cacheReadTokens,
  cacheWriteTokens,
  latencyMs,
  capabilityNotice,
  capabilityMatrix,
  responseId,
  toolCalls,
  likeCount = 0,
  likedByMe = false,
  anonymousLikeCount = 0,
  onRetry,
  retrying = false,
  likeControlled,
  canMaximize = false,
  maximized = false,
  onToggleMaximize,
  onDelete,
}: ModelResponsePanelProps) {
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function remove() {
    if (!onDelete || deleteBusy) return;
    const confirmed = await confirmDialog({
      title: "Delete this model response?",
      message: "Only this response will be removed. Sibling model responses remain visible.",
      confirmLabel: "Delete Dialog",
      danger: true,
    });
    if (!confirmed) return;
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await onDelete();
    } catch (cause) {
      setDeleteError(cause instanceof Error ? cause.message : "Delete failed");
    } finally {
      setDeleteBusy(false);
    }
  }

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
          <ResponseLikeButton
            responseId={responseId}
            initialCount={likeCount}
            initialLiked={likedByMe}
            controlled={likeControlled}
          />
          {anonymousLikeCount > 0 ? (
            <span
              className="inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-xs text-stone-400"
              title={`${anonymousLikeCount} anonymous thumbs up from shared viewers`}
            >
              <ThumbsUp className="h-3.5 w-3.5" />
              {anonymousLikeCount}
            </span>
          ) : null}
          {(status === "complete" || status === "error" || capabilityMatrix) && (
            <ResponseDetails
              latencyMs={latencyMs}
              inputTokens={inputTokens}
              outputTokens={outputTokens}
              cacheReadTokens={cacheReadTokens}
              cacheWriteTokens={cacheWriteTokens}
              capabilityMatrix={capabilityMatrix}
              hasUsage={status === "complete" || status === "error"}
            />
          )}
          {(status === "complete" || status === "error") && text && (
            <CopyButton text={text} />
          )}
          {status === "error" && onRetry && (
            <button
              type="button"
              onClick={onRetry}
              disabled={retrying}
              className="inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-xs font-medium text-red-600 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50"
              title="Retry this model"
            >
              <RotateCcw className={`h-3.5 w-3.5 ${retrying ? "animate-spin" : ""}`} />
              Retry
            </button>
          )}
          {responseId && onDelete && status !== "streaming" && (
            <button
              type="button"
              onClick={() => void remove()}
              disabled={deleteBusy}
              className="inline-flex items-center rounded-md p-1 text-stone-400 transition hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
              title="Delete this response"
              aria-label="Delete response"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          )}
          {canMaximize && onToggleMaximize && (
            <button
              type="button"
              onClick={onToggleMaximize}
              className="inline-flex items-center rounded-md p-1 text-stone-400 transition hover:bg-stone-100 hover:text-stone-700"
              title={maximized ? "Restore side-by-side" : "Maximize this response"}
              aria-label={maximized ? "Restore side-by-side" : "Maximize this response"}
              aria-pressed={maximized}
            >
              {maximized ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
            </button>
          )}
        </div>
      </div>

      {capabilityNotice && (
        <div className="border-b border-amber-100 bg-amber-50 px-4 py-1.5 text-xs text-amber-700">
          ⚠ {capabilityNotice}
        </div>
      )}
      {deleteError && <div role="alert" className="border-b border-red-100 bg-red-50 px-4 py-2 text-xs text-red-700">{deleteError}</div>}

      <div className="min-h-[60px] flex-1 px-4 py-3 text-sm">
        <ToolStatusIndicator toolCalls={toolCalls} />
        {status === "error" ? (
          <span className="text-red-600">{errorMessage ?? "An error occurred"}</span>
        ) : (
          <>
            <ThinkingBlock
              reasoning={reasoning}
              autoOpen={status === "streaming" && !text}
            />
            <ExpandableContent forceExpanded={status === "streaming"}>
              <StreamingMarkdown content={text} debounceMs={100} complete={status === "complete"} />
            </ExpandableContent>
            {status === "streaming" && <span className="animate-pulse text-stone-400">▋</span>}
          </>
        )}
      </div>
    </div>
  );
}
