"use client";

import type { LoginResponse, RegisterRequest, VerifyEmailRequest } from "@/lib/types/api";

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "";

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ code: "UNKNOWN", message: res.statusText }));
    throw Object.assign(new Error(err.message ?? "Request failed"), { code: err.code, status: res.status });
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export async function register(req: RegisterRequest): Promise<{ message: string }> {
  return post("/api/v1/auth/register", req);
}

export async function verifyEmail(req: VerifyEmailRequest): Promise<{ message: string }> {
  return post("/api/v1/auth/verify-email", req);
}

export async function login(req: { email: string; password: string }): Promise<LoginResponse> {
  const data = await post<LoginResponse>("/api/v1/auth/login", req);
  if (typeof document !== "undefined") {
    // Non-HttpOnly cookie so server components can read it via cookies()
    document.cookie = `auth_token=${data.token}; path=/; max-age=${60 * 60}; SameSite=Lax`;
  }
  return data;
}

export async function logout(): Promise<void> {
  const token = getToken();
  if (!token) return;
  await fetch(`${BASE}/api/v1/auth/logout`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => {});
  if (typeof document !== "undefined") {
    document.cookie = "auth_token=; path=/; max-age=0; SameSite=Lax";
  }
}

export function getToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)auth_token=([^;]+)/);
  return match?.[1] ?? null;
}
