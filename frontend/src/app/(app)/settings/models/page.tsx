import { cookies } from "next/headers";
import { listModels } from "@/lib/api/models";
import { listApiKeys, listModelConfigs } from "@/lib/api/userConfig";
import ModelCard from "@/components/models/ModelCard";
import ApiKeyForm from "@/components/models/ApiKeyForm";
import type { ModelDefinition } from "@/lib/types/api";

export default async function ModelsSettingsPage() {
  const cookieStore = await cookies();
  const token = cookieStore.get("auth_token")?.value ?? "";

  const [{ models }, { apiKeys }, { modelConfigs }] = await Promise.all([
    listModels(),
    listApiKeys(token),
    listModelConfigs(token),
  ]);

  const configMap = new Map(modelConfigs.map((c) => [c.modelId, c]));
  const keyMap = new Map(apiKeys.map((k) => [k.id, k]));

  return (
    <div className="max-w-3xl mx-auto px-4 py-8 flex flex-col gap-8">
      <h1 className="text-2xl font-bold">Model Settings</h1>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">Add API Key</h2>
        <ApiKeyForm models={models} onAdded={() => {}} />
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">Stored Keys</h2>
        {apiKeys.length === 0 ? (
          <p className="text-sm text-gray-500">No API keys stored yet.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {apiKeys.map((key) => (
              <li key={key.id} className="border rounded px-3 py-2 text-sm flex justify-between">
                <span>
                  <strong>{key.providerId}</strong>
                  {key.label ? ` — ${key.label}` : ""}
                </span>
                <span className="text-gray-400 text-xs">{new Date(key.createdAt).toLocaleDateString()}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">Available Models</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {models.map((model: ModelDefinition) => {
            const config = configMap.get(model.id);
            const keyMeta = config?.providerApiKeyId ? keyMap.get(config.providerApiKeyId) : undefined;
            return (
              <ModelCard key={model.id} model={model}>
                <div className="flex flex-col items-end gap-1 text-xs">
                  {config ? (
                    <>
                      <span className={`px-1.5 py-0.5 rounded ${config.isEnabled ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                        {config.isEnabled ? "Enabled" : "Disabled"}
                      </span>
                      {keyMeta && <span className="text-gray-400">{keyMeta.providerId}</span>}
                    </>
                  ) : (
                    <span className="text-gray-400">Not configured</span>
                  )}
                </div>
              </ModelCard>
            );
          })}
        </div>
      </section>
    </div>
  );
}
