"use client";

import { useEffect, useState } from "react";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import { getToken } from "@/lib/api/auth";
import {
  addConfiguredModel,
  listCatalogue,
  listConnectionEndpointModels,
} from "@/lib/api/connections";
import type { CatalogueEntryV2, ConfiguredModelV2, ConnectionV2 } from "@/lib/types/api";
import { parseJsonObject, prettyJson } from "./formUtils";

interface Props {
  connection: ConnectionV2 | null;
  onClose: () => void;
  onSaved: (model: ConfiguredModelV2) => void;
}

export default function AddModelDialog({ connection, onClose, onSaved }: Props) {
  const [suggestions, setSuggestions] = useState<CatalogueEntryV2[]>([]);
  const [endpointModels, setEndpointModels] = useState<string[] | null>(null);
  const [loadingEndpointModels, setLoadingEndpointModels] = useState(false);
  const [endpointModelsError, setEndpointModelsError] = useState<string | null>(null);
  const [modelId, setModelId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [capabilities, setCapabilities] = useState("");
  const [customParams, setCustomParams] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!connection) return;
    void listCatalogue(connection.protocol)
      .then((response) => setSuggestions(response.items))
      .catch(() => setSuggestions([]));
  }, [connection]);

  const loadEndpointModels = async () => {
    if (!connection) return;
    const token = getToken();
    if (!token) return setEndpointModelsError("Not authenticated");
    setLoadingEndpointModels(true);
    setEndpointModelsError(null);
    try {
      const response = await listConnectionEndpointModels(token, connection.id);
      setEndpointModels(response.items);
    } catch (cause) {
      setEndpointModelsError(
        cause instanceof Error ? cause.message : "Failed to load models from the endpoint",
      );
    } finally {
      setLoadingEndpointModels(false);
    }
  };

  const selectEndpointModel = (id: string) => {
    if (!id) return;
    setModelId(id);
    if (!displayName.trim()) setDisplayName(id);
  };

  const selectSuggestion = (id: string) => {
    const suggestion = suggestions.find((item) => item.modelId === id);
    if (!suggestion) return;
    setModelId(suggestion.modelId);
    setDisplayName(suggestion.displayName);
    setCapabilities(prettyJson(suggestion.capabilityOverrides));
    setCustomParams(prettyJson(suggestion.customParams));
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!connection) return;
    const token = getToken();
    if (!token) return setError("Not authenticated");
    setSaving(true);
    try {
      const saved = await addConfiguredModel(token, {
        connectionId: connection.id,
        modelId,
        displayName,
        capabilityOverrides: parseJsonObject(capabilities, "Capability overrides"),
        customParams: parseJsonObject(customParams, "Custom parameters"),
      });
      onSaved(saved);
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to add model");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen={connection !== null} onClose={onClose} title="Add configured model" size="lg">
      <form className="max-h-[70vh] space-y-4 overflow-y-auto pr-1" onSubmit={submit}>
        {suggestions.length > 0 ? (
          <label className="block text-sm font-medium text-gray-700">
            Catalogue suggestion
            <select onChange={(event) => selectSuggestion(event.target.value)} defaultValue="" className="mt-1.5 w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5">
              <option value="">Manual entry</option>
              {suggestions.map((item) => <option key={`${item.providerLabel}:${item.modelId}`} value={item.modelId}>{item.providerLabel} · {item.displayName}</option>)}
            </select>
          </label>
        ) : (
          <p className="rounded-lg bg-stone-50 px-3 py-2 text-xs text-stone-500">
            Catalogue is unavailable or has no matches. Manual model entry remains available.
          </p>
        )}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-gray-700">Models on this endpoint</span>
            <Button type="button" size="sm" variant="secondary" isLoading={loadingEndpointModels} onClick={() => void loadEndpointModels()}>
              Load models
            </Button>
          </div>
          {endpointModels !== null ? (
            endpointModels.length > 0 ? (
              <select
                onChange={(event) => selectEndpointModel(event.target.value)}
                defaultValue=""
                className="w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5"
              >
                <option value="">Select a model from the endpoint…</option>
                {endpointModels.map((id) => <option key={id} value={id}>{id}</option>)}
              </select>
            ) : (
              <p className="rounded-lg bg-stone-50 px-3 py-2 text-xs text-stone-500">The endpoint returned no models. Manual entry remains available.</p>
            )
          ) : null}
          {endpointModelsError ? (
            <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">{endpointModelsError} — manual entry remains available.</p>
          ) : null}
        </div>
        <Input label="Model ID" required value={modelId} onChange={(event) => setModelId(event.target.value)} placeholder="provider-model-id" />
        <Input label="Display name" required value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
        <JsonField label="Capability overrides" value={capabilities} onChange={setCapabilities} placeholder={'{\n  "context_length_tokens": 128000\n}'} />
        <JsonField label="Custom request parameters" value={customParams} onChange={setCustomParams} placeholder={'{\n  "temperature": 0.2\n}'} />
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" isLoading={saving}>Add model</Button>
        </div>
      </form>
    </Modal>
  );
}

export function JsonField({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (value: string) => void; placeholder?: string }) {
  return (
    <label className="block text-sm font-medium text-gray-700">
      {label}
      <textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        rows={5}
        spellCheck={false}
        className="mt-1.5 w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5 font-mono text-sm"
      />
    </label>
  );
}
