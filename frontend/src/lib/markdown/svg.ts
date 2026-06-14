import DOMPurify from 'dompurify';

/**
 * Sanitize an SVG string for safe inline rendering. Inlined SVG executes in the host origin, so any
 * `<script>` / event handlers must be stripped before it touches the DOM (FR-011 / R6).
 *
 * Must run in the browser only — DOMPurify needs `window`. Callers gate this behind an effect/state so
 * it never runs during server rendering of a client component.
 */
export function sanitizeSvg(svg: string): string {
  return DOMPurify.sanitize(svg, {
    USE_PROFILES: { svg: true, svgFilters: true },
  });
}
