import { apiUrl } from "@/lib/api/base";
import type {
  AddConfiguredModelRequestV2,
  AddConnectionRequestV2,
  CatalogueEntryV2,
  ConfiguredModelV2,
  ConnectionV2,
  PageResponse,
  PatchConfiguredModelRequestV2,
  PatchConnectionRequestV2,
  ProtocolDefinitionV2,
} from "@/lib/types/api";

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  const response = await fetch(apiUrl(path), {
    ...options,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      "Content-Type": "application/json",
      ...(options.headers ?? {}),
    },
    cache: "no-store",
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText })) as {
      message?: string;
    };
    throw Object.assign(new Error(error.message ?? "Request failed"), { status: response.status });
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function listProtocols(page = 0, size = 100): Promise<PageResponse<ProtocolDefinitionV2>> {
  return request(`/api/v2/protocols?page=${page}&size=${size}`);
}

export function listCatalogue(
  protocol: string,
  page = 0,
  size = 100,
): Promise<PageResponse<CatalogueEntryV2>> {
  return request(
    `/api/v2/catalogue?protocol=${encodeURIComponent(protocol)}&page=${page}&size=${size}`,
  );
}

export function listConnections(
  token: string,
  page = 0,
  size = 100,
): Promise<PageResponse<ConnectionV2>> {
  return request(`/api/v2/connections?page=${page}&size=${size}`, {}, token);
}

export function addConnection(
  token: string,
  body: AddConnectionRequestV2,
): Promise<ConnectionV2> {
  return request("/api/v2/connections", { method: "POST", body: JSON.stringify(body) }, token);
}

export function patchConnection(
  token: string,
  id: string,
  body: PatchConnectionRequestV2,
): Promise<ConnectionV2> {
  return request(
    `/api/v2/connections/${encodeURIComponent(id)}`,
    { method: "PATCH", body: JSON.stringify(body) },
    token,
  );
}

export function rotateConnectionKey(token: string, id: string, apiKey: string): Promise<void> {
  return request(
    `/api/v2/connections/${encodeURIComponent(id)}/key`,
    { method: "PUT", body: JSON.stringify({ apiKey }) },
    token,
  );
}

export function listConnectionEndpointModels(
  token: string,
  id: string,
): Promise<{ items: string[] }> {
  return request(
    `/api/v2/connections/${encodeURIComponent(id)}/models`,
    {},
    token,
  );
}

export function deleteConnection(token: string, id: string): Promise<void> {
  return request(
    `/api/v2/connections/${encodeURIComponent(id)}`,
    { method: "DELETE" },
    token,
  );
}

export function listConfiguredModels(
  token: string,
  enabled?: boolean,
  page = 0,
  size = 100,
): Promise<PageResponse<ConfiguredModelV2>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (enabled !== undefined) params.set("enabled", String(enabled));
  return request(`/api/v2/configured-models?${params.toString()}`, {}, token);
}

export function addConfiguredModel(
  token: string,
  body: AddConfiguredModelRequestV2,
): Promise<ConfiguredModelV2> {
  return request(
    "/api/v2/configured-models",
    { method: "POST", body: JSON.stringify(body) },
    token,
  );
}

export function patchConfiguredModel(
  token: string,
  id: string,
  body: PatchConfiguredModelRequestV2,
): Promise<ConfiguredModelV2> {
  return request(
    `/api/v2/configured-models/${encodeURIComponent(id)}`,
    { method: "PATCH", body: JSON.stringify(body) },
    token,
  );
}

export function deleteConfiguredModel(token: string, id: string): Promise<void> {
  return request(
    `/api/v2/configured-models/${encodeURIComponent(id)}`,
    { method: "DELETE" },
    token,
  );
}
