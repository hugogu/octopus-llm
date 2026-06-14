'use client';

/** Shared inline states for renderable-block previews (Mermaid / PlantUML / SVG). */

/** Scoped, non-fatal error shown in place of a single block's preview (FR-006). */
export function PreviewError({ message }: { message: string }) {
  return (
    <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
      Couldn’t render preview — showing nothing for this block. {message}
    </div>
  );
}

/** Lightweight placeholder while a preview computes (sanitize / fetch / diagram render). */
export function PreviewLoading() {
  return <div className="h-16 animate-pulse rounded-md bg-stone-100" />;
}
