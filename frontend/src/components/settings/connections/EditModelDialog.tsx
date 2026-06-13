"use client";

import { useState } from "react";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import { getToken } from "@/lib/api/auth";
import { patchConfiguredModel } from "@/lib/api/connections";
import type { ConfiguredModelV2 } from "@/lib/types/api";
import { JsonField } from "./AddModelDialog";
import { normalizeCurrency, parseJsonObject, parseOptionalPrice, prettyJson } from "./formUtils";

interface Props {
  model: ConfiguredModelV2;
  onClose: () => void;
  onSaved: (model: ConfiguredModelV2) => void;
}

export default function EditModelDialog({ model, onClose, onSaved }: Props) {
  const [displayName, setDisplayName] = useState(model.displayName);
  const [capabilities, setCapabilities] = useState(prettyJson(model.capabilityOverrides));
  const [customParams, setCustomParams] = useState(prettyJson(model.customParams));
  const [inputPrice, setInputPrice] = useState(model.inputPricePerMtok?.toString() ?? "");
  const [outputPrice, setOutputPrice] = useState(model.outputPricePerMtok?.toString() ?? "");
  const [currency, setCurrency] = useState(model.priceCurrency ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const token = getToken();
    if (!token) return setError("Not authenticated");
    setSaving(true);
    try {
      const parsedInputPrice = parseOptionalPrice(inputPrice, "Input price");
      const parsedOutputPrice = parseOptionalPrice(outputPrice, "Output price");
      const saved = await patchConfiguredModel(token, model.id, {
        displayName,
        capabilityOverrides: parseJsonObject(capabilities, "Capability overrides"),
        customParams: parseJsonObject(customParams, "Custom parameters"),
        inputPricePerMtok: parsedInputPrice,
        outputPricePerMtok: parsedOutputPrice,
        priceCurrency: parsedInputPrice === null && parsedOutputPrice === null
          ? ""
          : normalizeCurrency(currency, true),
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
        <div className="grid gap-3 sm:grid-cols-3">
          <Input label="Input price / 1M tokens" type="number" min="0" step="0.0001" value={inputPrice} onChange={(event) => setInputPrice(event.target.value)} />
          <Input label="Output price / 1M tokens" type="number" min="0" step="0.0001" value={outputPrice} onChange={(event) => setOutputPrice(event.target.value)} />
          <Input label="Currency" maxLength={3} value={currency} onChange={(event) => setCurrency(event.target.value.toUpperCase())} placeholder="USD" />
        </div>
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
