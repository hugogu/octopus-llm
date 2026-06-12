"use client";

import { useState } from "react";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import { getToken } from "@/lib/api/auth";
import { addConnection } from "@/lib/api/connections";
import type { ConnectionV2, ProtocolDefinitionV2 } from "@/lib/types/api";

interface Props {
  open: boolean;
  protocols: ProtocolDefinitionV2[];
  onClose: () => void;
  onSaved: (connection: ConnectionV2) => void;
}

export default function AddConnectionDialog({ open, protocols, onClose, onSaved }: Props) {
  const [protocol, setProtocol] = useState(protocols[0]?.id ?? "");
  const [label, setLabel] = useState("");
  const [baseUrl, setBaseUrl] = useState(protocols[0]?.defaultBaseUrl ?? "");
  const [apiKey, setApiKey] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const effectiveProtocol = protocol || protocols[0]?.id || "";
  const effectiveBaseUrl = baseUrl || protocols.find((item) => item.id === effectiveProtocol)?.defaultBaseUrl || "";

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const token = getToken();
    if (!token) return setError("Not authenticated");
    setSaving(true);
    try {
      const saved = await addConnection(token, {
        protocol: effectiveProtocol,
        label,
        baseUrl: effectiveBaseUrl,
        apiKey,
      });
      onSaved(saved);
      setLabel("");
      setApiKey("");
      setError(null);
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to add connection");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen={open} onClose={onClose} title="Add connection">
      <form className="space-y-4" onSubmit={submit}>
        <label className="block text-sm font-medium text-gray-700">
          Protocol
          <select
            value={effectiveProtocol}
            onChange={(event) => {
              const next = event.target.value;
              setProtocol(next);
              setBaseUrl(protocols.find((item) => item.id === next)?.defaultBaseUrl ?? "");
            }}
            className="mt-1.5 w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5"
          >
            {protocols.map((item) => <option key={item.id} value={item.id}>{item.displayName}</option>)}
          </select>
        </label>
        <Input label="Label" value={label} onChange={(event) => setLabel(event.target.value)} placeholder="Work, Personal, Proxy..." />
        <Input label="Base URL" type="url" required value={effectiveBaseUrl} onChange={(event) => setBaseUrl(event.target.value)} />
        <Input label="API key" type="password" required value={apiKey} onChange={(event) => setApiKey(event.target.value)} autoComplete="new-password" />
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" isLoading={saving}>Add connection</Button>
        </div>
      </form>
    </Modal>
  );
}
