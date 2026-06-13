import { apiUrl } from "@/lib/api/base";
import { replaceAuthToken } from "@/lib/api/auth";
import type { MeResponse, PasswordChangeResponse } from "@/lib/types/api";

async function request<T>(path: string, token: string, options: RequestInit = {}): Promise<T> {
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
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw Object.assign(new Error(error.message ?? "Request failed"), {
      status: response.status,
      details: error.details,
    });
  }
  return response.json() as Promise<T>;
}

export function getAccount(token: string): Promise<MeResponse> {
  return request("/api/v2/me", token);
}

export function updateProfile(token: string, displayName: string | null): Promise<MeResponse> {
  return request("/api/v2/me", token, {
    method: "PATCH",
    body: JSON.stringify({ displayName }),
  });
}

export async function changePassword(
  token: string,
  currentPassword: string,
  newPassword: string,
): Promise<PasswordChangeResponse> {
  const response = await request<PasswordChangeResponse>("/api/v2/me/password", token, {
    method: "POST",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
  replaceAuthToken(response.token, response.expiresAt);
  return response;
}

export function resendVerification(token: string): Promise<{ status: string }> {
  return request("/api/v2/me/email-verification/resend", token, { method: "POST" });
}
