"use client";

import { useState, useRef } from "react";
import { Paperclip, ArrowUp, X } from "lucide-react";
import type { Attachment } from "@/lib/types/api";

interface ChatInputProps {
  onSubmit: (promptText: string, attachments: Attachment[]) => void;
  disabled?: boolean;
  supportsAttachments?: boolean;
}

export default function ChatInput({ onSubmit, disabled = false, supportsAttachments = false }: ChatInputProps) {
  const [text, setText] = useState("");
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const fileRef = useRef<HTMLInputElement>(null);

  async function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    const encoded = await Promise.all(
      files.map((file) =>
        new Promise<Attachment>((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => {
            const dataUrl = reader.result as string;
            const base64 = dataUrl.split(",")[1] ?? "";
            const type = file.type.startsWith("video/") ? "video" : "image";
            resolve({ type, data: base64, mimeType: file.type });
          };
          reader.onerror = reject;
          reader.readAsDataURL(file);
        }),
      ),
    );
    setAttachments((prev) => [...prev, ...encoded]);
    if (fileRef.current) fileRef.current.value = "";
  }

  function removeAttachment(index: number) {
    setAttachments((prev) => prev.filter((_, i) => i !== index));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = text.trim();
    if (!trimmed && attachments.length === 0) return;
    onSubmit(trimmed, attachments);
    setText("");
    setAttachments([]);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e as unknown as React.FormEvent);
    }
  }

  const canSend = !disabled && (text.trim().length > 0 || attachments.length > 0);

  return (
    <form onSubmit={handleSubmit} className="mx-auto w-full max-w-3xl">
      <div className="flex flex-col gap-2 rounded-2xl border border-stone-200 bg-white p-2.5 shadow-sm transition focus-within:border-[#c96442] focus-within:ring-1 focus-within:ring-[#c96442]">
        {attachments.length > 0 && (
          <div className="flex flex-wrap gap-2 px-1 pt-1">
            {attachments.map((att, i) => (
              <span
                key={i}
                className="inline-flex items-center gap-1.5 rounded-full border border-stone-200 bg-stone-50 px-2.5 py-1 text-xs text-stone-600"
              >
                <span className="font-medium capitalize">{att.type}</span>
                <span className="text-stone-400">{att.mimeType}</span>
                <button
                  type="button"
                  onClick={() => removeAttachment(i)}
                  className="text-stone-400 transition hover:text-stone-700"
                  aria-label="Remove attachment"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
          </div>
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
