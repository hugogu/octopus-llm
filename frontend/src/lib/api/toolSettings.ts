import { apiUrl } from "@/lib/api/base";

export interface ToolInfo {
  name: string;
  label: string;
  description: string;
  configurable: boolean;
  available: boolean;
}

export interface WebSearchProviderView {
  id: string;
  label: string;
  needsModel: boolean;
  baseUrl: string;
  model: string;
  apiKeySet: boolean;
}

export interface ToolSettingsAdmin {
  tools: ToolInfo[];
  webSearchEnabled: boolean;
  webSearchActiveProvider: string;
  webSearchProviders: WebSearchProviderView[];
  updatedAt: string;
  updatedBy: string | null;
}

export interface WebSearchProviderUpdate {
  baseUrl?: string;
  model?: string;
  apiKey?: string;
}

export interface ToolActivationUpdate {
  webSearchEnabled?: boolean;
  webSearchActiveProvider?: string;
}

async function authed<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(apiUrl(path), {
    ...init,
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", ...(init.headers ?? {}) },
    cache: "no-store",
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw Object.assign(new Error(error.message ?? "Request failed"), { status: response.status });
  }
  return response.json() as Promise<T>;
}

export function getToolSettings(token: string): Promise<ToolSettingsAdmin> {
  return authed<ToolSettingsAdmin>("/api/v2/admin/tool-settings", token);
}

export function updateWebSearchProvider(
  token: string,
  provider: string,
  body: WebSearchProviderUpdate,
): Promise<ToolSettingsAdmin> {
  return authed<ToolSettingsAdmin>(`/api/v2/admin/tool-settings/providers/${encodeURIComponent(provider)}`, token, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function updateToolActivation(token: string, body: ToolActivationUpdate): Promise<ToolSettingsAdmin> {
  return authed<ToolSettingsAdmin>("/api/v2/admin/tool-settings", token, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
