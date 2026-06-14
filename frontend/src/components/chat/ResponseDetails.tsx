'use client';

import { useState } from 'react';
import { Info } from 'lucide-react';

interface ResponseDetailsProps {
  latencyMs?: number;
  inputTokens?: number | null;
  outputTokens?: number | null;
  cacheReadTokens?: number | null;
  cacheWriteTokens?: number | null;
}

const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : value.toLocaleString();

/**
 * Per-response usage details (FR-012/013). An Info control opens a small popover with latency, input /
 * output tokens, and the normalized cache-read / cache-write pair. Missing figures (provider didn't
 * report them, or a response generated before cache capture shipped) render as "—" (FR-014). Keeps
 * usage out of the main answer body.
 */
export default function ResponseDetails({
  latencyMs,
  inputTokens,
  outputTokens,
  cacheReadTokens,
  cacheWriteTokens,
}: ResponseDetailsProps) {
  const [open, setOpen] = useState(false);

  const rows: Array<[string, string]> = [
    ['Latency', latencyMs === undefined ? '—' : `${(latencyMs / 1000).toFixed(2)}s`],
    ['Input tokens', fmt(inputTokens)],
    ['Output tokens', fmt(outputTokens)],
    ['Cache read', fmt(cacheReadTokens)],
    ['Cache write', fmt(cacheWriteTokens)],
  ];

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="Response details"
        aria-expanded={open}
        title="Usage details"
        className="rounded-md p-1 text-stone-400 transition-colors hover:bg-stone-100 hover:text-stone-700"
      >
        <Info className="h-3.5 w-3.5" />
      </button>
      {open && (
        <div className="absolute right-0 z-10 mt-1 w-52 rounded-xl border border-stone-200 bg-white p-3 text-xs shadow-lg">
          <p className="mb-2 font-semibold text-stone-700">Usage</p>
          <dl className="space-y-1">
            {rows.map(([label, value]) => (
              <div key={label} className="flex justify-between gap-3">
                <dt className="text-stone-500">{label}</dt>
                <dd className="font-medium tabular-nums text-stone-800">{value}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </div>
  );
}
