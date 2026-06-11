'use client';

import { useState, useRef, useEffect, type ReactNode } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';

interface ExpandableContentProps {
  children: ReactNode;
  /** Collapsed height in pixels; content shorter than this never collapses. */
  collapsedHeight?: number;
  /** Keep fully expanded regardless of height, e.g. while streaming. */
  forceExpanded?: boolean;
}

/**
 * Clamps tall content to a fixed height with a fade-out, plus a
 * "Show more / Show less" toggle. Short content renders unchanged.
 */
export default function ExpandableContent({
  children,
  collapsedHeight = 420,
  forceExpanded = false,
}: ExpandableContentProps) {
  const [expanded, setExpanded] = useState(false);
  const [overflows, setOverflows] = useState(false);
  const innerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = innerRef.current;
    if (!el) return;
    const measure = () => setOverflows(el.scrollHeight > collapsedHeight + 40);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [collapsedHeight, children]);

  const collapsed = overflows && !expanded && !forceExpanded;

  return (
    <div>
      <div
        className={collapsed ? 'relative overflow-hidden' : undefined}
        style={collapsed ? { maxHeight: collapsedHeight } : undefined}
      >
        <div ref={innerRef}>{children}</div>
        {collapsed && (
          <div className="pointer-events-none absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-white to-transparent" />
        )}
      </div>
      {overflows && !forceExpanded && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="mt-1 flex items-center gap-1 text-xs font-medium text-stone-500 hover:text-stone-800"
        >
          {expanded ? (
            <>
              <ChevronUp className="h-3.5 w-3.5" /> Show less
            </>
          ) : (
            <>
              <ChevronDown className="h-3.5 w-3.5" /> Show more
            </>
          )}
        </button>
      )}
    </div>
  );
}
