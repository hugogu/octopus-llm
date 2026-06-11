"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { ModelDefinition } from "@/lib/types/api";
import { listModels } from "@/lib/api/models";
import { addApiKey, addModelConfig, syncProviderModels } from "@/lib/api/userConfig";
import { getToken } from "@/lib/api/auth";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";

interface ApiKeyFormProps {
  models: ModelDefinition[];
  onClose?: () => void;
}

export default function ApiKeyForm({ models, onClose }: ApiKeyFormProps) {
  const router = useRouter();
  const providers = [...new Set(models.map((m) => m.providerId))].sort();

  const [providerId, setProviderId] = useState(providers[0] ?? "");
  const [apiKey, setApiKey] = useState("");
  const [label, setLabel] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const token = getToken();
      if (!token) throw new Error("Not authenticated");
      const createdKey = await addApiKey(token, {
        providerId,
        apiKey,
        label: label || undefined,
        baseUrl: baseUrl.trim() || undefined,
      });
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
      onClose?.();
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save key");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {error && (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-4 py-3">
          <p className="text-red-600 dark:text-red-400 text-sm">{error}</p>
        </div>
      )}
      
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
          Provider
        </label>
        <select
          value={providerId}
          onChange={(e) => setProviderId(e.target.value)}
          className="w-full px-3.5 py-2.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          required
        >
          {providers.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </div>

      <Input
        type="password"
        label="API Key"
        placeholder="Enter your API key"
        value={apiKey}
        onChange={(e) => setApiKey(e.target.value)}
        required
      />

      <Input
        type="text"
        label="Label"
        placeholder="Optional label (e.g., 'Production Key')"
        value={label}
        onChange={(e) => setLabel(e.target.value)}
        helperText="Helps you identify this key"
      />

      <Input
        type="url"
        label="Base URL"
        placeholder="https://api.kimi.com/coding/v1 (optional)"
        value={baseUrl}
        onChange={(e) => setBaseUrl(e.target.value)}
        helperText="Override the provider's default API endpoint — needed when your key belongs to a different service address (e.g., a Kimi key instead of a Moonshot key). Leave empty to use the default."
      />

      <div className="flex gap-3 pt-2">
        <Button type="submit" isLoading={loading} fullWidth>
          Save Key & Load Models
        </Button>
        {onClose && (
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
}
