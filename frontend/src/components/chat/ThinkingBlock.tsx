'use client';

import { useState } from 'react';
import { Brain, ChevronDown, ChevronRight } from 'lucide-react';
import MarkdownRenderer from './MarkdownRenderer';

interface ThinkingBlockProps {
  reasoning: string;
  /** Open the block without user interaction, e.g. while reasoning streams in. */
  autoOpen?: boolean;
}

/**
 * Collapsible "thought process" section shown above a model response.
 * Auto-opens while reasoning streams; once the user toggles it manually,
 * their choice wins.
 */
export default function ThinkingBlock({ reasoning, autoOpen = false }: ThinkingBlockProps) {
  const [userToggle, setUserToggle] = useState<boolean | null>(null);
  const open = userToggle ?? autoOpen;

  if (!reasoning) return null;

  return (
    <div className="mb-3 rounded-lg border border-stone-200 bg-stone-50/80">
      <button
        type="button"
        onClick={() => setUserToggle(!open)}
        className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs font-medium text-stone-500 hover:text-stone-700"
      >
        <Brain className="h-3.5 w-3.5 shrink-0" />
        <span>Thought process</span>
        {open ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
      </button>
      {open && (
        <div className="border-t border-stone-200 px-3 py-2">
          <MarkdownRenderer
            content={reasoning}
            className="text-xs [&_p]:mb-2 [&_p]:text-stone-500 [&_li]:text-stone-500"
          />
        </div>
      )}
    </div>
  );
}
