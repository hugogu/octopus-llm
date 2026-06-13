"use client";

import { useState } from "react";
import { changePassword } from "@/lib/api/account";
import { getToken } from "@/lib/api/auth";

export default function PasswordChangeForm() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setMessage(null);
    if (newPassword.length < 8) return setError("New password must be at least 8 characters.");
    if (newPassword !== confirm) return setError("New passwords do not match.");
    const token = getToken();
    if (!token) return setError("Not authenticated.");
    setLoading(true);
    try {
      await changePassword(token, currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setConfirm("");
      setMessage("Password updated. Other signed-in sessions have been invalidated.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Password update failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="rounded-2xl border border-stone-200 bg-white/80 p-5 shadow-sm">
      <h2 className="text-lg font-semibold text-stone-900">Change password</h2>
      <form onSubmit={submit} className="mt-5 max-w-xl space-y-4">
        {[
          ["Current password", currentPassword, setCurrentPassword],
          ["New password", newPassword, setNewPassword],
          ["Confirm new password", confirm, setConfirm],
        ].map(([label, value, setter]) => (
          <label key={label as string} className="block text-sm font-medium text-stone-700">
            {label as string}
            <input type="password" required value={value as string} onChange={(event) => (setter as (value: string) => void)(event.target.value)} className="mt-1.5 w-full rounded-lg border border-stone-300 px-3 py-2" />
          </label>
        ))}
        {message ? <p className="text-sm text-emerald-700">{message}</p> : null}
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        <button disabled={loading} className="rounded-lg bg-[#c96442] px-4 py-2 text-sm font-medium text-white disabled:opacity-50">{loading ? "Updating..." : "Update password"}</button>
      </form>
    </section>
  );
}
