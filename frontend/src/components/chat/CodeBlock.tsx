'use client';

import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import CopyButton from '@/components/ui/CopyButton';

/** Max rendered height of a single fenced block before it scrolls internally (FR-001). */
export const MAX_BLOCK_HEIGHT = 360;

interface CodeBlockProps {
  code: string;
  language?: string;
  /** When false, render only the bounded highlighter (no language/copy header) — used inside
   *  BlockViewToggle, which provides its own toolbar. Defaults to true. */
  showHeader?: boolean;
}

/**
 * A single fenced code block: a dark language/copy header plus a height-capped, internally-scrolling
 * syntax-highlighted body. Replaces the unbounded inline highlighter so one large block can no longer
 * stretch the whole message (FR-001), and gives every block its own copy control (FR-003).
 */
export default function CodeBlock({ code, language, showHeader = true }: CodeBlockProps) {
  const body = (
    <div className="overflow-auto" style={{ maxHeight: MAX_BLOCK_HEIGHT }}>
      <SyntaxHighlighter
        language={language || 'text'}
        style={oneDark}
        PreTag="div"
        customStyle={{ margin: 0, borderRadius: 0 }}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  );

  if (!showHeader) return body;

  return (
    <div className="my-3 overflow-hidden rounded-lg border border-stone-700/30">
      <div className="flex items-center justify-between bg-gray-800 px-4 py-1.5 text-xs text-gray-400">
        <span>{language || 'text'}</span>
        <CopyButton text={code} className="!text-gray-400 hover:!bg-white/10 hover:!text-gray-100" />
      </div>
      {body}
    </div>
  );
}
