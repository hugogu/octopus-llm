"use client";

import { useState } from "react";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import { getToken } from "@/lib/api/auth";
import { patchConnection, rotateConnectionKey } from "@/lib/api/connections";
import type { ConnectionV2 } from "@/lib/types/api";

interface Props {
  connection: ConnectionV2;
  onClose: () => void;
  onSaved: (connection: ConnectionV2) => void;
}

export default function EditConnectionDialog({ connection, onClose, onSaved }: Props) {
  const [label, setLabel] = useState(connection.label ?? "");
  const [baseUrl, setBaseUrl] = useState(connection.baseUrl);
  const [apiKey, setApiKey] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const token = getToken();
    if (!token) return setError("Not authenticated");
    setSaving(true);
    try {
      const saved = await patchConnection(token, connection.id, { label, baseUrl });
      if (apiKey.trim()) await rotateConnectionKey(token, connection.id, apiKey);
      onSaved(saved);
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to update connection");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen onClose={onClose} title="Edit connection">
      <form className="space-y-4" onSubmit={submit}>
        <Input label="Label" value={label} onChange={(event) => setLabel(event.target.value)} />
        <Input label="Base URL" type="url" required value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} />
        <Input
          label="Rotate API key"
          type="password"
          value={apiKey}
          onChange={(event) => setApiKey(event.target.value)}
          helperText="Leave blank to keep the current encrypted key."
          autoComplete="new-password"
        />
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" isLoading={saving}>Save changes</Button>
        </div>
      </form>
    </Modal>
  );
}
