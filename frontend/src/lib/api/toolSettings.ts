import { apiUrl } from "@/lib/api/base";

export interface ToolInfo {
  name: string;
  label: string;
  description: string;
  configurable: boolean;
  available: boolean;
}

export interface WebSearchProvider {
  id: string;
  label: string;
  defaultBaseUrl: string;
  defaultModel: string;
}

export interface WebSearchConfig {
  enabled: boolean;
  provider: string;
  baseUrl: string | null;
  model: string | null;
  apiKeySet: boolean;
}

export interface ToolSettingsAdmin {
  tools: ToolInfo[];
  webSearch: WebSearchConfig;
  webSearchProviders: WebSearchProvider[];
  updatedAt: string;
  updatedBy: string | null;
}

export interface ToolSettingsUpdate {
  webSearchEnabled?: boolean;
  webSearchProvider?: string;
  webSearchBaseUrl?: string;
  webSearchModel?: string;
  webSearchApiKey?: string;
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

export function updateToolSettings(token: string, body: ToolSettingsUpdate): Promise<ToolSettingsAdmin> {
  return authed<ToolSettingsAdmin>("/api/v2/admin/tool-settings", token, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
