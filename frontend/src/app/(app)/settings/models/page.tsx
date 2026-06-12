import { cookies } from "next/headers";
import { listModels, listProviders } from "@/lib/api/models";
import { listApiKeys, listModelConfigs } from "@/lib/api/userConfig";
import ModelsSettingsPage from "./ModelsSettingsPage";

export default async function ModelsSettingsServerPage() {
  const cookieStore = await cookies();
  const token = cookieStore.get("auth_token")?.value ?? "";

  const [{ models }, { apiKeys }, { modelConfigs }, { providers }] = await Promise.all([
    listModels(),
    listApiKeys(token),
    listModelConfigs(token),
    listProviders(),
  ]);

  const defaultBaseUrls = Object.fromEntries(providers.map((p) => [p.id, p.defaultBaseUrl]));

  return (
    <ModelsSettingsPage
      models={models}
      apiKeys={apiKeys}
      modelConfigs={modelConfigs}
      defaultBaseUrls={defaultBaseUrls}
    />
  );
}
