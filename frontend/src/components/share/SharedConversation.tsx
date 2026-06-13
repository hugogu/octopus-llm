"use client";

import { useEffect, useState } from "react";
import { Heart } from "lucide-react";
import MarkdownRenderer from "@/components/chat/MarkdownRenderer";
import { getToken } from "@/lib/api/auth";
import { anonymousLike, getSharedSession, sharedNamedLike } from "@/lib/api/shares";
import type { SharedSession, SharedResponse } from "@/lib/types/api";

export default function SharedConversation({ shareToken }: { shareToken: string }) {
  const [session, setSession] = useState<SharedSession | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [namedLiked, setNamedLiked] = useState<Set<string>>(() => new Set());

  useEffect(() => {
    getSharedSession(shareToken).then(setSession).catch((cause) => setError(cause instanceof Error ? cause.message : "Shared conversation unavailable"));
  }, [shareToken]);

  async function like(response: SharedResponse) {
    setBusyId(response.responseId);
    try {
      const authToken = getToken();
      if (authToken) {
        const nextLiked = !namedLiked.has(response.responseId);
        await sharedNamedLike(shareToken, response.responseId, authToken, nextLiked);
        setNamedLiked((current) => {
          const next = new Set(current);
          if (nextLiked) next.add(response.responseId);
          else next.delete(response.responseId);
          return next;
        });
      } else {
        const state = await anonymousLike(shareToken, response.responseId);
        setSession((current) => current ? {
          ...current,
          turns: current.turns.map((turn) => ({
            ...turn,
            responses: turn.responses.map((item) => item.responseId === response.responseId ? {
              ...item,
              anonymousLikeCount: state.anonymousLikeCount,
              likedByThisVisitor: state.likedByThisVisitor,
            } : item),
          })),
        } : current);
      }
    } finally {
      setBusyId(null);
    }
  }

  if (error) return <main className="flex min-h-screen items-center justify-center bg-[#faf9f5] p-6 text-sm text-red-700">{error}</main>;
  if (!session) return <main className="min-h-screen bg-[#faf9f5] p-8"><div className="mx-auto h-64 max-w-4xl animate-pulse rounded-2xl bg-white" /></main>;
  return (
    <main className="min-h-screen bg-[#faf9f5] px-4 py-8 sm:px-6">
      <div className="mx-auto max-w-5xl">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#b75536]">Shared conversation</p>
        <h1 className="mt-1 text-2xl font-semibold text-stone-900">{session.title || "Untitled conversation"}</h1>
        <p className="mt-2 text-sm text-stone-500">Read-only view. Anonymous likes are best-effort deduplicated in this browser.</p>
        <div className="mt-7 space-y-6">
          {session.turns.map((turn) => (
            <section key={turn.sequenceNum} className="space-y-3">
              <div className="ml-auto w-fit max-w-3xl rounded-2xl bg-[#30302e] px-4 py-3 text-white"><MarkdownRenderer content={turn.promptText} className="text-sm [&_*]:text-white" /></div>
              <div className="grid gap-3 md:grid-cols-2">
                {turn.responses.map((response) => (
                  <article key={response.responseId} className="overflow-hidden rounded-xl border border-stone-200 bg-white">
                    <header className="flex items-center justify-between border-b border-stone-100 bg-stone-50 px-4 py-2"><span className="text-sm font-semibold text-stone-800">{response.modelDisplayName}</span><button disabled={busyId === response.responseId || (!getToken() && response.likedByThisVisitor)} onClick={() => void like(response)} className={`flex items-center gap-1 rounded px-2 py-1 text-xs disabled:opacity-60 ${response.likedByThisVisitor || namedLiked.has(response.responseId) ? "text-rose-600" : "text-stone-500"}`}><Heart className={`h-4 w-4 ${response.likedByThisVisitor || namedLiked.has(response.responseId) ? "fill-current" : ""}`} />{response.anonymousLikeCount}</button></header>
                    <div className="p-4 text-sm">{response.status === "error" ? <p className="text-red-600">{response.errorMessage}</p> : <MarkdownRenderer content={response.responseText ?? ""} />}</div>
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      </div>
    </main>
  );
}
