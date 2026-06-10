"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { createCustomModel } from "@/lib/api/userConfig";
import { getToken } from "@/lib/api/auth";
import type { ApiKeyMeta, CapabilityMatrix } from "@/lib/types/api";

interface CustomModelFormProps {
  apiKeys: ApiKeyMeta[];
  onClose?: () => void;
}

const DEFAULT_PARAMS = "{}";

export default function CustomModelForm({ apiKeys, onClose }: CustomModelFormProps) {
  const router = useRouter();
  const providers = useMemo(
    () => [...new Set(apiKeys.map((key) => key.providerId))].sort(),
    [apiKeys],
  );
  const [providerId, setProviderId] = useState(providers[0] ?? "");
  const [modelId, setModelId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [contextLength, setContextLength] = useState("");
  const [supportsImage, setSupportsImage] = useState(false);
  const [supportsVideo, setSupportsVideo] = useState(false);
  const [supportsFunctionCalling, setSupportsFunctionCalling] = useState(true);
  const [customParamsText, setCustomParamsText] = useState(DEFAULT_PARAMS);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const providerKeys = useMemo(
    () => apiKeys.filter((key) => key.providerId === providerId),
    [apiKeys, providerId],
  );
  const [providerApiKeyId, setProviderApiKeyId] = useState(providerKeys[0]?.id ?? "");

  useEffect(() => {
    if (!providerKeys.some((key) => key.id === providerApiKeyId)) {
      setProviderApiKeyId(providerKeys[0]?.id ?? "");
    }
  }, [providerApiKeyId, providerKeys]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const token = getToken();
      if (!token) throw new Error("Not authenticated");
      if (!providerApiKeyId) throw new Error("Select an API key first");

      const customParams = JSON.parse(customParamsText || "{}") as Record<string, unknown>;
      const inputModalities = ["text"];
      if (supportsImage) inputModalities.push("image");
      if (supportsVideo) inputModalities.push("video");

      const capabilityMatrix: CapabilityMatrix = {
        input_modalities: inputModalities,
        output_modalities: ["text"],
        context_length_tokens: contextLength ? Number(contextLength) : null,
        supports_streaming: true,
        supports_function_calling: supportsFunctionCalling,
        supports_system_prompt: true,
        supports_video_input: supportsVideo,
      };

      await createCustomModel(token, {
        providerId,
        modelId,
        displayName: displayName || undefined,
        providerApiKeyId,
        isEnabled: true,
        customParams,
        capabilityMatrix,
      });

      setModelId("");
      setDisplayName("");
      setContextLength("");
      setSupportsImage(false);
      setSupportsVideo(false);
      setSupportsFunctionCalling(true);
      setCustomParamsText(DEFAULT_PARAMS);
      onClose?.();
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create custom model");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="border rounded-lg p-4 flex flex-col gap-3 bg-white">
      <div>
        <h3 className="font-semibold text-sm">Add Custom Model</h3>
        <p className="text-xs text-gray-500">Use this when the provider supports a model ID that is not in the discovered list yet.</p>
      </div>

      {providers.length === 0 ? (
        <p className="text-sm text-gray-500">Add at least one API key before creating a custom model.</p>
      ) : (
        <>
          {error && <p className="text-red-600 text-xs">{error}</p>}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <select
              value={providerId}
              onChange={(e) => {
                const nextProvider = e.target.value;
                setProviderId(nextProvider);
                const nextKey = apiKeys.find((key) => key.providerId === nextProvider)?.id ?? "";
                setProviderApiKeyId(nextKey);
              }}
              className="border rounded px-2 py-1.5 text-sm"
              required
            >
              {providers.map((provider) => (
                <option key={provider} value={provider}>
                  {provider}
                </option>
              ))}
            </select>

            <select
              value={providerApiKeyId}
              onChange={(e) => setProviderApiKeyId(e.target.value)}
              className="border rounded px-2 py-1.5 text-sm"
              required
            >
              {providerKeys.map((key) => (
                <option key={key.id} value={key.id}>
                  {key.label || `${key.providerId} key`}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <input
              type="text"
              value={modelId}
              onChange={(e) => setModelId(e.target.value)}
              placeholder="Provider model ID, e.g. kimi-k2.6"
              className="border rounded px-2 py-1.5 text-sm"
              required
            />
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="Display name (optional)"
              className="border rounded px-2 py-1.5 text-sm"
            />
          </div>

          <input
            type="number"
            min="0"
            value={contextLength}
            onChange={(e) => setContextLength(e.target.value)}
            placeholder="Context length tokens (optional)"
            className="border rounded px-2 py-1.5 text-sm"
          />

          <div className="flex flex-wrap gap-4 text-sm">
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={supportsImage} onChange={(e) => setSupportsImage(e.target.checked)} />
              Image input
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={supportsVideo} onChange={(e) => setSupportsVideo(e.target.checked)} />
              Video input
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={supportsFunctionCalling} onChange={(e) => setSupportsFunctionCalling(e.target.checked)} />
              Function calling
            </label>
          </div>

          <textarea
            value={customParamsText}
            onChange={(e) => setCustomParamsText(e.target.value)}
            rows={5}
            className="border rounded px-2 py-1.5 text-xs font-mono"
            placeholder='{"temperature":0.2,"thinking":{"type":"enabled"}}'
          />

          <button
            type="submit"
            disabled={loading}
            className="self-start rounded bg-gray-900 text-white px-3 py-1.5 text-sm disabled:opacity-50"
          >
            {loading ? "Creating…" : "Create Custom Model"}
          </button>
        </>
      )}
    </form>
  );
}
