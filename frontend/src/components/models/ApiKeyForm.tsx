"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { ModelDefinition } from "@/lib/types/api";
import { listModels } from "@/lib/api/models";
import { addApiKey, addModelConfig, syncProviderModels } from "@/lib/api/userConfig";
import { getToken } from "@/lib/api/auth";

interface ApiKeyFormProps {
  models: ModelDefinition[];
}

export default function ApiKeyForm({ models }: ApiKeyFormProps) {
  const router = useRouter();
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
      const createdKey = await addApiKey(token, { providerId, apiKey, label: label || undefined });
      const { models: syncedModels } = await syncProviderModels(token, {
        providerId,
        providerApiKeyId: createdKey.id,
      }).catch(async () => listModels({ providerId }));
      const providerModels = syncedModels.filter((model) => model.providerId === providerId);
      await Promise.all(
        providerModels.map((model) =>
          addModelConfig(token, {
            modelId: model.id,
            providerApiKeyId: createdKey.id,
            isEnabled: true,
            customParams: {},
          }),
        ),
      );
      setApiKey("");
      setLabel("");
      router.refresh();
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
        {loading ? "Saving…" : "Save Key And Load Models"}
      </button>
    </form>
  );
}
