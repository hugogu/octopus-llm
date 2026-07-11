"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import Button from "@/components/ui/Button";
import AdminShell from "@/components/admin/AdminShell";
import { getToken } from "@/lib/api/auth";
import {
  getToolSettings,
  updateToolSettings,
  type ToolSettingsAdmin,
  type ToolSettingsUpdate,
} from "@/lib/api/toolSettings";

const inputClass =
  "w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 shadow-sm focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";

export default function ToolSettingsPage() {
  const [settings, setSettings] = useState<ToolSettingsAdmin | null>(null);
  const [form, setForm] = useState<ToolSettingsUpdate>({});
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
      const s = await getToolSettings(token);
      setSettings(s);
      setForm({
        webSearchEnabled: s.webSearch.enabled,
        webSearchProvider: s.webSearch.provider,
        webSearchBaseUrl: s.webSearch.baseUrl ?? "",
        webSearchModel: s.webSearch.model ?? "",
      });
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to load tool settings");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  const set = <K extends keyof ToolSettingsUpdate>(key: K, value: ToolSettingsUpdate[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const applyProvider = (id: string) => {
    const preset = settings?.webSearchProviders.find((p) => p.id === id);
    setForm((f) => ({
      ...f,
      webSearchProvider: id,
      webSearchBaseUrl: preset?.defaultBaseUrl ?? f.webSearchBaseUrl,
      webSearchModel: preset?.defaultModel ?? f.webSearchModel,
    }));
  };

  const save = async () => {
    const token = getToken();
    if (!token) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const s = await updateToolSettings(token, {
        ...form,
        // Only send the key when the admin actually typed one; blank keeps the stored key.
        webSearchApiKey: form.webSearchApiKey?.trim() ? form.webSearchApiKey : undefined,
      });
      setSettings(s);
      setForm((f) => ({ ...f, webSearchApiKey: "" }));
      setSuccess("Saved.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AdminShell
      title="Tools"
      description="配置对话中可供模型调用的内置工具，及其使用的搜索 Provider。"
    >
      {loading ? (
        <div className="h-40 animate-pulse rounded-2xl bg-white/70" />
      ) : (
        <div className="space-y-4">
          {error && (
            <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}
          {success && (
            <div className="rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{success}</div>
          )}

          {/* Supported tools */}
          <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm">
            <header className="border-b border-stone-200 bg-stone-50/70 p-4">
              <h2 className="text-sm font-semibold text-stone-800">支持的工具</h2>
              <p className="mt-0.5 text-xs text-stone-500">当前系统内置的工具及可用状态。</p>
            </header>
            <ul className="divide-y divide-stone-100">
              {settings?.tools.map((tool) => (
                <li key={tool.name} className="flex items-center justify-between gap-3 p-4">
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 text-sm font-semibold text-stone-800">
                      <span className="font-mono">{tool.name}</span>
                      <span className="text-stone-500">{tool.label}</span>
                    </p>
                    <p className="mt-0.5 text-xs text-stone-500">{tool.description}</p>
                  </div>
                  <span
                    className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${
                      tool.available ? "bg-green-100 text-green-700" : "bg-stone-100 text-stone-500"
                    }`}
                  >
                    {tool.available ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
                    {tool.available ? "可用" : "未启用"}
                  </span>
                </li>
              ))}
            </ul>
          </section>

          {/* web_search provider config */}
          <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm">
            <header className="border-b border-stone-200 bg-stone-50/70 p-4">
              <h2 className="text-sm font-semibold text-stone-800">联网搜索 (web_search) Provider</h2>
              <p className="mt-0.5 text-xs text-stone-500">
                启用后，所有支持工具调用的模型都可通过 web_search 联网检索（涵盖新闻/股价/天气）。
              </p>
            </header>
            <div className="space-y-4 p-4">
              <label className="flex items-center gap-2 text-sm text-stone-700">
                <input
                  type="checkbox"
                  checked={form.webSearchEnabled ?? false}
                  onChange={(e) => set("webSearchEnabled", e.target.checked)}
                  className="h-4 w-4 rounded border-stone-300 text-[#c96442] focus:ring-[#c96442]"
                />
                启用 web_search
              </label>

              <div>
                <label className="mb-1 block text-xs font-medium text-stone-600">Provider</label>
                <select
                  value={form.webSearchProvider ?? ""}
                  onChange={(e) => applyProvider(e.target.value)}
                  className={inputClass}
                >
                  {settings?.webSearchProviders.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1 block text-xs font-medium text-stone-600">Base URL</label>
                  <input
                    className={inputClass}
                    value={form.webSearchBaseUrl ?? ""}
                    onChange={(e) => set("webSearchBaseUrl", e.target.value)}
                    placeholder="https://token-plan-cn.xiaomimimo.com/v1"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-stone-600">Model</label>
                  <input
                    className={inputClass}
                    value={form.webSearchModel ?? ""}
                    onChange={(e) => set("webSearchModel", e.target.value)}
                    placeholder="mimo-v2.5-pro"
                  />
                </div>
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-stone-600">
                  API Key {settings?.webSearch.apiKeySet ? <span className="text-green-600">（已配置，留空则保持不变）</span> : null}
                </label>
                <input
                  type="password"
                  className={inputClass}
                  value={form.webSearchApiKey ?? ""}
                  onChange={(e) => set("webSearchApiKey", e.target.value)}
                  placeholder={settings?.webSearch.apiKeySet ? "••••••••（保持不变）" : "输入 Provider API Key"}
                  autoComplete="off"
                />
              </div>

              <div className="flex justify-end">
                <Button onClick={() => void save()} isLoading={saving} className="!bg-[#c96442] hover:!bg-[#b55538]">
                  保存
                </Button>
              </div>
            </div>
          </section>
        </div>
      )}
    </AdminShell>
  );
}
