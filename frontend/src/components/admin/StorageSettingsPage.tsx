"use client";

import { useCallback, useEffect, useState } from "react";
import Button from "@/components/ui/Button";
import AdminShell from "@/components/admin/AdminShell";
import { getToken } from "@/lib/api/auth";
import {
  getStorageSettings,
  updateStorageSettings,
  type StorageSettingsUpdate,
  type StorageSettingsView,
} from "@/lib/api/storageSettings";

const inputClass =
  "w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 shadow-sm focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";
const MB = 1_048_576;
const toMb = (bytes: number) => Math.round((bytes / MB) * 100) / 100;

export default function StorageSettingsPage() {
  const [settings, setSettings] = useState<StorageSettingsView | null>(null);
  const [form, setForm] = useState<StorageSettingsUpdate>({});
  const [secret, setSecret] = useState("");
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
      const s = await getStorageSettings(token);
      setSettings(s);
      setForm({
        backend: s.backend,
        localPublicBaseUrl: s.localPublicBaseUrl ?? undefined,
        s3Endpoint: s.s3Endpoint ?? undefined,
        s3Region: s.s3Region ?? undefined,
        s3Bucket: s.s3Bucket ?? undefined,
        s3AccessKey: s.s3AccessKey ?? undefined,
        s3PublicBaseUrl: s.s3PublicBaseUrl ?? undefined,
        maxImageBytes: s.maxImageBytes,
        maxVideoBytes: s.maxVideoBytes,
        maxAudioBytes: s.maxAudioBytes,
        maxFilesPerPrompt: s.maxFilesPerPrompt,
        maxTotalBytesPerPrompt: s.maxTotalBytesPerPrompt,
      });
      setSecret("");
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to load storage settings");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  const set = <K extends keyof StorageSettingsUpdate>(key: K, value: StorageSettingsUpdate[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const save = async () => {
    const token = getToken();
    if (!token) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const body: StorageSettingsUpdate = { ...form };
      if (secret.trim()) body.s3SecretKey = secret.trim();
      await updateStorageSettings(token, body);
      setSuccess("Storage settings saved.");
      await load();
    } catch (cause) {
      const status = (cause as { status?: number }).status;
      setError(
        status === 422
          ? "S3/OSS endpoint unreachable or credentials invalid — previous settings kept."
          : cause instanceof Error
            ? cause.message
            : "Failed to save",
      );
    } finally {
      setSaving(false);
    }
  };

  const mbInput = (label: string, key: "maxImageBytes" | "maxVideoBytes" | "maxAudioBytes" | "maxTotalBytesPerPrompt") => (
    <label className="block text-sm">
      <span className="text-stone-700">{label} (MB)</span>
      <input
        type="number"
        min="0.1"
        step="0.1"
        value={form[key] != null ? toMb(form[key] as number) : ""}
        onChange={(e) => set(key, Math.round(Number(e.target.value) * MB))}
        className={`mt-1 ${inputClass}`}
      />
    </label>
  );

  return (
    <AdminShell
      title="Media storage"
      description="Choose where user-uploaded media is stored and set the per-type size limits. The S3 secret is encrypted and never shown."
      actions={
        <Button onClick={() => void save()} isLoading={saving} className="!bg-[#c96442] hover:!bg-[#b55538]">
          Test & Save
        </Button>
      }
    >
      {loading ? (
        <div className="h-64 animate-pulse rounded-2xl bg-white/70" />
      ) : (
        <div className="space-y-5">
          {error && <div className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
          {success && <div className="rounded-xl border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">{success}</div>}

          <section className="rounded-2xl border border-stone-200 bg-white p-4 shadow-sm">
            <h2 className="mb-3 text-sm font-semibold text-stone-800">Limits</h2>
            <div className="grid gap-3 sm:grid-cols-3">
              {mbInput("Max image", "maxImageBytes")}
              {mbInput("Max video", "maxVideoBytes")}
              {mbInput("Max audio", "maxAudioBytes")}
              <label className="block text-sm">
                <span className="text-stone-700">Max files / message</span>
                <input type="number" min="1" value={form.maxFilesPerPrompt ?? ""} onChange={(e) => set("maxFilesPerPrompt", Number(e.target.value))} className={`mt-1 ${inputClass}`} />
              </label>
              {mbInput("Max total / message", "maxTotalBytesPerPrompt")}
            </div>
          </section>

          <section className="rounded-2xl border border-stone-200 bg-white p-4 shadow-sm">
            <h2 className="mb-3 text-sm font-semibold text-stone-800">Backend</h2>
            <div className="flex gap-2">
              {(["local", "s3"] as const).map((b) => (
                <button
                  key={b}
                  type="button"
                  onClick={() => set("backend", b)}
                  className={`rounded-lg border px-3 py-1.5 text-sm transition ${
                    form.backend === b ? "border-[#c96442] bg-[#c96442]/10 text-[#b75536]" : "border-stone-200 bg-white text-stone-600 hover:bg-stone-50"
                  }`}
                >
                  {b === "local" ? "Local filesystem" : "S3 / OSS-compatible"}
                </button>
              ))}
            </div>

            {form.backend === "local" ? (
              <label className="mt-4 block text-sm">
                <span className="text-stone-700">Public base URL (where local media is served)</span>
                <input value={form.localPublicBaseUrl ?? ""} onChange={(e) => set("localPublicBaseUrl", e.target.value)} placeholder="http://localhost:8080/media" className={`mt-1 ${inputClass}`} />
              </label>
            ) : (
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <label className="block text-sm"><span className="text-stone-700">Endpoint</span><input value={form.s3Endpoint ?? ""} onChange={(e) => set("s3Endpoint", e.target.value)} placeholder="https://oss-cn-hangzhou.aliyuncs.com" className={`mt-1 ${inputClass}`} /></label>
                <label className="block text-sm"><span className="text-stone-700">Region</span><input value={form.s3Region ?? ""} onChange={(e) => set("s3Region", e.target.value)} placeholder="cn-hangzhou" className={`mt-1 ${inputClass}`} /></label>
                <label className="block text-sm"><span className="text-stone-700">Bucket</span><input value={form.s3Bucket ?? ""} onChange={(e) => set("s3Bucket", e.target.value)} placeholder="octopus-media" className={`mt-1 ${inputClass}`} /></label>
                <label className="block text-sm"><span className="text-stone-700">Public/CDN base URL</span><input value={form.s3PublicBaseUrl ?? ""} onChange={(e) => set("s3PublicBaseUrl", e.target.value)} placeholder="https://octopus-media.oss-cn-hangzhou.aliyuncs.com" className={`mt-1 ${inputClass}`} /></label>
                <label className="block text-sm"><span className="text-stone-700">Access key</span><input value={form.s3AccessKey ?? ""} onChange={(e) => set("s3AccessKey", e.target.value)} className={`mt-1 ${inputClass}`} /></label>
                <label className="block text-sm">
                  <span className="text-stone-700">Secret key {settings?.s3SecretKeySet ? <span className="text-stone-400">(set — leave blank to keep)</span> : null}</span>
                  <input type="password" value={secret} onChange={(e) => setSecret(e.target.value)} placeholder="••••••••" className={`mt-1 ${inputClass}`} />
                </label>
              </div>
            )}
          </section>
        </div>
      )}
    </AdminShell>
  );
}
