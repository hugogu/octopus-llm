import type { ModelDefinition, ListModelsResponse } from "@/lib/types/api";
import { apiUrl } from "@/lib/api/base";

export async function listModels(params?: {
  providerId?: string;
  inputModality?: string;
}): Promise<ListModelsResponse> {
  const url = new URL(apiUrl("/api/v1/models"));
  if (params?.providerId) url.searchParams.set("provider_id", params.providerId);
  if (params?.inputModality) url.searchParams.set("input_modality", params.inputModality);
  const res = await fetch(url.toString(), { cache: "no-store" });
  if (!res.ok) throw new Error(`Failed to list models: ${res.status}`);
  return res.json() as Promise<ListModelsResponse>;
}

export async function getModel(id: string): Promise<ModelDefinition> {
  const res = await fetch(apiUrl(`/api/v1/models/${encodeURIComponent(id)}`), { cache: "no-store" });
  if (!res.ok) throw new Error(`Model not found: ${id}`);
  return res.json() as Promise<ModelDefinition>;
}
