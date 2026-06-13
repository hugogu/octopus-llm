"use client";

import { useEffect, useState } from "react";
import { Heart, ThumbsUp } from "lucide-react";
import MarkdownRenderer from "@/components/chat/MarkdownRenderer";
import { getToken } from "@/lib/api/auth";
import { anonymousLike, getSharedSession, sharedNamedLike } from "@/lib/api/shares";
import type { SharedSession, SharedResponse } from "@/lib/types/api";

export default function SharedConversation({ shareToken }: { shareToken: string }) {
  const [session, setSession] = useState<SharedSession | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    getSharedSession(shareToken, getToken() ?? undefined)
      .then(setSession)
      .catch((cause) => setError(cause instanceof Error ? cause.message : "Shared conversation unavailable"));
  }, [shareToken]);

  function patchResponse(responseId: string, patch: Partial<SharedResponse>) {
    setSession((current) => current ? {
      ...current,
      turns: current.turns.map((turn) => ({
        ...turn,
        responses: turn.responses.map((item) => item.responseId === responseId ? { ...item, ...patch } : item),
      })),
    } : current);
  }

  // Named love: only available to signed-in viewers (FR-018). Attributed to the account.
  async function toggleNamedLove(response: SharedResponse) {
    const authToken = getToken();
    if (!authToken) return;
    setBusyId(response.responseId);
    try {
      const state = await sharedNamedLike(shareToken, response.responseId, authToken, !response.likedByMe);
      patchResponse(response.responseId, { namedLikeCount: state.likeCount, likedByMe: state.likedByMe });
    } finally {
      setBusyId(null);
    }
  }

  // Anonymous thumb: available to anyone (signed-in too). Best-effort deduplicated per browser cookie.
  async function toggleAnonymousThumb(response: SharedResponse) {
    setBusyId(response.responseId);
    try {
      const state = await anonymousLike(shareToken, response.responseId);
      patchResponse(response.responseId, {
        anonymousLikeCount: state.anonymousLikeCount,
        likedByThisVisitor: state.likedByThisVisitor,
      });
    } finally {
      setBusyId(null);
    }
  }

  if (error) return <main className="flex min-h-screen items-center justify-center bg-[#faf9f5] p-6 text-sm text-red-700">{error}</main>;
  if (!session) return <main className="min-h-screen bg-[#faf9f5] p-8"><div className="mx-auto h-64 max-w-4xl animate-pulse rounded-2xl bg-white" /></main>;

  const signedIn = Boolean(getToken());

  return (
    <main className="min-h-screen bg-[#faf9f5] px-4 py-8 sm:px-6">
      <div className="mx-auto max-w-5xl">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#b75536]">Shared conversation</p>
        <h1 className="mt-1 text-2xl font-semibold text-stone-900">{session.title || "Untitled conversation"}</h1>
        <p className="mt-2 text-sm text-stone-500">
          Read-only view. <span className="text-rose-600">♥ loves</span> come from signed-in users;{" "}
          <span className="text-[#c96442]">👍 thumbs</span> are anonymous and deduplicated in this browser.
        </p>
        <div className="mt-7 space-y-6">
          {session.turns.map((turn) => (
            <section key={turn.sequenceNum} className="space-y-3">
              <div className="ml-auto w-fit max-w-3xl rounded-2xl bg-[#30302e] px-4 py-3 text-white"><MarkdownRenderer content={turn.promptText} className="text-sm [&_*]:text-white" /></div>
              <div className="grid gap-3 md:grid-cols-2">
                {turn.responses.map((response) => (
                  <article key={response.responseId} className="overflow-hidden rounded-xl border border-stone-200 bg-white">
                    <header className="flex items-center justify-between gap-2 border-b border-stone-100 bg-stone-50 px-4 py-2">
                      <span className="truncate text-sm font-semibold text-stone-800">{response.modelDisplayName}</span>
                      <div className="flex items-center gap-1.5">
                        <button
                          type="button"
                          title={signedIn ? "Love this answer" : "Sign in to love this answer"}
                          aria-label="Loves"
                          disabled={!signedIn || busyId === response.responseId}
                          onClick={() => void toggleNamedLove(response)}
                          className={`flex items-center gap-1 rounded px-2 py-1 text-xs transition-colors disabled:cursor-default disabled:opacity-100 ${response.likedByMe ? "text-rose-600" : "text-stone-500"} ${signedIn ? "hover:bg-stone-100" : ""}`}
                        >
                          <Heart className={`h-4 w-4 ${response.likedByMe ? "fill-current" : ""}`} />
                          {response.namedLikeCount}
                        </button>
                        <button
                          type="button"
                          title={response.likedByThisVisitor ? "You already thumbed this" : "Give an anonymous thumbs up"}
                          aria-label="Anonymous thumbs up"
                          disabled={busyId === response.responseId || response.likedByThisVisitor}
                          onClick={() => void toggleAnonymousThumb(response)}
                          className={`flex items-center gap-1 rounded px-2 py-1 text-xs transition-colors disabled:cursor-default disabled:opacity-100 ${response.likedByThisVisitor ? "text-[#c96442]" : "text-stone-500"} ${!response.likedByThisVisitor ? "hover:bg-stone-100" : ""}`}
                        >
                          <ThumbsUp className={`h-4 w-4 ${response.likedByThisVisitor ? "fill-current" : ""}`} />
                          {response.anonymousLikeCount}
                        </button>
                      </div>
                    </header>
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
