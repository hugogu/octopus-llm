'use client';

import { useEffect, useState } from 'react';
import { sanitizeSvg } from '@/lib/markdown/svg';
import { PreviewError, PreviewLoading } from './previewStates';

/**
 * Render an SVG block as a sanitized inline image-like preview (FR-011). Sanitization runs in an effect
 * (client-only) so DOMPurify never touches `window` during server rendering. Any embedded scripting is
 * stripped by {@link sanitizeSvg}.
 */
export default function SvgPreview({ code }: { code: string }) {
  const [clean, setClean] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      setClean(sanitizeSvg(code));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid SVG');
    }
  }, [code]);

  if (error) return <PreviewError message={error} />;
  if (clean === null) return <PreviewLoading />;
  return (
    <div
      className="flex justify-center overflow-auto bg-white p-3 [&_svg]:max-w-full"
      dangerouslySetInnerHTML={{ __html: clean }}
    />
  );
}
