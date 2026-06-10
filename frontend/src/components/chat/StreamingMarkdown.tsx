'use client';

import React, { useState, useEffect, useCallback } from 'react';
import MarkdownRenderer from './MarkdownRenderer';

interface StreamingMarkdownProps {
  content: string;
  debounceMs?: number;
  className?: string;
}

export default function StreamingMarkdown({ 
  content, 
  debounceMs = 100, 
  className = '' 
}: StreamingMarkdownProps) {
  const [displayContent, setDisplayContent] = useState(content);
  const [error, setError] = useState<Error | null>(null);

  const debouncedUpdate = useCallback(
    (newContent: string) => {
      const timer = setTimeout(() => {
        try {
          setDisplayContent(newContent);
          setError(null);
        } catch (err) {
          setError(err instanceof Error ? err : new Error('Render failed'));
        }
      }, debounceMs);
      
      return () => clearTimeout(timer);
    },
    [debounceMs]
  );

  useEffect(() => {
    const cleanup = debouncedUpdate(content);
    return cleanup;
  }, [content, debouncedUpdate]);

  if (error) {
    return (
      <div className={`text-gray-700 dark:text-gray-300 whitespace-pre-wrap font-mono text-sm ${className}`}>
        {content}
      </div>
    );
  }

  return (
    <div className={className}>
      <MarkdownRenderer content={displayContent} />
    </div>
  );
}
