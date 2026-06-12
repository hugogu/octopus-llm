import { apiUrl } from "@/lib/api/base";
import type { UpdatePreferencesRequestV2, UserPreferencesV2 } from "@/lib/types/api";

async function authFetch<T>(path: string, options: RequestInit, token: string): Promise<T> {
  const response = await fetch(apiUrl(path), {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
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
  return response.json() as Promise<T>;
}

export function getPreferences(token: string): Promise<UserPreferencesV2> {
  return authFetch("/api/v2/user/preferences", { method: "GET" }, token);
}

export function updatePreferences(
  token: string,
  request: UpdatePreferencesRequestV2,
): Promise<UserPreferencesV2> {
  return authFetch(
    "/api/v2/user/preferences",
    { method: "PUT", body: JSON.stringify(request) },
    token,
  );
}

export function patchPreferences(
  token: string,
  request: UpdatePreferencesRequestV2,
): Promise<UserPreferencesV2> {
  return authFetch(
    "/api/v2/user/preferences",
    { method: "PATCH", body: JSON.stringify(request) },
    token,
  );
}
