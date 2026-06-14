'use client';

import { useState, type ReactNode } from 'react';
import { Code2, Eye } from 'lucide-react';
import CopyButton from '@/components/ui/CopyButton';
import CodeBlock, { MAX_BLOCK_HEIGHT } from './CodeBlock';

interface BlockViewToggleProps {
  source: string;
  language: string;
  /** Which view is shown first: diagrams default to 'preview'; HTML defaults to 'source' (Q2). */
  initialView: 'preview' | 'source';
  /** The rendered preview (diagram / sanitized SVG / run surface). */
  preview: ReactNode;
  /** Short label for the preview tab, e.g. "Diagram" or "Run". */
  previewLabel?: string;
}

/**
 * Chrome for a renderable fenced block: a [Preview | Source] segmented toggle plus a per-block copy
 * control (FR-003), over a body that swaps between the visual preview and the bounded source view
 * (FR-004/FR-005). The preview is height-capped to match code blocks so it never overflows the bubble
 * (FR-007).
 */
export default function BlockViewToggle({
  source,
  language,
  initialView,
  preview,
  previewLabel = 'Preview',
}: BlockViewToggleProps) {
  const [view, setView] = useState<'preview' | 'source'>(initialView);

  const tab = (value: 'preview' | 'source', label: string, icon: ReactNode) => (
    <button
      type="button"
      onClick={() => setView(value)}
      className={`flex items-center gap-1 rounded px-2 py-0.5 text-xs transition-colors ${
        view === value ? 'bg-white text-stone-800 shadow-sm' : 'text-stone-500 hover:text-stone-700'
      }`}
      aria-pressed={view === value}
    >
      {icon}
      {label}
    </button>
  );

  return (
    <div className="my-3 overflow-hidden rounded-lg border border-stone-200">
      <div className="flex items-center justify-between gap-2 border-b border-stone-200 bg-stone-100 px-2 py-1.5">
        <div className="flex items-center gap-1 rounded-md bg-stone-200/70 p-0.5">
          {tab('preview', previewLabel, <Eye className="h-3.5 w-3.5" />)}
          {tab('source', language || 'source', <Code2 className="h-3.5 w-3.5" />)}
        </div>
        <CopyButton text={source} />
      </div>
      {view === 'preview' ? (
        <div className="overflow-auto" style={{ maxHeight: MAX_BLOCK_HEIGHT }}>
          {preview}
        </div>
      ) : (
        <CodeBlock code={source} language={language} showHeader={false} />
      )}
    </div>
  );
}
