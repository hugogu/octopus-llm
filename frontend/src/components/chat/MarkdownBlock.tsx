'use client';

import React from 'react';
import ReactMarkdown from 'react-markdown';
import { markdownComponents, remarkPlugins, rehypePlugins } from './markdownComponents';
import { normalizeMathDelimiters } from '@/lib/markdown/math';

/**
 * Renders a single markdown block (no prose wrapper). Memoized on its content string so that, while
 * streaming, blocks that are already settled are never re-parsed — only the growing trailing block
 * re-renders. This turns the O(n²) "re-parse the whole message every tick" cost into O(n).
 */
const MarkdownBlock = React.memo(function MarkdownBlock({ content }: { content: string }) {
  return (
    <ReactMarkdown remarkPlugins={remarkPlugins} rehypePlugins={rehypePlugins} components={markdownComponents}>
      {normalizeMathDelimiters(content)}
    </ReactMarkdown>
  );
});

export default MarkdownBlock;
