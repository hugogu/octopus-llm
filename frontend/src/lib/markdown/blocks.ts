/**
 * Fenced-block classification for the shared markdown pipeline (feature 006).
 *
 * `markdownComponents.code()` calls {@link classifyFence} with the fence language tag and routes the
 * block to one of three render strategies. This is the single place that decides what a fenced block
 * becomes, so the in-app conversation and the public share view stay identical.
 */

/** How a fenced block is rendered. */
export type BlockStrategy =
  /** Bounded, copyable syntax-highlighted code (the default for anything not specially handled). */
  | 'code'
  /** A visual diagram/markup preview shown by default, toggleable to source. */
  | 'diagram-preview'
  /** Active web content: shown as source by default, runs in a sandbox only on explicit user action. */
  | 'html-runnable';

/** Which renderer a `diagram-preview` block uses. */
export type DiagramKind = 'mermaid' | 'plantuml' | 'svg';

export interface BlockClassification {
  /** Normalized (lower-cased, trimmed) fence language, e.g. `mermaid`, `ts`, or `''` when untagged. */
  language: string;
  strategy: BlockStrategy;
  /** Present only when `strategy === 'diagram-preview'`. */
  diagram?: DiagramKind;
}

/**
 * Classify a fence language tag into a render strategy.
 *
 * - `mermaid` → Mermaid diagram preview (client-side).
 * - `plantuml` / `puml` / `uml` → PlantUML preview (via the self-hosted render proxy).
 * - `svg` → sanitized inline SVG preview.
 * - `html` / `htm` → runnable artifact (source by default; explicit run — clarification Q2).
 * - anything else (or untagged) → bounded, copyable code.
 */
export function classifyFence(rawLanguage: string | undefined): BlockClassification {
  const language = (rawLanguage ?? '').trim().toLowerCase();

  switch (language) {
    case 'mermaid':
      return { language, strategy: 'diagram-preview', diagram: 'mermaid' };
    case 'plantuml':
    case 'puml':
    case 'uml':
      return { language, strategy: 'diagram-preview', diagram: 'plantuml' };
    case 'svg':
      return { language, strategy: 'diagram-preview', diagram: 'svg' };
    case 'html':
    case 'htm':
      return { language, strategy: 'html-runnable' };
    default:
      return { language, strategy: 'code' };
  }
}
