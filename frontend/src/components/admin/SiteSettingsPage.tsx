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
        chinaFilingEnabled: s.chinaFilingEnabled,
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
    setError(null);
    setSuccess(null);
    const chinaFilingEnabled = form.chinaFilingEnabled ?? false;
    if (chinaFilingEnabled && !(form.icpRecordNo ?? "").trim()) {
      setError("Enter an ICP record number before showing Chinese filing information.");
      return;
    }
    setSaving(true);
    try {
      await updateSiteSettings(token, {
        siteName: form.siteName ?? null,
        footerText: form.footerText ?? null,
        chinaFilingEnabled,
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
              <div className="mb-3 flex items-start justify-between gap-4">
                <div>
                  <h2 className="text-sm font-semibold text-stone-800">Chinese site records</h2>
                  <p className="mt-1 text-xs text-stone-500">
                    Enable this to show the records in the public footer. An ICP number is required when enabled.
                  </p>
                </div>
                <button
                  type="button"
                  role="switch"
                  aria-checked={form.chinaFilingEnabled ?? false}
                  aria-label="Show Chinese filing information"
                  onClick={() => set("chinaFilingEnabled", !(form.chinaFilingEnabled ?? false))}
                  disabled={saving}
                  className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition ${form.chinaFilingEnabled ? "bg-[#c96442]" : "bg-stone-300"}`}
                >
                  <span className={`inline-block h-4 w-4 rounded-full bg-white shadow transition ${form.chinaFilingEnabled ? "translate-x-6" : "translate-x-1"}`} />
                </button>
              </div>
              <div className="space-y-3">
                <label className="block text-sm">
                  <span className="text-stone-700">ICP record number {form.chinaFilingEnabled ? <span className="text-red-600">*</span> : <span className="text-stone-400">(required when enabled)</span>}</span>
                  <input
                    value={form.icpRecordNo ?? ""}
                    onChange={(e) => set("icpRecordNo", e.target.value)}
                    placeholder="京ICP备12345678号-1"
                    aria-required={form.chinaFilingEnabled ?? false}
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
