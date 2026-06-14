'use client';

import { useEffect, useState } from 'react';
import { renderPlantUml } from '@/lib/api/render';
import { sanitizeSvg } from '@/lib/markdown/svg';
import { PreviewError, PreviewLoading } from './previewStates';

/**
 * Render a PlantUML block via the self-hosted render proxy (Q1). On any failure (renderer unavailable,
 * invalid source) it surfaces a scoped error so the caller can keep the source view available
 * (FR-006a). The returned SVG is sanitized before inlining as defense-in-depth.
 */
export default function PlantUmlPreview({ code }: { code: string }) {
  const [svg, setSvg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setSvg(null);
    setError(null);
    const timer = setTimeout(() => {
      void (async () => {
        try {
          const rendered = await renderPlantUml(code);
          if (!cancelled) setSvg(sanitizeSvg(rendered));
        } catch (err) {
          if (!cancelled) {
            setError(err instanceof Error ? err.message : 'Renderer unavailable');
          }
        }
      })();
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [code]);

  if (error) return <PreviewError message={`${error} — switch to Source to read it.`} />;
  if (!svg) return <PreviewLoading />;
  return (
    <div
      className="flex justify-center overflow-auto bg-white p-3"
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
