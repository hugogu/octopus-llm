"use client";

import { useCallback, useEffect, useState } from "react";
import Button from "@/components/ui/Button";
import AdminShell from "@/components/admin/AdminShell";
import { getToken } from "@/lib/api/auth";
import {
  getSiteSettings,
  updateSiteSettings,
  type SiteSettingsAdmin,
  type SiteSettingsUpdate,
} from "@/lib/api/siteSettings";

const inputClass =
  "w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 shadow-sm focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";

export default function SiteSettingsPage() {
  const [settings, setSettings] = useState<SiteSettingsAdmin | null>(null);
  const [form, setForm] = useState<SiteSettingsUpdate>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const load = useCallback(async () => {
    const token = getToken();
    if (!token) {
      setLoading(false);
      setError("Not authenticated");
      return;
    }
    setLoading(true);
    try {
      const s = await getSiteSettings(token);
      setSettings(s);
      setForm({
        siteName: s.siteName ?? "",
        footerText: s.footerText ?? "",
        icpRecordNo: s.icpRecordNo ?? "",
        policeRecordNo: s.policeRecordNo ?? "",
      });
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to load site settings");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  const set = <K extends keyof SiteSettingsUpdate>(key: K, value: SiteSettingsUpdate[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const save = async () => {
    const token = getToken();
    if (!token) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await updateSiteSettings(token, {
        siteName: form.siteName ?? null,
        footerText: form.footerText ?? null,
        icpRecordNo: form.icpRecordNo ?? null,
        policeRecordNo: form.policeRecordNo ?? null,
      });
      setSuccess("Site settings saved.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      title="Site info"
      description="Configure the public site-info shown in the footer at the bottom of every page. Leave any field blank to hide it."
      actions={
        <Button onClick={() => void save()} isLoading={saving} className="!bg-[#c96442] hover:!bg-[#b55538]">
          Save
        </Button>
      }
    >
      {loading ? (
        <div className="h-64 animate-pulse rounded-2xl bg-white/70" />
      ) : (
        <div className="space-y-4">
          {error && (
            <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </div>
          )}
          {success && (
            <div className="rounded-xl border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">
              {success}
            </div>
          )}
          <div className="grid gap-4 lg:grid-cols-2">
            <section className="rounded-2xl border border-stone-200 bg-white p-4 shadow-sm">
              <div className="space-y-3">
                <label className="block text-sm">
                  <span className="text-stone-700">Site name</span>
                  <input
                    value={form.siteName ?? ""}
                    onChange={(e) => set("siteName", e.target.value)}
                    placeholder="Octopus LLM"
                    className={`mt-1 ${inputClass}`}
                  />
                </label>
                <label className="block text-sm">
                  <span className="text-stone-700">Footer text</span>
                  <textarea
                    value={form.footerText ?? ""}
                    onChange={(e) => set("footerText", e.target.value)}
                    placeholder="© 2026 Octopus LLM"
                    rows={2}
                    className={`mt-1 ${inputClass}`}
                  />
                </label>
              </div>
            </section>
            <section className="rounded-2xl border border-stone-200 bg-white p-4 shadow-sm">
              <h2 className="mb-3 text-sm font-semibold text-stone-800">Chinese site records</h2>
              <p className="mb-3 text-xs text-stone-500">
                These render at the footer with their standard reference links. Leave blank to omit.
              </p>
              <div className="space-y-3">
                <label className="block text-sm">
                  <span className="text-stone-700">ICP record number</span>
                  <input
                    value={form.icpRecordNo ?? ""}
                    onChange={(e) => set("icpRecordNo", e.target.value)}
                    placeholder="京ICP备12345678号-1"
                    className={`mt-1 ${inputClass}`}
                  />
                </label>
                <label className="block text-sm">
                  <span className="text-stone-700">Public-security record number</span>
                  <input
                    value={form.policeRecordNo ?? ""}
                    onChange={(e) => set("policeRecordNo", e.target.value)}
                    placeholder="京公网安备11010102000001号"
                    className={`mt-1 ${inputClass}`}
                  />
                </label>
              </div>
            </section>
          </div>
          {settings && (
            <p className="text-xs text-stone-500">
              Last updated {new Date(settings.updatedAt).toLocaleString()}.
            </p>
          )}
        </div>
      )}
    </AdminShell>
  );
}
