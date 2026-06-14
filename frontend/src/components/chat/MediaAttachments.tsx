"use client";

import { useState } from "react";
import MediaItem from "./MediaItem";
import type { MediaReference } from "@/lib/types/api";

/**
 * Renders a turn's media attachments in history / share (feature 007): images as thumbnails that
 * click to enlarge in a lightbox; video and audio with inline playback.
 */
export default function MediaAttachments({
  items,
  dark = false,
}: {
  items: MediaReference[] | undefined;
  dark?: boolean;
}) {
  const [zoom, setZoom] = useState<string | null>(null);
  if (!items || items.length === 0) return null;
  const ordered = [...items].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  const border = dark ? "border-white/25" : "border-stone-200";

  return (
    <div className="mb-2 flex flex-wrap gap-2">
      {ordered.map((m) => {
        if (m.media_type === "image") {
          return (
            <button
              key={m.media_id}
              type="button"
              onClick={() => setZoom(m.url)}
              className="block"
              aria-label="Enlarge image"
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={m.url}
                alt={m.original_filename ?? "attachment"}
                className={`h-24 w-24 cursor-zoom-in rounded-lg border ${border} object-cover`}
              />
            </button>
          );
        }
        return (
          <div key={m.media_id} className="w-72 max-w-full">
            <MediaItem src={m.url} type={m.media_type} />
          </div>
        );
      })}

      {zoom && (
        <div
          onClick={() => setZoom(null)}
          className="fixed inset-0 z-50 flex cursor-zoom-out items-center justify-center bg-black/80 p-4"
          role="dialog"
          aria-modal="true"
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={zoom} alt="attachment enlarged" className="max-h-full max-w-full rounded-lg object-contain" />
        </div>
      )}
    </div>
  );
}
