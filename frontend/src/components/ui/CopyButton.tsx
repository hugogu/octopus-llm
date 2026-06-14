'use client';

import { useState, useRef, useEffect } from 'react';
import { Check, Copy, X } from 'lucide-react';

interface CopyButtonProps {
  text: string;
  label?: string;
  className?: string;
}

/**
 * Copies `text` to the clipboard. Flashes a check mark on success and a clear error state on failure
 * (clipboard unavailable / denied, e.g. an insecure context) instead of failing silently — FR-003.
 */
export default function CopyButton({ text, label, className = '' }: CopyButtonProps) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle');
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current);
  }, []);

  const flash = (next: 'copied' | 'failed') => {
    setState(next);
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setState('idle'), 2000);
  };

  const handleCopy = async () => {
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error('Clipboard API unavailable');
      }
      await navigator.clipboard.writeText(text);
      flash('copied');
    } catch (err) {
      console.error('Copy failed:', err);
      flash('failed');
    }
  };

  const title = state === 'copied' ? 'Copied' : state === 'failed' ? 'Copy failed' : 'Copy';
  const tone =
    state === 'failed'
      ? 'text-red-500 hover:text-red-600'
      : 'text-stone-400 hover:bg-stone-100 hover:text-stone-700';

  return (
    <button
      type="button"
      onClick={handleCopy}
      title={title}
      aria-label={title}
      className={`flex items-center gap-1 rounded-md px-1.5 py-1 text-xs transition-colors ${tone} ${className}`}
    >
      {state === 'copied' ? (
        <Check className="h-3.5 w-3.5 text-green-600" />
      ) : state === 'failed' ? (
        <X className="h-3.5 w-3.5" />
      ) : (
        <Copy className="h-3.5 w-3.5" />
      )}
      {label && <span>{state === 'copied' ? 'Copied' : state === 'failed' ? 'Failed' : label}</span>}
    </button>
  );
}
