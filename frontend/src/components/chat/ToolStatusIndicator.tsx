"use client";

import { useState } from "react";
import { Check, ChevronDown, Loader2, Wrench, X } from "lucide-react";
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

const pretty = (value: unknown): string => {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
};

function DetailBlock({ label, body, tone = "stone" }: { label: string; body: string; tone?: "stone" | "red" }) {
  return (
    <div>
      <p className="mb-0.5 font-semibold text-stone-500">{label}</p>
      <pre
        className={`max-h-40 overflow-auto whitespace-pre-wrap break-words rounded-md border p-1.5 text-[11px] ${
          tone === "red" ? "border-red-100 bg-red-50 text-red-700" : "border-stone-200 bg-stone-50 text-stone-700"
        }`}
      >
        {body}
      </pre>
    </div>
  );
}

/**
 * Renders the tool calls a model made as compact status chips (feature 009). Each chip is expandable to
 * show what was requested (arguments) and what came back (result JSON) or why it failed (error), so a
 * timeout/failure can be inspected instead of vanishing when the answer completes.
 */
export default function ToolStatusIndicator({ toolCalls }: { toolCalls?: ToolCallState[] }) {
  const [openId, setOpenId] = useState<string | null>(null);
  if (!toolCalls || toolCalls.length === 0) return null;

  return (
    <div className="mb-2 flex flex-col gap-1.5" aria-label="工具调用状态">
      {toolCalls.map((call) => {
        const style = STATUS_STYLE[call.status] ?? STATUS_STYLE.pending;
        const open = openId === call.callId;
        const hasDetail = Boolean(call.arguments || call.result || call.error);
        return (
          <div key={call.callId} className="flex flex-col">
            <button
              type="button"
              onClick={() => hasDetail && setOpenId(open ? null : call.callId)}
              aria-expanded={hasDetail ? open : undefined}
              title={call.error ?? undefined}
              className={`inline-flex w-fit max-w-full items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-medium ${style.className} ${
                hasDetail ? "cursor-pointer hover:brightness-95" : "cursor-default"
              }`}
            >
              <Wrench className="h-3 w-3 shrink-0 opacity-70" aria-hidden />
              <span className="max-w-[12rem] truncate font-mono">{call.toolName}</span>
              <span className="inline-flex items-center gap-1 opacity-90">
                <StatusIcon status={call.status} />
                {style.label}
              </span>
              {hasDetail && (
                <ChevronDown
                  className={`h-3 w-3 shrink-0 opacity-60 transition-transform ${open ? "rotate-180" : ""}`}
                  aria-hidden
                />
              )}
            </button>
            {open && hasDetail && (
              <div className="mt-1 max-w-md space-y-1.5 rounded-lg border border-stone-200 bg-white p-2 text-xs shadow-sm">
                {call.arguments && Object.keys(call.arguments).length > 0 && (
                  <DetailBlock label="请求参数" body={pretty(call.arguments)} />
                )}
                {call.error ? (
                  <DetailBlock label="错误" body={call.error} tone="red" />
                ) : call.result ? (
                  <DetailBlock label="返回结果" body={pretty(call.result)} />
                ) : null}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
