"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { SlidersHorizontal } from "lucide-react";
import { getToken } from "@/lib/api/auth";
import { getAccount, resendVerification, updateProfile } from "@/lib/api/account";
import type { MeResponse } from "@/lib/types/api";

export default function ProfileForm() {
  const [account, setAccount] = useState<MeResponse | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const token = getToken();
    if (!token) {
      queueMicrotask(() => setLoading(false));
      return;
    }
    getAccount(token)
      .then((value) => {
        setAccount(value);
        setDisplayName(value.displayName ?? "");
      })
      .catch((cause) => setError(cause instanceof Error ? cause.message : "Failed to load profile"))
      .finally(() => setLoading(false));
  }, []);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    const token = getToken();
    if (!token) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateProfile(token, displayName.trim() || null);
      setAccount(updated);
      setMessage("Profile updated.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to update profile");
    } finally {
      setSaving(false);
    }
  }

  async function resend() {
    const token = getToken();
    if (!token) return;
    setSaving(true);
    setError(null);
    try {
      await resendVerification(token);
      setMessage("Verification email sent.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not resend verification");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <div className="h-56 animate-pulse rounded-2xl bg-white/70" />;
  if (!account) return <div className="rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">{error ?? "Profile unavailable."}</div>;

  return (
    <section className="rounded-2xl border border-stone-200 bg-white/80 p-5 shadow-sm">
      <h2 className="text-lg font-semibold text-stone-900">Profile</h2>
      <form onSubmit={save} className="mt-5 space-y-4">
        <label className="block text-sm font-medium text-stone-700">
          Display name
          <input value={displayName} maxLength={255} onChange={(event) => setDisplayName(event.target.value)} placeholder="Optional" className="mt-1.5 w-full rounded-lg border border-stone-300 px-3 py-2" />
        </label>
        <div className="rounded-xl bg-stone-50 p-4">
          <p className="text-xs font-medium uppercase tracking-wide text-stone-400">Email</p>
          <div className="mt-1 flex flex-wrap items-center gap-3">
            <span className="text-sm text-stone-800">{account.email}</span>
            <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${account.emailVerified ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}>
              {account.emailVerified ? "Verified" : "Verification pending"}
            </span>
            {!account.emailVerified ? <button type="button" disabled={saving} onClick={() => void resend()} className="text-sm font-medium text-[#b75536] hover:underline disabled:opacity-50">Resend verification</button> : null}
          </div>
        </div>
        {message ? <p className="text-sm text-emerald-700">{message}</p> : null}
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        <div className="flex flex-wrap items-center gap-3">
          <button disabled={saving} className="rounded-lg bg-[#c96442] px-4 py-2 text-sm font-medium text-white disabled:opacity-50">{saving ? "Saving..." : "Save profile"}</button>
          <Link href="/settings/models" className="inline-flex items-center gap-1.5 rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm font-medium text-stone-700 hover:border-[#c96442] hover:text-[#b75536]">
            <SlidersHorizontal className="h-4 w-4" /> Manage models and pricing
          </Link>
        </div>
      </form>
    </section>
  );
}
