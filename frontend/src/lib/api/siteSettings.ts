import { apiUrl } from "@/lib/api/base";

/** Public shape returned by `/api/v2/site-settings` — used by the frontend footer. */
export interface SiteSettingsPublic {
  siteName: string | null;
  footerText: string | null;
  chinaFilingEnabled: boolean;
  icpRecordNo: string | null;
  policeRecordNo: string | null;
}

/** Admin shape returned by `/api/v2/admin/site-settings` — adds audit metadata. */
export interface SiteSettingsAdmin extends SiteSettingsPublic {
  updatedAt: string;
  updatedBy: string | null;
}

export type SiteSettingsUpdate = Partial<SiteSettingsPublic>;

function parseNulls<T extends Record<string, unknown>>(body: T): T {
  return Object.fromEntries(
    Object.entries(body).map(([k, v]) => [k, v === undefined ? null : v]),
  ) as T;
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

/** Public, anonymous — used by the layout footer. */
export function getPublicSiteSettings(): Promise<SiteSettingsPublic> {
  return fetch(apiUrl("/api/v2/site-settings"), { cache: "no-store" }).then(async (response) => {
    if (!response.ok) {
      throw Object.assign(new Error(`Site settings fetch failed: ${response.status}`), {
        status: response.status,
      });
    }
    return (await response.json()) as SiteSettingsPublic;
  });
}

export function getSiteSettings(token: string): Promise<SiteSettingsAdmin> {
  return authed<SiteSettingsAdmin>("/api/v2/admin/site-settings", token);
}

export function updateSiteSettings(
  token: string,
  body: SiteSettingsUpdate,
): Promise<SiteSettingsAdmin> {
  return authed<SiteSettingsAdmin>("/api/v2/admin/site-settings", token, {
    method: "PUT",
    body: JSON.stringify(parseNulls(body as Record<string, unknown>)),
  });
}
