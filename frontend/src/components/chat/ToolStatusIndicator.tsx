"use client";

import { Check, Loader2, Wrench, X } from "lucide-react";
import type { ToolCallState, ToolCallStatus } from "@/lib/types/api";

const STATUS_STYLE: Record<ToolCallStatus, { className: string; label: string }> = {
  pending: { className: "border-stone-200 bg-stone-50 text-stone-600", label: "排队中" },
  running: { className: "border-blue-200 bg-blue-50 text-blue-700", label: "调用中" },
  success: { className: "border-green-200 bg-green-50 text-green-700", label: "已完成" },
  failed: { className: "border-red-200 bg-red-50 text-red-700", label: "失败" },
  timeout: { className: "border-red-200 bg-red-50 text-red-700", label: "超时" },
};

function StatusIcon({ status }: { status: ToolCallStatus }) {
  if (status === "pending" || status === "running") {
    return <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin" aria-hidden />;
  }
  if (status === "success") return <Check className="h-3.5 w-3.5 shrink-0" aria-hidden />;
  return <X className="h-3.5 w-3.5 shrink-0" aria-hidden />;
}

/**
 * Renders the tool calls a model made this turn as compact status chips (feature 009). Each chip shows
 * the tool name and its live status; failed/timed-out calls expose the error via the chip title.
 */
export default function ToolStatusIndicator({ toolCalls }: { toolCalls?: ToolCallState[] }) {
  if (!toolCalls || toolCalls.length === 0) return null;

  return (
    <div className="mb-2 flex flex-wrap gap-1.5" aria-label="工具调用状态">
      {toolCalls.map((call) => {
        const style = STATUS_STYLE[call.status] ?? STATUS_STYLE.pending;
        const title = call.error ?? undefined;
        return (
          <span
            key={call.callId}
            title={title}
            className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-medium ${style.className}`}
          >
            <Wrench className="h-3 w-3 shrink-0 opacity-70" aria-hidden />
            <span className="max-w-[12rem] truncate font-mono">{call.toolName}</span>
            <span className="inline-flex items-center gap-1 opacity-90">
              <StatusIcon status={call.status} />
              {style.label}
            </span>
          </span>
        );
      })}
    </div>
  );
}
