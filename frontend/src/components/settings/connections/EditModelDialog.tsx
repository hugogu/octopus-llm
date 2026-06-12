"use client";

import { useState } from "react";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import { getToken } from "@/lib/api/auth";
import { patchConfiguredModel } from "@/lib/api/connections";
import type { ConfiguredModelV2 } from "@/lib/types/api";
import { JsonField } from "./AddModelDialog";
import { parseJsonObject, prettyJson } from "./formUtils";

interface Props {
  model: ConfiguredModelV2;
  onClose: () => void;
  onSaved: (model: ConfiguredModelV2) => void;
}

export default function EditModelDialog({ model, onClose, onSaved }: Props) {
  const [displayName, setDisplayName] = useState(model.displayName);
  const [capabilities, setCapabilities] = useState(prettyJson(model.capabilityOverrides));
  const [customParams, setCustomParams] = useState(prettyJson(model.customParams));
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const token = getToken();
    if (!token) return setError("Not authenticated");
    setSaving(true);
    try {
      const saved = await patchConfiguredModel(token, model.id, {
        displayName,
        capabilityOverrides: parseJsonObject(capabilities, "Capability overrides"),
        customParams: parseJsonObject(customParams, "Custom parameters"),
      });
      onSaved(saved);
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to update model");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen onClose={onClose} title="Edit configured model" size="lg">
      <form className="max-h-[70vh] space-y-4 overflow-y-auto pr-1" onSubmit={submit}>
        <Input label="Model ID" value={model.modelId} disabled helperText="Create a new configured model to change the provider model ID." />
        <Input label="Display name" required value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
        <JsonField label="Capability overrides" value={capabilities} onChange={setCapabilities} />
        <JsonField label="Custom request parameters" value={customParams} onChange={setCustomParams} />
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" isLoading={saving}>Save model</Button>
        </div>
      </form>
    </Modal>
  );
}
