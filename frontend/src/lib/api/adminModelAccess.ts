import { apiUrl } from "@/lib/api/base";
import type { AdminModelAccess, PageResponse } from "@/lib/types/api";

async function request<T>(path: string, options: RequestInit, token: string): Promise<T> {
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
    const error = await response.json().catch(() => ({ message: response.statusText })) as { message?: string };
    throw Object.assign(new Error(error.message ?? "Request failed"), { status: response.status });
  }
  return response.json() as Promise<T>;
}

export interface AdminModelAccessFilter {
  q?: string;
  connectionId?: string;
  protocol?: string;
  enabled?: boolean;
  anonymousAllowed?: boolean;
}

export function listAdminModels(
  token: string,
  filter: AdminModelAccessFilter = {},
  page = 0,
  size = 50,
): Promise<PageResponse<AdminModelAccess>> {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort: "displayName", direction: "asc" });
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return request(`/api/v2/admin/models?${params.toString()}`, {}, token);
}

export type AdminModelBulkAction = "ALLOW_ANONYMOUS" | "REVOKE_ANONYMOUS" | "SHOW" | "HIDE" | "DELETE";

export interface AdminModelSelection {
  mode: "IDS" | "FILTER";
  ids?: string[];
  filter?: AdminModelAccessFilter;
  excludeIds?: string[];
}

export async function previewAdminModelBulk(
  token: string,
  action: AdminModelBulkAction,
  selection: AdminModelSelection,
) {
  return request<{
    operationId: string;
    action: AdminModelBulkAction;
    targetCount: number;
    expiresAt: string;
    summary: Record<string, number>;
  }>("/api/v2/admin/model-bulk-operations/preview", {
    method: "POST",
    body: JSON.stringify({ action, selection }),
  }, token);
}

export function executeAdminModelBulk(token: string, operationId: string, idempotencyKey: string) {
  return request<AdminModelBulkResult>(
    `/api/v2/admin/model-bulk-operations/${encodeURIComponent(operationId)}/execute`,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey } },
    token,
  );
}

export function getAdminModelBulk(token: string, operationId: string) {
  return request<AdminModelBulkResult>(
    `/api/v2/admin/model-bulk-operations/${encodeURIComponent(operationId)}`,
    {},
    token,
  );
}

export interface AdminModelBulkResult {
  operationId: string;
  status: string;
  action: AdminModelBulkAction;
  targetCount: number;
  changedCount: number;
  alreadySatisfiedCount: number;
  failedCount: number;
  items: Array<{
    configuredModelId: string;
    displayName: string;
    outcome: string;
    errorCode: string | null;
    errorMessage: string | null;
  }>;
}
