import { apiUrl } from "@/lib/api/base";
import type {
  AnalyticsSummary,
  ModelAnalytics,
  PageResponse,
  PublicModelAnalytics,
  ResponseAnalytics,
  SessionAnalytics,
} from "@/lib/types/api";

export interface AnalyticsQuery {
  from?: string;
  to?: string;
  configuredModelId?: string;
  protocol?: string;
  modelId?: string;
  page?: number;
  size?: number;
}

function queryString(query: AnalyticsQuery): string {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  const text = params.toString();
  return text ? `?${text}` : "";
}

async function request<T>(path: string, token?: string): Promise<T> {
  const response = await fetch(apiUrl(path), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    cache: "no-store",
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(error.message ?? "Analytics request failed");
  }
  return response.json() as Promise<T>;
}

export function getAnalyticsSummary(token: string, query: AnalyticsQuery): Promise<AnalyticsSummary> {
  return request(`/api/v2/analytics/summary${queryString(query)}`, token);
}

export function getModelAnalytics(token: string, query: AnalyticsQuery): Promise<PageResponse<ModelAnalytics>> {
  return request(`/api/v2/analytics/by-model${queryString(query)}`, token);
}

export function getSessionAnalytics(token: string, query: AnalyticsQuery): Promise<PageResponse<SessionAnalytics>> {
  return request(`/api/v2/analytics/by-session${queryString(query)}`, token);
}

export function getResponseAnalytics(token: string, query: AnalyticsQuery): Promise<PageResponse<ResponseAnalytics>> {
  return request(`/api/v2/analytics/responses${queryString(query)}`, token);
}

export function getPublicModelAnalytics(query: AnalyticsQuery): Promise<PageResponse<PublicModelAnalytics>> {
  return request(`/api/v2/analytics/public/by-model${queryString(query)}`);
}
