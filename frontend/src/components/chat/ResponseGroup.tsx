"use client";

import { useState } from "react";
import type { CapabilityMatrix } from "@/lib/types/api";
import { getToken } from "@/lib/api/auth";
import { likeResponse, unlikeResponse } from "@/lib/api/reactions";
import ModelResponsePanel from "./ModelResponsePanel";

export interface ResponsePanelData {
  key: string;
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
  likeCount?: number;
  likedByMe?: boolean;
  anonymousLikeCount?: number;
  onRetry?: () => void;
  retrying?: boolean;
}

const responseGridStyle = {
  display: "grid",
  gap: "0.75rem",
  gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 360px), 1fr))",
} as const;

const dotClass = (status: ResponsePanelData["status"]) =>
  status === "streaming"
    ? "animate-pulse bg-blue-500"
    : status === "complete"
      ? "bg-green-500"
      : status === "error"
        ? "bg-red-500"
        : "bg-stone-300";

/**
 * A group of model responses for one chat turn. Adds two cross-panel behaviours on top of
 * {@link ModelResponsePanel}:
 *  - **Maximize** (feature 4): when more than one response is present, any panel can be maximized so
 *    it spans the full width; the others collapse to clickable chips. Only one is maximized at a
 *    time, and toggling it off restores the side-by-side grid.
 *  - **Mutually exclusive likes** (feature 5): liking one response un-likes the viewer's like on the
 *    other responses in the same group.
 */
export default function ResponseGroup({ panels }: { panels: ResponsePanelData[] }) {
  const [maximizedKey, setMaximizedKey] = useState<string | null>(null);
  const [likeOverrides, setLikeOverrides] = useState<Record<string, { count: number; liked: boolean }>>({});
  const [likeBusy, setLikeBusy] = useState(false);

  const likeOf = (panel: ResponsePanelData) =>
    likeOverrides[panel.key] ?? { count: panel.likeCount ?? 0, liked: panel.likedByMe ?? false };

  async function toggleLike(panel: ResponsePanelData) {
    if (!panel.responseId || likeBusy) return;
    const token = getToken();
    if (!token) return;
    setLikeBusy(true);
    try {
      if (likeOf(panel).liked) {
        const next = await unlikeResponse(panel.responseId, token);
        setLikeOverrides((prev) => ({ ...prev, [panel.key]: { count: next.likeCount, liked: next.likedByMe } }));
      } else {
        const liked = await likeResponse(panel.responseId, token);
        const updates: Record<string, { count: number; liked: boolean }> = {
          [panel.key]: { count: liked.likeCount, liked: liked.likedByMe },
        };
        // Mutual exclusion: drop the viewer's like on every sibling that was liked.
        for (const other of panels) {
          if (other.key === panel.key || !other.responseId) continue;
          if (likeOf(other).liked) {
            const r = await unlikeResponse(other.responseId, token);
            updates[other.key] = { count: r.likeCount, liked: r.likedByMe };
          }
        }
        setLikeOverrides((prev) => ({ ...prev, ...updates }));
      }
    } catch {
      // leave state unchanged on failure
    } finally {
      setLikeBusy(false);
    }
  }

  const renderPanel = (panel: ResponsePanelData, maximized: boolean) => {
    const like = likeOf(panel);
    return (
      <ModelResponsePanel
        key={panel.key}
        modelId={panel.modelId}
        displayName={panel.displayName}
        connectionLabel={panel.connectionLabel}
        text={panel.text}
        reasoning={panel.reasoning}
        status={panel.status}
        errorMessage={panel.errorMessage}
        inputTokens={panel.inputTokens}
        outputTokens={panel.outputTokens}
        cacheReadTokens={panel.cacheReadTokens}
        cacheWriteTokens={panel.cacheWriteTokens}
        latencyMs={panel.latencyMs}
        capabilityNotice={panel.capabilityNotice}
        capabilityMatrix={panel.capabilityMatrix}
        responseId={panel.responseId}
        likeCount={panel.likeCount}
        likedByMe={panel.likedByMe}
        anonymousLikeCount={panel.anonymousLikeCount}
        onRetry={panel.onRetry}
        retrying={panel.retrying}
        likeControlled={
          panel.responseId
            ? { count: like.count, liked: like.liked, busy: likeBusy, onToggle: () => void toggleLike(panel) }
            : undefined
        }
        canMaximize={panels.length > 1}
        maximized={maximized}
        onToggleMaximize={() => setMaximizedKey(maximized ? null : panel.key)}
      />
    );
  };

  const maximizedPanel = maximizedKey ? panels.find((p) => p.key === maximizedKey) : undefined;

  if (maximizedPanel && panels.length > 1) {
    const others = panels.filter((p) => p.key !== maximizedPanel.key);
    return (
      <div className="space-y-2">
        <div className="flex flex-wrap gap-2">
          {others.map((p) => (
            <button
              key={p.key}
              type="button"
              onClick={() => setMaximizedKey(p.key)}
              className="inline-flex max-w-[14rem] items-center gap-1.5 rounded-lg border border-stone-200 bg-white px-2.5 py-1.5 text-xs font-medium text-stone-600 shadow-sm transition hover:border-stone-300 hover:text-stone-900"
              title={`Maximize ${p.displayName ?? p.modelId}`}
            >
              <span className={`h-2 w-2 shrink-0 rounded-full ${dotClass(p.status)}`} />
              <span className="truncate">{p.displayName ?? p.modelId}</span>
            </button>
          ))}
        </div>
        {renderPanel(maximizedPanel, true)}
      </div>
    );
  }

  return <div style={responseGridStyle}>{panels.map((panel) => renderPanel(panel, false))}</div>;
}
