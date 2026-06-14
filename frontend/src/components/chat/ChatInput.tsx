"use client";

import { useState, useRef } from "react";
import { Paperclip, ArrowUp } from "lucide-react";
import AttachmentTray, { type PendingAttachment } from "@/components/chat/AttachmentTray";
import { validateCandidate } from "@/lib/media/limits";

interface ChatInputProps {
  onSubmit: (promptText: string, files: File[]) => void;
  disabled?: boolean;
  supportsAttachments?: boolean;
}

export default function ChatInput({ onSubmit, disabled = false, supportsAttachments = false }: ChatInputProps) {
  const [text, setText] = useState("");
  const [items, setItems] = useState<PendingAttachment[]>([]);
  const [limitError, setLimitError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(e.target.files ?? []);
    if (fileRef.current) fileRef.current.value = "";
    setLimitError(null);
    setItems((prev) => {
      const next = [...prev];
      for (const file of selected) {
        const error = validateCandidate(file, next.map((p) => p.file));
        if (error) {
          setLimitError(error);
          continue;
        }
        next.push({ id: crypto.randomUUID(), file, url: URL.createObjectURL(file) });
      }
      return next;
    });
  }

  function removeItem(id: string) {
    setItems((prev) => {
      const target = prev.find((p) => p.id === id);
      if (target) URL.revokeObjectURL(target.url);
      return prev.filter((p) => p.id !== id);
    });
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = text.trim();
    if (!trimmed && items.length === 0) return;
    onSubmit(trimmed, items.map((p) => p.file));
    items.forEach((p) => URL.revokeObjectURL(p.url));
    setText("");
    setItems([]);
    setLimitError(null);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e as unknown as React.FormEvent);
    }
  }

  const canSend = !disabled && (text.trim().length > 0 || items.length > 0);

  return (
    <form onSubmit={handleSubmit} className="mx-auto w-full max-w-3xl">
      <div className="flex flex-col gap-2 rounded-2xl border border-stone-200 bg-white p-2.5 shadow-sm transition focus-within:border-[#c96442] focus-within:ring-1 focus-within:ring-[#c96442]">
        <AttachmentTray items={items} onRemove={removeItem} onReorder={setItems} />

        {limitError && (
          <p className="px-2 text-xs text-red-600">{limitError}</p>
        )}

        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          placeholder="Ask all selected models…  (Enter to send, Shift+Enter for newline)"
          rows={3}
          className="w-full resize-none bg-transparent px-2 py-1 text-sm text-stone-800 placeholder:text-stone-400 focus:outline-none disabled:text-stone-400"
        />

        <div className="flex items-center justify-between gap-2 px-1">
          <div className="flex items-center gap-1">
            {supportsAttachments && (
              <>
                <input
                  ref={fileRef}
                  type="file"
                  accept="image/*,video/*"
                  multiple
                  className="sr-only"
                  onChange={handleFile}
                  disabled={disabled}
                />
                <button
                  type="button"
                  onClick={() => fileRef.current?.click()}
                  disabled={disabled}
                  className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-stone-500 transition hover:bg-stone-100 hover:text-stone-700 disabled:opacity-50"
                  title="Attach image or video"
                  aria-label="Attach image or video"
                >
                  <Paperclip className="h-4 w-4" />
                </button>
              </>
            )}
          </div>

          <button
            type="submit"
            disabled={!canSend}
            className="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-[#c96442] text-white shadow-sm transition hover:bg-[#b55538] disabled:cursor-not-allowed disabled:opacity-40"
            title="Send message"
            aria-label="Send message"
          >
            <ArrowUp className="h-4 w-4" />
          </button>
        </div>
      </div>
    </form>
  );
}
