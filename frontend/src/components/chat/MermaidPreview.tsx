'use client';

import { useEffect, useState } from 'react';
import { PreviewError, PreviewLoading } from './previewStates';

/** Lazy, one-time Mermaid load — code-split so the library never enters the bundle unless a Mermaid
 *  block is actually rendered. `securityLevel: 'strict'` makes Mermaid sanitize its own SVG output. */
let mermaidPromise: Promise<typeof import('mermaid').default> | null = null;
function loadMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then((mod) => {
      mod.default.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'neutral' });
      return mod.default;
    });
  }
  return mermaidPromise;
}

let idCounter = 0;

/**
 * Render a Mermaid diagram client-side. Debounced so that, while the block is still streaming in,
 * partial/invalid source does not render-error every tick — only settled content renders (R10).
 * Failures degrade to a scoped inline error, never throwing into the surrounding message (FR-006).
 */
export default function MermaidPreview({ code }: { code: string }) {
  const [svg, setSvg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(() => {
      void (async () => {
        try {
          const mermaid = await loadMermaid();
          const { svg: rendered } = await mermaid.render(`mmd-${idCounter++}`, code);
          if (!cancelled) {
            setSvg(rendered);
            setError(null);
          }
        } catch (err) {
          if (!cancelled) {
            setSvg(null);
            setError(err instanceof Error ? err.message : 'Invalid diagram');
          }
        }
      })();
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [code]);

  if (error) return <PreviewError message={error} />;
  if (!svg) return <PreviewLoading />;
  return (
    <div
      className="flex justify-center overflow-auto bg-white p-3"
      // Mermaid sanitizes its own output under securityLevel: 'strict'.
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
