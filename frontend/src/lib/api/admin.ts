import { apiUrl } from "@/lib/api/base";
import type {
  AdminUser,
  BuiltinConnection,
  BuiltinModel,
  ConnectionAllocationView,
  MeResponse,
  PageResponse,
} from "@/lib/types/api";

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
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
    const error = (await response.json().catch(() => ({ message: response.statusText }))) as {
      message?: string;
    };
    throw Object.assign(new Error(error.message ?? "Request failed"), { status: response.status });
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

// --- Current user ----------------------------------------------------------

export function getMe(token: string): Promise<MeResponse> {
  return request("/api/v2/me", {}, token);
}

// --- Public password reset completion --------------------------------------

export function confirmPasswordReset(token: string, password: string): Promise<{ status: string }> {
  return request("/api/v1/auth/password-reset/confirm", {
    method: "POST",
    body: JSON.stringify({ token, password }),
  });
}

// --- User management -------------------------------------------------------

export function listUsers(
  token: string,
  q = "",
  page = 0,
  size = 25,
  testOnly = false,
): Promise<PageResponse<AdminUser>> {
  const query = q ? `&q=${encodeURIComponent(q)}` : "";
  const testParam = testOnly ? "&testOnly=true" : "";
  return request(`/api/v2/admin/users?page=${page}&size=${size}${query}${testParam}`, {}, token);
}

export function activateUser(token: string, id: string): Promise<AdminUser> {
  return request(`/api/v2/admin/users/${id}/activate`, { method: "POST" }, token);
}

export function disableUser(token: string, id: string): Promise<AdminUser> {
  return request(`/api/v2/admin/users/${id}/disable`, { method: "POST" }, token);
}

export function enableUser(token: string, id: string): Promise<AdminUser> {
  return request(`/api/v2/admin/users/${id}/enable`, { method: "POST" }, token);
}

export function resetUserPassword(token: string, id: string): Promise<{ status: string }> {
  return request(`/api/v2/admin/users/${id}/reset-password`, { method: "POST" }, token);
}

export function deleteUser(token: string, id: string): Promise<void> {
  return request(`/api/v2/admin/users/${id}`, { method: "DELETE" }, token);
}

export function purgeTestAccounts(token: string): Promise<{ deleted: number }> {
  return request(`/api/v2/admin/users/purge-test`, { method: "POST" }, token);
}

// --- Built-in connections --------------------------------------------------

export function listBuiltinConnections(
  token: string,
  page = 0,
  size = 25,
): Promise<PageResponse<BuiltinConnection>> {
  return request(`/api/v2/admin/connections?page=${page}&size=${size}`, {}, token);
}

export function createBuiltinConnection(
  token: string,
  body: { protocol: string; baseUrl: string; apiKey: string; label?: string },
): Promise<BuiltinConnection> {
  return request(`/api/v2/admin/connections`, { method: "POST", body: JSON.stringify(body) }, token);
}

export function patchBuiltinConnection(
  token: string,
  id: string,
  body: { label?: string; baseUrl?: string },
): Promise<BuiltinConnection> {
  return request(`/api/v2/admin/connections/${id}`, { method: "PATCH", body: JSON.stringify(body) }, token);
}

export function rotateBuiltinKey(token: string, id: string, apiKey: string): Promise<void> {
  return request(`/api/v2/admin/connections/${id}/key`, { method: "PUT", body: JSON.stringify({ apiKey }) }, token);
}

export function deleteBuiltinConnection(token: string, id: string): Promise<void> {
  return request(`/api/v2/admin/connections/${id}`, { method: "DELETE" }, token);
}

// --- Built-in models -------------------------------------------------------

export function listBuiltinModels(
  token: string,
  connectionId: string,
  page = 0,
  size = 100,
): Promise<PageResponse<BuiltinModel>> {
  return request(`/api/v2/admin/connections/${connectionId}/models?page=${page}&size=${size}`, {}, token);
}

export function addBuiltinModel(
  token: string,
  connectionId: string,
  body: { modelId: string; displayName: string; isEnabled?: boolean },
): Promise<BuiltinModel> {
  return request(
    `/api/v2/admin/connections/${connectionId}/models`,
    { method: "POST", body: JSON.stringify(body) },
    token,
  );
}

export function deleteBuiltinModel(
  token: string,
  connectionId: string,
  configuredModelId: string,
): Promise<void> {
  return request(
    `/api/v2/admin/connections/${connectionId}/models/${configuredModelId}`,
    { method: "DELETE" },
    token,
  );
}

// --- Allocations -----------------------------------------------------------

export function listAllocations(
  token: string,
  connectionId: string,
  page = 0,
  size = 100,
): Promise<PageResponse<ConnectionAllocationView>> {
  return request(`/api/v2/admin/connections/${connectionId}/allocations?page=${page}&size=${size}`, {}, token);
}

export function allocateConnection(token: string, connectionId: string, userId: string): Promise<void> {
  return request(`/api/v2/admin/connections/${connectionId}/allocations/${userId}`, { method: "PUT" }, token);
}

export function revokeConnection(token: string, connectionId: string, userId: string): Promise<void> {
  return request(`/api/v2/admin/connections/${connectionId}/allocations/${userId}`, { method: "DELETE" }, token);
}
