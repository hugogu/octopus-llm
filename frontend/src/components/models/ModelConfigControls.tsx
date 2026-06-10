"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import type { ApiKeyMeta, ModelDefinition, UserModelConfig } from "@/lib/types/api";
import { addModelConfig, deleteModelConfig, patchModelConfig } from "@/lib/api/userConfig";
import { getToken } from "@/lib/api/auth";

interface ModelConfigControlsProps {
  model: ModelDefinition;
  apiKeys: ApiKeyMeta[];
  config?: UserModelConfig;
}

export default function ModelConfigControls({
  model,
  apiKeys,
  config,
}: ModelConfigControlsProps) {
  const router = useRouter();
  const providerKeys = useMemo(
    () => apiKeys.filter((key) => key.providerId === model.providerId),
    [apiKeys, model.providerId],
  );
  const [selectedKeyId, setSelectedKeyId] = useState(
    config?.providerApiKeyId ?? providerKeys[0]?.id ?? "",
  );
  const [customParamsText, setCustomParamsText] = useState(
    JSON.stringify(config?.customParams ?? {}, null, 2),
  );
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setSelectedKeyId(config?.providerApiKeyId ?? providerKeys[0]?.id ?? "");
    setCustomParamsText(JSON.stringify(config?.customParams ?? {}, null, 2));
  }, [config, providerKeys]);

  async function withAuth<T>(fn: (token: string) => Promise<T>) {
    const token = getToken();
    if (!token) throw new Error("Not authenticated");
    return fn(token);
  }

  async function saveConfig() {
    if (!selectedKeyId) {
      setError("Select an API key first.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const customParams = JSON.parse(customParamsText || "{}") as Record<string, unknown>;
      await withAuth((token) =>
        config
          ? patchModelConfig(token, config.id, {
              providerApiKeyId: selectedKeyId,
              isEnabled: config.isEnabled,
              customParams,
            })
          : addModelConfig(token, {
              modelId: model.id,
              providerApiKeyId: selectedKeyId,
              isEnabled: true,
              customParams,
            }),
      );
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save model configuration.");
    } finally {
      setLoading(false);
    }
  }

  async function toggleEnabled(nextEnabled: boolean) {
    if (!config) return;
    setLoading(true);
    setError(null);
    try {
      await withAuth((token) =>
        patchModelConfig(token, config.id, { isEnabled: nextEnabled }),
      );
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update model state.");
    } finally {
      setLoading(false);
    }
  }

  async function removeConfig() {
    if (!config) return;
    setLoading(true);
    setError(null);
    try {
      await withAuth((token) => deleteModelConfig(token, config.id));
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to remove model configuration.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col gap-2 min-w-40">
      {providerKeys.length === 0 ? (
        <p className="text-xs text-amber-700">Add a {model.providerId} key above to enable this model.</p>
      ) : (
        <>
          <select
            value={selectedKeyId}
            onChange={(e) => setSelectedKeyId(e.target.value)}
            disabled={loading}
            className="border rounded px-2 py-1 text-xs bg-white"
          >
            {providerKeys.map((key) => (
              <option key={key.id} value={key.id}>
                {key.label || `${key.providerId} key`}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={saveConfig}
            disabled={loading || !selectedKeyId}
            className="rounded bg-blue-600 px-2 py-1 text-xs text-white disabled:opacity-50"
          >
            {config ? "Save key" : "Enable model"}
          </button>
        </>
      )}

      <textarea
        value={customParamsText}
        onChange={(e) => setCustomParamsText(e.target.value)}
        disabled={loading}
        rows={4}
        className="border rounded px-2 py-1 text-[11px] font-mono bg-white"
        placeholder='{"temperature":0.2}'
      />

      {config && (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => toggleEnabled(!config.isEnabled)}
            disabled={loading}
            className="rounded border px-2 py-1 text-xs text-gray-700 disabled:opacity-50"
          >
            {config.isEnabled ? "Disable" : "Enable"}
          </button>
          <button
            type="button"
            onClick={removeConfig}
            disabled={loading}
            className="rounded border border-red-200 px-2 py-1 text-xs text-red-700 disabled:opacity-50"
          >
            Remove
          </button>
        </div>
      )}

      {error && <p className="text-xs text-red-600">{error}</p>}
      <p className="text-[11px] text-gray-400">Custom params are sent as provider request body fields when supported.</p>
    </div>
  );
}
