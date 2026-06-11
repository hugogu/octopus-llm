/**
 * Normalizes the LaTeX delimiter conventions emitted by different AI
 * providers into the single `$` / `$$` convention understood by remark-math.
 *
 * Handled input formats:
 * - `\( ... \)`            → `$ ... $`    (OpenAI / DeepSeek inline style)
 * - `\[ ... \]`            → `$$ ... $$`  (OpenAI / DeepSeek display style)
 * - bare `\begin{align} ... \end{align}` → wrapped in `$$ ... $$`
 * - `$ ... $` and `$$ ... $$` pass through untouched
 *
 * Fenced code blocks and inline code spans are preserved verbatim.
 */

type Segment = { type: "code" | "text"; value: string };

// Fenced blocks (``` or ~~~, possibly unterminated while streaming) and
// inline code spans. Order matters: fences are matched before inline code.
const PROTECTED_REGION_PATTERN =
  /(^|\n)(```|~~~)[\s\S]*?(?:\n\2[^\S\n]*(?=\n|$)|$)|`[^`\n]*`/g;

// LaTeX display environments that KaTeX can render but only inside math
// mode; some providers emit them bare, without surrounding delimiters.
const BARE_ENVIRONMENT_PATTERN =
  /\\begin\{(equation|align|gather|multline|alignat|flalign|eqnarray|CD)(\*?)\}[\s\S]*?\\end\{\1\2\}/g;

// Existing display math, masked so bare-environment wrapping never nests.
const DISPLAY_MATH_SPLIT_PATTERN = /(\$\$[\s\S]*?\$\$)/g;

/** Splits content into code segments (kept verbatim) and text segments. */
function splitProtectedRegions(content: string): Segment[] {
  const segments: Segment[] = [];
  let lastIndex = 0;

  for (const match of content.matchAll(PROTECTED_REGION_PATTERN)) {
    const index = match.index ?? 0;
    if (index > lastIndex) {
      segments.push({ type: "text", value: content.slice(lastIndex, index) });
    }
    segments.push({ type: "code", value: match[0] });
    lastIndex = index + match[0].length;
  }

  if (lastIndex < content.length) {
    segments.push({ type: "text", value: content.slice(lastIndex) });
  }

  return segments;
}

/** `\[ ... \]` → `$$ ... $$` (display math, may span lines). */
function convertBracketDelimiters(text: string): string {
  return text.replace(/\\\[\s*([\s\S]*?)\s*\\\]/g, (_match, expression: string) => {
    return `$$\n${expression.trim()}\n$$`;
  });
}

/** `\( ... \)` → `$ ... $` (inline math, may span a line break). */
function convertParenDelimiters(text: string): string {
  return text.replace(/\\\(([\s\S]+?)\\\)/g, (_match, expression: string) => {
    return `$${expression.trim()}$`;
  });
}

/** Wraps bare `\begin{env} ... \end{env}` blocks in `$$`, skipping any
 *  that already sit inside `$$ ... $$`. */
function wrapBareEnvironments(text: string): string {
  return text
    .split(DISPLAY_MATH_SPLIT_PATTERN)
    .map((part) => {
      if (part.startsWith("$$")) return part;
      return part.replace(BARE_ENVIRONMENT_PATTERN, (environment) => `$$\n${environment}\n$$`);
    })
    .join("");
}

const TEXT_TRANSFORMS: Array<(text: string) => string> = [
  convertBracketDelimiters,
  convertParenDelimiters,
  wrapBareEnvironments,
];

/**
 * Rewrites all supported math delimiter styles to `$` / `$$` so a single
 * remark-math pass can parse output from any provider.
 */
export function normalizeMathDelimiters(content: string): string {
  return splitProtectedRegions(content)
    .map((segment) => {
      if (segment.type === "code") return segment.value;
      return TEXT_TRANSFORMS.reduce((text, transform) => transform(text), segment.value);
    })
    .join("");
}
