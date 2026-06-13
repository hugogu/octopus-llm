"use client";

import { useCallback, useEffect, useState } from "react";
import { KeyRound, Search, ShieldCheck, Trash2 } from "lucide-react";
import Button from "@/components/ui/Button";
import AdminShell from "@/components/admin/AdminShell";
import { getToken } from "@/lib/api/auth";
import {
  activateUser,
  deactivateUser,
  deleteUser,
  disableUser,
  enableUser,
  listUsers,
  purgeTestAccounts,
  resetUserPassword,
} from "@/lib/api/admin";
import type { AdminUser } from "@/lib/types/api";

function Pill({ tone, children }: { tone: "green" | "red" | "amber" | "accent" | "stone"; children: React.ReactNode }) {
  const tones = {
    green: "bg-green-100 text-green-700",
    red: "bg-red-100 text-red-700",
    amber: "bg-amber-100 text-amber-700",
    accent: "bg-[#c96442]/10 text-[#b75536]",
    stone: "bg-stone-200 text-stone-600",
  } as const;
  return <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${tones[tone]}`}>{children}</span>;
}

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [query, setQuery] = useState("");
  const [testOnly, setTestOnly] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [purging, setPurging] = useState(false);

  const token = getToken() ?? "";

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listUsers(token, query, 0, 50, testOnly);
      setUsers(page.items);
      setError(null);
    } catch {
      setError("Failed to load users.");
    } finally {
      setLoading(false);
    }
  }, [token, query, testOnly]);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  async function run(id: string, action: () => Promise<unknown>, success: string) {
    setError(null);
    setNotice(null);
    setBusyId(id);
    try {
      await action();
      setNotice(success);
      await load();
    } catch (e) {
      const status = (e as { status?: number }).status;
      setError(
        status === 409
          ? "Refused: this would lock out the last administrator."
          : status === 422
            ? "Refused: demote the administrator before deleting."
            : "Action failed.",
      );
    } finally {
      setBusyId(null);
    }
  }

  async function purge() {
    if (!confirm("Permanently delete ALL suspected test accounts (reserved example/test email domains)? This cannot be undone.")) {
      return;
    }
    setError(null);
    setNotice(null);
    setPurging(true);
    try {
      const { deleted } = await purgeTestAccounts(token);
      setNotice(`Deleted ${deleted} test account${deleted === 1 ? "" : "s"}.`);
      await load();
    } catch {
      setError("Purge failed.");
    } finally {
      setPurging(false);
    }
  }

  return (
    <AdminShell
      title="User management"
      description="Activate registered accounts, allocate built-in connections, disable or re-enable access, and trigger password resets. BYOK stays available to every active account."
      actions={
        <Button variant="danger" isLoading={purging} onClick={() => void purge()}>
          <Trash2 className="mr-1.5 h-4 w-4" /> Delete test accounts
        </Button>
      }
    >
      <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void load();
          }}
          className="flex flex-1 items-center gap-2"
        >
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-stone-400" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by email"
              className="w-full rounded-xl border border-stone-300 bg-white py-2.5 pl-9 pr-3 text-sm text-stone-800 shadow-sm focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]"
            />
          </div>
          <Button type="submit" variant="secondary">
            Search
          </Button>
        </form>
        <button
          type="button"
          onClick={() => setTestOnly((v) => !v)}
          className={`inline-flex items-center gap-1.5 rounded-xl border px-3 py-2.5 text-sm font-medium transition-colors ${
            testOnly
              ? "border-[#c96442] bg-[#c96442]/10 text-[#b75536]"
              : "border-stone-300 bg-white text-stone-600 hover:bg-stone-50"
          }`}
        >
          <Trash2 className="h-4 w-4" />
          Suspected test only
        </button>
      </div>

      {error ? (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      ) : null}
      {notice ? (
        <div className="mb-4 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{notice}</div>
      ) : null}

      {loading ? (
        <div className="space-y-3">
          <div className="h-16 animate-pulse rounded-2xl bg-white/70" />
          <div className="h-16 animate-pulse rounded-2xl bg-white/70" />
          <div className="h-16 animate-pulse rounded-2xl bg-white/70" />
        </div>
      ) : users.length === 0 ? (
        <section className="rounded-3xl border border-dashed border-stone-300 bg-white/70 px-8 py-16 text-center">
          <h2 className="text-lg font-semibold text-stone-900">No accounts found</h2>
          <p className="mx-auto mt-2 max-w-lg text-sm text-stone-500">Try a different search, or wait for users to register.</p>
        </section>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-stone-200 bg-stone-50/70 text-left text-xs uppercase tracking-wide text-stone-500">
                  <th className="px-4 py-3 font-medium">Account</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id} className="border-b border-stone-100 last:border-0 hover:bg-stone-50/50">
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-medium text-stone-900">{u.email}</span>
                        {u.isAdmin ? (
                          <Pill tone="accent">
                            <ShieldCheck className="mr-0.5 inline h-3 w-3 align-[-1px]" />
                            Admin
                          </Pill>
                        ) : null}
                        {u.suspectedTest ? <Pill tone="amber">Suspected test</Pill> : null}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <Pill tone={u.isDisabled ? "red" : "green"}>{u.isDisabled ? "Disabled" : "Enabled"}</Pill>
                        <Pill tone={u.isActive ? "green" : "amber"}>{u.isActive ? "Activated" : "Not activated"}</Pill>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap items-center justify-end gap-1.5">
                        {!u.isActive ? (
                          <Button size="sm" variant="secondary" isLoading={busyId === u.id} onClick={() => void run(u.id, () => activateUser(token, u.id), "User activated.")}>
                            Activate
                          </Button>
                        ) : (
                          <Button size="sm" variant="ghost" isLoading={busyId === u.id} onClick={() => void run(u.id, () => deactivateUser(token, u.id), "User deactivated.")}>
                            Deactivate
                          </Button>
                        )}
                        {u.isDisabled ? (
                          <Button size="sm" variant="secondary" isLoading={busyId === u.id} onClick={() => void run(u.id, () => enableUser(token, u.id), "User enabled.")}>
                            Enable
                          </Button>
                        ) : (
                          <Button size="sm" variant="danger" isLoading={busyId === u.id} onClick={() => void run(u.id, () => disableUser(token, u.id), "User disabled.")}>
                            Disable
                          </Button>
                        )}
                        <Button size="sm" variant="ghost" isLoading={busyId === u.id} onClick={() => void run(u.id, () => resetUserPassword(token, u.id), "Password reset email sent.")}>
                          <KeyRound className="mr-1 h-3.5 w-3.5" /> Reset
                        </Button>
                        {!u.isAdmin ? (
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-red-600"
                            isLoading={busyId === u.id}
                            onClick={() => {
                              if (confirm(`Permanently delete ${u.email} and all of its data? This cannot be undone.`)) {
                                void run(u.id, () => deleteUser(token, u.id), "User deleted.");
                              }
                            }}
                          >
                            <Trash2 className="mr-1 h-3.5 w-3.5" /> Delete
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </AdminShell>
  );
}
