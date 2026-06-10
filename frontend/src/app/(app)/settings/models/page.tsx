import { cookies } from "next/headers";
import { listModels } from "@/lib/api/models";
import { listApiKeys, listModelConfigs } from "@/lib/api/userConfig";
import ModelsSettingsPage from "./ModelsSettingsPage";

export default async function ModelsSettingsServerPage() {
  const cookieStore = await cookies();
  const token = cookieStore.get("auth_token")?.value ?? "";

  const [{ models }, { apiKeys }, { modelConfigs }] = await Promise.all([
    listModels(),
    listApiKeys(token),
    listModelConfigs(token),
  ]);

  return (
    <ModelsSettingsPage
      models={models}
      apiKeys={apiKeys}
      modelConfigs={modelConfigs}
    />
  );
}
