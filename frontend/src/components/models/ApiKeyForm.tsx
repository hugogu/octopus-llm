"use client";

import { useState } from "react";
import type { ModelDefinition, ApiKeyMeta } from "@/lib/types/api";
import { addApiKey } from "@/lib/api/userConfig";
import { getToken } from "@/lib/api/auth";

interface ApiKeyFormProps {
  models: ModelDefinition[];
  onAdded: (key: ApiKeyMeta) => void;
}

export default function ApiKeyForm({ models, onAdded }: ApiKeyFormProps) {
  const providers = [...new Set(models.map((m) => m.providerId))].sort();

  const [providerId, setProviderId] = useState(providers[0] ?? "");
  const [apiKey, setApiKey] = useState("");
  const [label, setLabel] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const token = getToken();
      if (!token) throw new Error("Not authenticated");
      const created = await addApiKey(token, { providerId, apiKey, label: label || undefined });
      onAdded(created);
      setApiKey("");
      setLabel("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save key");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="border rounded-lg p-4 flex flex-col gap-3 bg-gray-50">
      <h3 className="font-semibold text-sm">Add API Key</h3>
      {error && <p className="text-red-600 text-xs">{error}</p>}
      <select
        value={providerId}
        onChange={(e) => setProviderId(e.target.value)}
        className="border rounded px-2 py-1 text-sm"
        required
      >
        {providers.map((p) => (
          <option key={p} value={p}>
            {p}
          </option>
        ))}
      </select>
      <input
        type="password"
        placeholder="API Key"
        value={apiKey}
        onChange={(e) => setApiKey(e.target.value)}
        required
        className="border rounded px-2 py-1 text-sm"
      />
      <input
        type="text"
        placeholder="Label (optional)"
        value={label}
        onChange={(e) => setLabel(e.target.value)}
        className="border rounded px-2 py-1 text-sm"
        maxLength={255}
      />
      <button
        type="submit"
        disabled={loading}
        className="bg-blue-600 text-white rounded px-3 py-1.5 text-sm disabled:opacity-50"
      >
        {loading ? "Saving…" : "Save Key"}
      </button>
    </form>
  );
}
