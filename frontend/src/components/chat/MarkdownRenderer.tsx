'use client';

import React from 'react';
import dynamic from 'next/dynamic';

const MarkdownBlock = dynamic(() => import('./MarkdownBlock'), {
  ssr: false,
  loading: () => null,
});

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

/**
 * Full-message markdown renderer (one parse of the whole content). Used for settled content — saved
 * conversations, the share page, and completed responses. Live streaming uses StreamingMarkdown,
 * which renders block-by-block to stay O(n).
 */
export default function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
  return (
    <div className={`prose dark:prose-invert max-w-none ${className}`}>
      <MarkdownBlock content={content} />
    </div>
  );
}
