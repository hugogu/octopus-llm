'use client';

import { useEffect, useSyncExternalStore } from 'react';
import { AlertTriangle } from 'lucide-react';
import {
  getConfirmSnapshot,
  settleConfirm,
  subscribeConfirm,
} from '@/lib/ui/confirm';

/**
 * Single host for {@link confirmDialog}. Mounted once at the app root; it renders the styled
 * confirmation dialog whenever a request is pending. Replaces the native `window.confirm`
 * everywhere so confirmations match the design system (Constitution VIII).
 */
export default function ConfirmHost() {
  const request = useSyncExternalStore(subscribeConfirm, getConfirmSnapshot, () => null);

  useEffect(() => {
    if (!request) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') settleConfirm(request.id, false);
      if (e.key === 'Enter') settleConfirm(request.id, true);
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [request]);

  if (!request) return null;

  const {
    id,
    title,
    message,
    confirmLabel = 'Confirm',
    cancelLabel = 'Cancel',
    danger = false,
  } = request;

  return (
    <div
      onClick={() => settleConfirm(id, false)}
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4 backdrop-blur-sm"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-2xl"
      >
        <div className="flex items-start gap-3 px-6 pt-6">
          {danger && (
            <span className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-red-50 text-red-600">
              <AlertTriangle className="h-5 w-5" />
            </span>
          )}
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-stone-900">{title}</h2>
            {message && <p className="mt-1.5 text-sm leading-relaxed text-stone-600">{message}</p>}
          </div>
        </div>
        <div className="mt-6 flex justify-end gap-2 border-t border-stone-100 bg-stone-50/60 px-6 py-4">
          <button
            type="button"
            autoFocus
            onClick={() => settleConfirm(id, false)}
            className="rounded-lg border border-stone-200 bg-white px-4 py-2 text-sm font-medium text-stone-600 transition hover:bg-stone-100"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={() => settleConfirm(id, true)}
            className={`rounded-lg px-4 py-2 text-sm font-medium text-white shadow-sm transition ${
              danger
                ? 'bg-red-600 hover:bg-red-700'
                : 'bg-[#c96442] hover:bg-[#b55538]'
            }`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
