"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import { getToken } from "@/lib/api/auth";
import { importSharedSession, newShareImportKey } from "@/lib/api/shares";

export function parseShareToken(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  if (/^[A-Za-z0-9_-]+$/.test(trimmed)) return trimmed;
  try {
    const url = new URL(trimmed, window.location.origin);
    if (url.origin !== window.location.origin) return null;
    const match = url.pathname.match(/^\/share\/([^/]+)\/?$/);
    return match?.[1] ? decodeURIComponent(match[1]) : null;
  } catch { return null; }
}

export default function QuestImportDialog({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const router = useRouter();
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const attempt = useRef<{ token: string; key: string } | null>(null);

  function close() {
    setValue("");
    setError(null);
    attempt.current = null;
    onClose();
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const token = parseShareToken(value);
    if (!token) {
      setError("Enter a share link from this deployment or its opaque token.");
      return;
    }
    const authToken = getToken();
    if (!authToken) {
      setError("Sign in before importing a Quest.");
      return;
    }
    if (attempt.current?.token !== token) attempt.current = { token, key: newShareImportKey() };
    setBusy(true);
    setError(null);
    try {
      const result = await importSharedSession(token, attempt.current.key, authToken);
      close();
      router.push(`/chat?session=${encodeURIComponent(result.sessionId)}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Import failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal isOpen={isOpen} onClose={close} title="Import a Quest">
      <form onSubmit={submit} className="space-y-4">
        <p className="text-sm text-stone-600">
          Paste a share link from this deployment. The imported Quest is an independent copy you own.
        </p>
        <label className="block text-sm font-medium text-stone-800">
          Share link or token
          <input
            value={value}
            onChange={(event) => {
              setValue(event.target.value);
              setError(null);
              attempt.current = null;
            }}
            autoFocus
            placeholder="https://example.test/share/opaque-token"
            className="mt-2 w-full rounded-lg border border-stone-300 px-3 py-2 font-mono text-sm outline-none focus:border-[#c96442] focus:ring-2 focus:ring-[#c96442]/20"
          />
        </label>
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={close} disabled={busy}>Cancel</Button>
          <Button type="submit" isLoading={busy} className="!bg-[#c96442] hover:!bg-[#b55538]">
            Import Quest
          </Button>
        </div>
      </form>
    </Modal>
  );
}
