'use client';

import React, { useEffect, useMemo, useState } from 'react';
import MarkdownBlock from './MarkdownBlock';

interface StreamingMarkdownProps {
  content: string;
  debounceMs?: number;
  className?: string;
  complete?: boolean;
}

/**
 * Split markdown into top-level blocks (separated by blank lines), respecting open code fences so a
 * fenced block is never split. Earlier blocks settle into stable strings as the message grows; only
 * the trailing block keeps changing. Combined with the memoized MarkdownBlock, this means settled
 * blocks are parsed once instead of re-parsing the whole message on every streaming tick.
 */
function splitIntoBlocks(text: string): string[] {
  const blocks: string[] = [];
  let current: string[] = [];
  let inFence = false;
  for (const line of text.split('\n')) {
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence;
      current.push(line);
      continue;
    }
    if (!inFence && line.trim() === '') {
      if (current.length > 0) {
        blocks.push(current.join('\n'));
        current = [];
      }
      continue;
    }
    current.push(line);
  }
  if (current.length > 0) blocks.push(current.join('\n'));
  return blocks;
}

export default function StreamingMarkdown({
  content,
  debounceMs = 80,
  className = '',
  complete = false,
}: StreamingMarkdownProps) {
  const [display, setDisplay] = useState(content);

  useEffect(() => {
    const timer = setTimeout(() => setDisplay(content), debounceMs);
    return () => clearTimeout(timer);
  }, [content, debounceMs]);

  // While streaming, render block-by-block (only the trailing block re-parses). Once complete, render
  // the whole message in one canonical pass so any cross-block markdown (e.g. loose lists) is exact.
  const blocks = useMemo(
    () => (complete ? [content] : splitIntoBlocks(display)),
    [complete, content, display],
  );

  return (
    <div className={`prose dark:prose-invert max-w-none ${className}`}>
      {blocks.map((block, index) => (
        <MarkdownBlock key={index} content={block} />
      ))}
    </div>
  );
}
