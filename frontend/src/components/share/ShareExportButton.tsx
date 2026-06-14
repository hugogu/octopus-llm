'use client';

import { useEffect, useRef, useState } from 'react';
import { Download } from 'lucide-react';
import QRCode from 'qrcode';
import { toPng } from 'html-to-image';
import MarkdownRenderer from '@/components/chat/MarkdownRenderer';
import type { SharedSession } from '@/lib/types/api';

/**
 * Long-image (poster) export for the share page (US6). Renders an off-screen, fully-expanded poster of
 * the conversation — a QR code (encoding this share URL) in the top-right with the content below — in
 * the platform visual style, then rasterizes it to a downloadable PNG (html-to-image).
 *
 * The poster is always mounted off-screen so diagram previews (Mermaid/SVG) have rendered by capture
 * time. HTML blocks render as source in the poster (they are not executed), and nothing is collapsed,
 * so the image carries the full content (FR-022).
 */
export default function ShareExportButton({ session }: { session: SharedSession }) {
  const posterRef = useRef<HTMLDivElement>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    QRCode.toDataURL(window.location.href, { width: 180, margin: 1 })
      .then(setQr)
      .catch(() => setQr(null));
  }, []);

  const handleExport = async () => {
    const node = posterRef.current;
    if (!node) return;
    setBusy(true);
    setError(false);
    try {
      // Let any diagram previews finish rendering before rasterizing.
      await new Promise((resolve) => setTimeout(resolve, 700));
      const dataUrl = await toPng(node, { pixelRatio: 2, cacheBust: true, backgroundColor: '#faf9f5' });
      const link = document.createElement('a');
      link.href = dataUrl;
      link.download = `octopus-share-${Date.now()}.png`;
      link.click();
    } catch (cause) {
      console.error('Long-image export failed:', cause);
      setError(true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={handleExport}
        disabled={busy}
        className="inline-flex items-center gap-1.5 rounded-lg border border-stone-200 bg-white px-3 py-1.5 text-sm font-medium text-stone-700 shadow-sm transition-colors hover:border-[#c96442] hover:text-[#b75536] disabled:cursor-not-allowed disabled:opacity-60"
      >
        <Download className="h-4 w-4" />
        {busy ? 'Generating…' : error ? 'Retry export' : 'Long image'}
      </button>

      {/* Off-screen poster (laid out but off-screen so html-to-image can capture it). */}
      <div aria-hidden className="pointer-events-none fixed -left-[10000px] top-0">
        <div
          ref={posterRef}
          style={{ width: 860 }}
          className="bg-[radial-gradient(circle_at_top_left,_#f8e9dc,_transparent_30%),linear-gradient(180deg,#faf9f5,#f2f0e8)] p-10"
        >
          <header className="mb-8 flex items-start justify-between gap-6">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#b75536]">Shared conversation</p>
              <h1 className="mt-1 text-3xl font-semibold text-stone-900">
                {session.title || 'Untitled conversation'}
              </h1>
              <p className="mt-2 text-sm text-stone-500">Scan the code to open this conversation.</p>
            </div>
            {qr && (
              <img
                src={qr}
                alt="QR code linking to this shared conversation"
                width={120}
                height={120}
                className="shrink-0 rounded-xl border border-stone-200 bg-white p-1.5 shadow-sm"
              />
            )}
          </header>

          <div className="space-y-8">
            {session.turns.map((turn) => (
              <section key={turn.sequenceNum} className="space-y-3">
                <div className="ml-auto w-fit max-w-2xl rounded-2xl bg-[#30302e] px-4 py-3 text-white">
                  <MarkdownRenderer content={turn.promptText} className="text-sm [&_*]:text-white" />
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  {turn.responses.map((response) => (
                    <article key={response.responseId} className="overflow-hidden rounded-xl border border-stone-200 bg-white shadow-sm">
                      <header className="border-b border-stone-100 bg-stone-50 px-4 py-2">
                        <span className="truncate text-sm font-semibold text-stone-800">{response.modelDisplayName}</span>
                      </header>
                      <div className="p-4 text-sm">
                        {response.status === 'error' ? (
                          <p className="text-red-600">{response.errorMessage}</p>
                        ) : (
                          <MarkdownRenderer content={response.responseText ?? ''} />
                        )}
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            ))}
            {session.turns.length === 0 && (
              <p className="rounded-xl border border-stone-200 bg-white px-4 py-6 text-center text-sm text-stone-500">
                This conversation has no messages yet.
              </p>
            )}
          </div>

          <footer className="mt-8 border-t border-stone-200 pt-4 text-xs text-stone-400">
            Shared via Octopus LLM
          </footer>
        </div>
      </div>
    </>
  );
}
