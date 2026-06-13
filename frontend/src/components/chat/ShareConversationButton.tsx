"use client";

import { useEffect, useState } from "react";
import { Check, Link as LinkIcon, X } from "lucide-react";
import { getToken } from "@/lib/api/auth";
import { createShare, listShares, revokeShare } from "@/lib/api/shares";
import type { ShareLink } from "@/lib/types/api";

export default function ShareConversationButton({ sessionId }: { sessionId: string }) {
  const [shares, setShares] = useState<ShareLink[]>([]);
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [busy, setBusy] = useState(false);
  const active = shares.find((share) => !share.revokedAt);

  useEffect(() => {
    const token = getToken();
    if (!token) return;
    listShares(sessionId, token).then((page) => setShares(page.items)).catch(() => {});
  }, [sessionId]);

  async function createOrCopy() {
    const token = getToken();
    if (!token) return;
    setBusy(true);
    try {
      const share = active ?? await createShare(sessionId, token);
      if (!active) setShares((current) => [share, ...current]);
      await navigator.clipboard.writeText(`${window.location.origin}${share.shareUrl}`);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } finally {
      setBusy(false);
    }
  }

  async function revoke(share: ShareLink) {
    const token = getToken();
    if (!token) return;
    setBusy(true);
    try {
      await revokeShare(sessionId, share.token, token);
      setShares((current) => current.map((item) => item.token === share.token ? { ...item, revokedAt: new Date().toISOString() } : item));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="relative">
      <button type="button" disabled={busy} onClick={() => setOpen((value) => !value)} className="flex items-center gap-1.5 rounded-lg border border-stone-200 bg-white px-3 py-1.5 text-xs font-medium text-stone-600 hover:text-stone-900 disabled:opacity-50">
        <LinkIcon className="h-3.5 w-3.5" /> Share
      </button>
      {open ? (
        <div className="absolute right-0 top-10 z-20 w-80 rounded-xl border border-stone-200 bg-white p-3 shadow-xl">
          <div className="flex items-center justify-between"><p className="text-sm font-semibold text-stone-900">Conversation share</p><button onClick={() => setOpen(false)} aria-label="Close"><X className="h-4 w-4" /></button></div>
          <p className="mt-1 text-xs text-stone-500">Anyone with the opaque link can read this conversation until you revoke it.</p>
          <button onClick={() => void createOrCopy()} disabled={busy} className="mt-3 flex w-full items-center justify-center gap-1.5 rounded-lg bg-[#c96442] px-3 py-2 text-sm font-medium text-white disabled:opacity-50">
            {copied ? <Check className="h-4 w-4" /> : <LinkIcon className="h-4 w-4" />}
            {copied ? "Copied" : active ? "Copy active link" : "Create and copy link"}
          </button>
          {shares.length ? <div className="mt-3 space-y-2">{shares.map((share) => <div key={share.token} className="flex items-center justify-between rounded-lg bg-stone-50 p-2 text-xs"><span className="truncate text-stone-500">{share.revokedAt ? "Revoked" : "Active"} · {new Date(share.createdAt).toLocaleDateString()}</span>{!share.revokedAt ? <button onClick={() => void revoke(share)} className="font-medium text-red-600">Revoke</button> : null}</div>)}</div> : null}
        </div>
      ) : null}
    </div>
  );
}
