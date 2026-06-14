import { apiUrl } from "@/lib/api/base";

export interface StorageSettingsView {
  backend: "local" | "s3";
  localPublicBaseUrl: string | null;
  s3Endpoint: string | null;
  s3Region: string | null;
  s3Bucket: string | null;
  s3AccessKey: string | null;
  s3SecretKeySet: boolean;
  s3PublicBaseUrl: string | null;
  maxImageBytes: number;
  maxVideoBytes: number;
  maxAudioBytes: number;
  maxFilesPerPrompt: number;
  maxTotalBytesPerPrompt: number;
  updatedAt: string;
  updatedBy: string | null;
}

export type StorageSettingsUpdate = Partial<{
  backend: "local" | "s3";
  localPublicBaseUrl: string;
  s3Endpoint: string;
  s3Region: string;
  s3Bucket: string;
  s3AccessKey: string;
  s3SecretKey: string;
  s3PublicBaseUrl: string;
  maxImageBytes: number;
  maxVideoBytes: number;
  maxAudioBytes: number;
  maxFilesPerPrompt: number;
  maxTotalBytesPerPrompt: number;
}>;

async function request<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
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

export function getStorageSettings(token: string): Promise<StorageSettingsView> {
  return request("/api/v2/admin/storage-settings", token);
}

export function updateStorageSettings(token: string, body: StorageSettingsUpdate): Promise<StorageSettingsView> {
  return request("/api/v2/admin/storage-settings", token, { method: "PUT", body: JSON.stringify(body) });
}
