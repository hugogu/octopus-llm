"use client";

import { useState } from "react";
import { Heart } from "lucide-react";
import { getToken } from "@/lib/api/auth";
import { likeResponse, unlikeResponse } from "@/lib/api/reactions";

interface Props {
  responseId?: string;
  initialCount?: number;
  initialLiked?: boolean;
  /**
   * Controlled mode used by {@link ResponseGroup} to make likes mutually exclusive within one
   * response group. When provided, the button does not call the API itself — it reflects the given
   * state and delegates toggling to the parent.
   */
  controlled?: {
    count: number;
    liked: boolean;
    busy?: boolean;
    onToggle: () => void;
  };
}

export default function ResponseLikeButton({
  responseId,
  initialCount = 0,
  initialLiked = false,
  controlled,
}: Props) {
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

  const isControlled = controlled !== undefined;
  const shownCount = isControlled ? controlled!.count : count;
  const shownLiked = isControlled ? controlled!.liked : liked;
  const isBusy = isControlled ? controlled!.busy ?? false : busy;

  return (
    <button
      type="button"
      disabled={!responseId || isBusy}
      onClick={() => (isControlled ? controlled!.onToggle() : void toggle())}
      aria-pressed={shownLiked}
      aria-label={shownLiked ? "Unlike response" : "Like response"}
      title={failed ? "Could not update like" : responseId ? "Like response" : "Available after response is saved"}
      className={`inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-xs transition disabled:cursor-not-allowed disabled:opacity-40 ${shownLiked ? "bg-rose-50 text-rose-600" : "text-stone-400 hover:bg-stone-100 hover:text-rose-600"}`}
    >
      <Heart className={`h-3.5 w-3.5 ${shownLiked ? "fill-current" : ""}`} />
      <span>{shownCount}</span>
    </button>
  );
}
