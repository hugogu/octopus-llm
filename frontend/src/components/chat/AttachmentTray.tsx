"use client";

import { useRef } from "react";
import { X } from "lucide-react";
import MediaItem from "./MediaItem";
import { mediaKindOf } from "@/lib/media/limits";

export interface PendingAttachment {
  id: string;
  file: File;
  url: string;
}

interface AttachmentTrayProps {
  items: PendingAttachment[];
  onRemove: (id: string) => void;
  onReorder: (items: PendingAttachment[]) => void;
}

/**
 * Pre-send preview tray (feature 007, US2): per-file thumbnail/preview, remove control, and
 * drag-to-reorder. The order shown is the order submitted.
 */
export default function AttachmentTray({ items, onRemove, onReorder }: AttachmentTrayProps) {
  const dragIndex = useRef<number | null>(null);
  if (items.length === 0) return null;

  function handleDrop(target: number) {
    const from = dragIndex.current;
    dragIndex.current = null;
    if (from === null || from === target) return;
    const next = [...items];
    const moved = next.splice(from, 1)[0];
    if (!moved) return;
    next.splice(target, 0, moved);
    onReorder(next);
  }

  return (
    <div className="flex flex-wrap gap-2 px-1 pt-1">
      {items.map((item, i) => {
        const kind = mediaKindOf(item.file);
        return (
          <div
            key={item.id}
            draggable
            onDragStart={() => {
              dragIndex.current = i;
            }}
            onDragOver={(e) => e.preventDefault()}
            onDrop={() => handleDrop(i)}
            className="group relative h-20 w-20 cursor-grab overflow-hidden rounded-xl border border-stone-200 bg-stone-50 active:cursor-grabbing"
            title={item.file.name}
          >
            {kind === "audio" || kind === "file" ? (
              <div className="flex h-full w-full flex-col items-center justify-center gap-1 px-1 text-center text-[10px] capitalize text-stone-500">
                <span className="font-medium">{kind === "audio" ? "Audio" : "File"}</span>
                <span className="w-full truncate text-stone-400">{item.file.name}</span>
              </div>
            ) : (
              <MediaItem src={item.url} type={kind} alt={item.file.name} className="h-full w-full object-cover" />
            )}
            <button
              type="button"
              onClick={() => onRemove(item.id)}
              className="absolute right-0.5 top-0.5 inline-flex h-5 w-5 items-center justify-center rounded-full bg-white/90 text-stone-500 shadow-sm transition hover:text-stone-800"
              aria-label="Remove attachment"
            >
              <X className="h-3 w-3" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
