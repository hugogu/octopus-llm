import type {
  ApiKeyMeta,
  AddApiKeyRequest,
  UpsertModelConfigRequest,
  PatchModelConfigRequest,
  UserModelConfig,
} from "@/lib/types/api";

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "";

async function authFetch<T>(
  path: string,
  options: RequestInit,
  token: string,
): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...(options.headers ?? {}),
    },
    cache: "no-store",
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText })) as { message?: string };
    throw Object.assign(new Error(err.message ?? "Request failed"), { status: res.status });
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export async function listApiKeys(token: string): Promise<{ apiKeys: ApiKeyMeta[] }> {
  return authFetch("/api/v1/user/api-keys", { method: "GET" }, token);
}

export async function addApiKey(token: string, req: AddApiKeyRequest): Promise<ApiKeyMeta> {
  return authFetch("/api/v1/user/api-keys", {
    method: "POST",
    body: JSON.stringify(req),
  }, token);
}

export async function deleteApiKey(token: string, keyId: string): Promise<void> {
  return authFetch(`/api/v1/user/api-keys/${encodeURIComponent(keyId)}`, { method: "DELETE" }, token);
}

export async function listModelConfigs(token: string): Promise<{ modelConfigs: UserModelConfig[] }> {
  return authFetch("/api/v1/user/model-configs", { method: "GET" }, token);
}

export async function addModelConfig(
  token: string,
  req: UpsertModelConfigRequest,
): Promise<UserModelConfig> {
  return authFetch("/api/v1/user/model-configs", {
    method: "POST",
    body: JSON.stringify(req),
  }, token);
}

export async function patchModelConfig(
  token: string,
  configId: string,
  req: PatchModelConfigRequest,
): Promise<UserModelConfig> {
  return authFetch(`/api/v1/user/model-configs/${encodeURIComponent(configId)}`, {
    method: "PATCH",
    body: JSON.stringify(req),
  }, token);
}

export async function deleteModelConfig(token: string, configId: string): Promise<void> {
  return authFetch(`/api/v1/user/model-configs/${encodeURIComponent(configId)}`, { method: "DELETE" }, token);
}
