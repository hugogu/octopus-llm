'use client';

/**
 * Runs a self-contained HTML/CSS/JS artifact inside an isolated sandboxed iframe (US3).
 *
 * Security (FR-009 / Q4): the `sandbox` attribute deliberately OMITS `allow-same-origin`, which forces
 * the frame into an opaque origin — it cannot read the viewer's cookies, `localStorage`, the auth
 * token, or navigate/alter the host app. `srcDoc` keeps the content off any real origin. Outbound
 * network is still permitted (default iframe behavior) so CDN assets load.
 *
 * This component only mounts after the user explicitly switches to the "Run" view (FR-010); reaching it
 * is the deliberate run action, so no content auto-executes in the default source view.
 */
const SANDBOX = 'allow-scripts allow-popups allow-forms allow-modals';

export default function RunnableArtifact({ html }: { html: string }) {
  return (
    <iframe
      title="Runnable artifact"
      sandbox={SANDBOX}
      srcDoc={html}
      className="h-80 w-full border-0 bg-white"
    />
  );
}
