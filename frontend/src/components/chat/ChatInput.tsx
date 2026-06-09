"use client";

import { useState, useRef } from "react";
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

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      {attachments.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {attachments.map((att, i) => (
            <div key={i} className="flex items-center gap-1 bg-gray-100 rounded px-2 py-1 text-xs">
              <span>{att.type}: {att.mimeType}</span>
              <button type="button" onClick={() => removeAttachment(i)} className="text-gray-400 hover:text-gray-700">×</button>
            </div>
          ))}
        </div>
      )}
      <div className="flex gap-2 items-end">
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          placeholder="Ask all selected models… (Enter to send, Shift+Enter for newline)"
          rows={3}
          className="flex-1 border rounded-lg px-3 py-2 text-sm resize-none disabled:bg-gray-50 disabled:text-gray-400"
        />
        <div className="flex flex-col gap-1 self-end">
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
                className="border rounded-lg px-3 py-2 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50 h-[42px]"
                title="Attach image or video"
              >
                📎
              </button>
            </>
          )}
          <button
            type="submit"
            disabled={disabled || (!text.trim() && attachments.length === 0)}
            className="bg-blue-600 text-white rounded-lg px-4 py-2 text-sm disabled:opacity-50 h-[42px]"
          >
            Send
          </button>
        </div>
      </div>
    </form>
  );
}
