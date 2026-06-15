'use client';

import { useState, type ReactNode } from 'react';
import { Code2, Eye, Maximize2, X } from 'lucide-react';
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
  const [zoomed, setZoomed] = useState(false);

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
        <div className="flex items-center gap-1">
          {view === 'preview' && (
            <button
              type="button"
              onClick={() => setZoomed(true)}
              title="Enlarge"
              aria-label="Enlarge preview"
              className="rounded p-1 text-stone-500 transition-colors hover:bg-stone-200 hover:text-stone-800"
            >
              <Maximize2 className="h-3.5 w-3.5" />
            </button>
          )}
          <CopyButton text={source} />
        </div>
      </div>
      {view === 'preview' ? (
        <div className="overflow-auto" style={{ maxHeight: MAX_BLOCK_HEIGHT }}>
          {preview}
        </div>
      ) : (
        <CodeBlock code={source} language={language} showHeader={false} />
      )}

      {zoomed && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
          onClick={() => setZoomed(false)}
          role="dialog"
          aria-modal="true"
        >
          <div
            className="flex max-h-[92vh] min-h-[300px] w-full min-w-[360px] max-w-[95vw] resize flex-col overflow-hidden rounded-xl bg-white shadow-2xl"
            style={{ width: 'min(1100px, 92vw)', height: '85vh' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-stone-200 bg-stone-100 px-3 py-2">
              <span className="text-xs font-medium text-stone-600">{previewLabel}</span>
              <button
                type="button"
                onClick={() => setZoomed(false)}
                title="Close"
                aria-label="Close enlarged view"
                className="rounded p-1 text-stone-500 transition-colors hover:bg-stone-200 hover:text-stone-800"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            {/* min-h-0 lets the body shrink inside the flex column; the [&>iframe] rules stretch a
                runnable HTML artifact to fill the enlarged (and resized) area, while diagrams/SVG keep
                their natural size and scroll. */}
            <div className="min-h-0 flex-1 overflow-auto p-3 [&>iframe]:h-full [&>iframe]:w-full">
              {preview}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
