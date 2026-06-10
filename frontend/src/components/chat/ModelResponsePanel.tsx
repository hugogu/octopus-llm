"use client";

import { useState } from "react";
import type { CapabilityMatrix } from "@/lib/types/api";
import StreamingMarkdown from "./StreamingMarkdown";

interface ModelResponsePanelProps {
  modelId: string;
  displayName?: string;
  text: string;
  status: "idle" | "streaming" | "complete" | "error";
  errorMessage?: string;
  inputTokens?: number;
  outputTokens?: number;
  latencyMs?: number;
  capabilityNotice?: string;
  capabilityMatrix?: CapabilityMatrix;
}

export default function ModelResponsePanel({
  modelId,
  displayName,
  text,
  status,
  errorMessage,
  inputTokens,
  outputTokens,
  latencyMs,
  capabilityNotice,
  capabilityMatrix,
}: ModelResponsePanelProps) {
  const [showCaps, setShowCaps] = useState(false);

  return (
    <div className="border rounded-lg flex flex-col gap-0 overflow-hidden bg-white shadow-sm">
      <div className="flex items-center justify-between px-3 py-2 border-b bg-gray-50">
        <span className="font-medium text-sm truncate">{displayName ?? modelId}</span>
        <div className="flex items-center gap-2 ml-2">
          {capabilityMatrix && (
            <button
              type="button"
              onClick={() => setShowCaps((v) => !v)}
              className="text-xs text-gray-400 hover:text-gray-600"
              title="Show capabilities"
            >
              ⓘ
            </button>
          )}
          <span className="text-xs">
            {status === "streaming" && (
              <span className="animate-pulse text-blue-500">●</span>
            )}
            {status === "complete" && (
              <span className="text-green-500">✓</span>
            )}
            {status === "error" && (
              <span className="text-red-500">✗</span>
            )}
          </span>
        </div>
      </div>

      {showCaps && capabilityMatrix && (
        <div className="px-3 py-2 bg-gray-50 border-b text-xs text-gray-600 flex flex-col gap-1">
          <p><strong>Input:</strong> {capabilityMatrix.input_modalities.join(", ")}</p>
          <p><strong>Output:</strong> {capabilityMatrix.output_modalities.join(", ")}</p>
          {capabilityMatrix.context_length_tokens && (
            <p><strong>Context:</strong> {capabilityMatrix.context_length_tokens.toLocaleString()} tokens</p>
          )}
          <div className="flex gap-2">
            {capabilityMatrix.supports_streaming && <span className="bg-blue-100 text-blue-700 px-1 rounded">streaming</span>}
            {capabilityMatrix.supports_function_calling && <span className="bg-blue-100 text-blue-700 px-1 rounded">functions</span>}
            {capabilityMatrix.supports_video_input && <span className="bg-blue-100 text-blue-700 px-1 rounded">video</span>}
          </div>
        </div>
      )}

      {capabilityNotice && (
        <div className="px-3 py-1 bg-yellow-50 border-b text-xs text-yellow-700">
          ⚠ {capabilityNotice}
        </div>
      )}

      <div className="px-3 py-2 text-sm min-h-[60px] flex-1">
        {status === "error" ? (
          <span className="text-red-600">{errorMessage ?? "An error occurred"}</span>
        ) : (
          <>
            <StreamingMarkdown content={text} debounceMs={100} />
            {status === "streaming" && <span className="animate-pulse">▋</span>}
          </>
        )}
      </div>

      {status === "complete" && (latencyMs !== undefined || inputTokens !== undefined) && (
        <div className="px-3 py-1 border-t text-xs text-gray-400 flex gap-3">
          {latencyMs !== undefined && <span>{(latencyMs / 1000).toFixed(2)}s</span>}
          {inputTokens !== undefined && <span>in:{inputTokens}</span>}
          {outputTokens !== undefined && <span>out:{outputTokens}</span>}
        </div>
      )}
    </div>
  );
}
