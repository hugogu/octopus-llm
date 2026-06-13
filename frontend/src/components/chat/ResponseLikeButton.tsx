"use client";

import { useState } from "react";
import { Heart } from "lucide-react";
import { getToken } from "@/lib/api/auth";
import { likeResponse, unlikeResponse } from "@/lib/api/reactions";

interface Props {
  responseId?: string;
  initialCount?: number;
  initialLiked?: boolean;
}

export default function ResponseLikeButton({ responseId, initialCount = 0, initialLiked = false }: Props) {
  const [count, setCount] = useState(initialCount);
  const [liked, setLiked] = useState(initialLiked);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);

  async function toggle() {
    if (!responseId || busy) return;
    const token = getToken();
    if (!token) return;
    setBusy(true);
    setFailed(false);
    try {
      const next = liked
        ? await unlikeResponse(responseId, token)
        : await likeResponse(responseId, token);
      setCount(next.likeCount);
      setLiked(next.likedByMe);
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      type="button"
      disabled={!responseId || busy}
      onClick={() => void toggle()}
      aria-pressed={liked}
      aria-label={liked ? "Unlike response" : "Like response"}
      title={failed ? "Could not update like" : responseId ? "Like response" : "Available after response is saved"}
      className={`inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-xs transition disabled:cursor-not-allowed disabled:opacity-40 ${liked ? "bg-rose-50 text-rose-600" : "text-stone-400 hover:bg-stone-100 hover:text-rose-600"}`}
    >
      <Heart className={`h-3.5 w-3.5 ${liked ? "fill-current" : ""}`} />
      <span>{count}</span>
    </button>
  );
}
