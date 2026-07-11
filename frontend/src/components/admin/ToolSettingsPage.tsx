"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Check, Star, X } from "lucide-react";
import Button from "@/components/ui/Button";
import AdminShell from "@/components/admin/AdminShell";
import { getToken } from "@/lib/api/auth";
import {
  getToolSettings,
  updateToolActivation,
  updateWebSearchProvider,
  type ToolSettingsAdmin,
} from "@/lib/api/toolSettings";

const inputClass =
  "w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 shadow-sm focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";

type ProviderForm = { baseUrl: string; model: string; apiKey: string };

export default function ToolSettingsPage() {
  const [settings, setSettings] = useState<ToolSettingsAdmin | null>(null);
  const [forms, setForms] = useState<Record<string, ProviderForm>>({});
  const [activeTab, setActiveTab] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const applySettings = useCallback((s: ToolSettingsAdmin) => {
    setSettings(s);
    setForms(
      Object.fromEntries(
        s.webSearchProviders.map((p) => [p.id, { baseUrl: p.baseUrl, model: p.model, apiKey: "" }]),
      ),
    );
    setActiveTab((prev) => prev || s.webSearchActiveProvider || s.webSearchProviders[0]?.id || "");
  }, []);

  const load = useCallback(async () => {
    const token = getToken();
    if (!token) {
      setLoading(false);
      setError("Not authenticated");
      return;
    }
    setLoading(true);
    try {
      applySettings(await getToolSettings(token));
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to load tool settings");
    } finally {
      setLoading(false);
    }
  }, [applySettings]);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  const provider = useMemo(
    () => settings?.webSearchProviders.find((p) => p.id === activeTab),
    [settings, activeTab],
  );
  const form = forms[activeTab] ?? { baseUrl: "", model: "", apiKey: "" };
  const setField = (key: keyof ProviderForm, value: string) =>
    setForms((f) => ({ ...f, [activeTab]: { ...form, [key]: value } }));

  const run = async (label: string, fn: (token: string) => Promise<ToolSettingsAdmin>) => {
    const token = getToken();
    if (!token) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      applySettings(await fn(token));
      setSuccess(label);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  };

  const saveProvider = () =>
    run("已保存该 Provider 配置。", (token) =>
      updateWebSearchProvider(token, activeTab, {
        baseUrl: form.baseUrl,
        model: form.model,
        apiKey: form.apiKey.trim() ? form.apiKey : undefined,
      }),
    );
  const setActive = () =>
    run("已切换为当前使用的 Provider。", (token) => updateToolActivation(token, { webSearchActiveProvider: activeTab }));
  const toggleEnabled = () =>
    run(
      settings?.webSearchEnabled ? "已停用 web_search。" : "已启用 web_search。",
      (token) => updateToolActivation(token, { webSearchEnabled: !settings?.webSearchEnabled }),
    );

  return (
    <AdminShell title="Tools" description="配置对话中可供模型调用的内置工具，及各搜索 Provider 的凭据。">
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

          {/* web_search providers */}
          <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm">
            <header className="flex flex-wrap items-center justify-between gap-3 border-b border-stone-200 bg-stone-50/70 p-4">
              <div>
                <h2 className="text-sm font-semibold text-stone-800">联网搜索 (web_search) Provider</h2>
                <p className="mt-0.5 text-xs text-stone-500">各 Provider 的配置可并存；选择一个作为当前使用即可。</p>
              </div>
              <label className="flex items-center gap-2 text-sm text-stone-700">
                <input
                  type="checkbox"
                  checked={settings?.webSearchEnabled ?? false}
                  onChange={() => void toggleEnabled()}
                  disabled={saving}
                  className="h-4 w-4 rounded border-stone-300 text-[#c96442] focus:ring-[#c96442]"
                />
                启用 web_search
              </label>
            </header>

            {/* Provider tabs */}
            <div className="flex flex-wrap gap-1.5 border-b border-stone-100 p-3">
              {settings?.webSearchProviders.map((p) => {
                const isActive = p.id === settings.webSearchActiveProvider;
                const selected = p.id === activeTab;
                return (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setActiveTab(p.id)}
                    className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium transition ${
                      selected
                        ? "border-[#c96442] bg-[#c96442] text-white"
                        : "border-stone-300 bg-white text-stone-600 hover:border-stone-500"
                    }`}
                  >
                    {isActive && <Star className={`h-3 w-3 ${selected ? "text-white" : "text-[#c96442]"}`} fill="currentColor" />}
                    {p.label}
                    {p.apiKeySet && (
                      <Check className={`h-3 w-3 ${selected ? "text-white" : "text-green-600"}`} />
                    )}
                  </button>
                );
              })}
            </div>

            {/* Active tab form */}
            {provider && (
              <div className="space-y-4 p-4">
                <div className="flex items-center gap-2 text-xs text-stone-500">
                  {provider.id === settings?.webSearchActiveProvider ? (
                    <span className="inline-flex items-center gap-1 rounded-full bg-[#c96442]/10 px-2 py-0.5 font-medium text-[#b75536]">
                      <Star className="h-3 w-3" fill="currentColor" /> 当前使用
                    </span>
                  ) : (
                    <Button
                      onClick={() => void setActive()}
                      isLoading={saving}
                      className="!bg-white !px-2.5 !py-1 !text-xs !text-[#b75536] !shadow-none ring-1 ring-[#c96442]/40 hover:!bg-[#c96442]/5"
                    >
                      设为当前使用
                    </Button>
                  )}
                  {provider.apiKeySet && <span className="text-green-600">已配置 Key</span>}
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-stone-600">Base URL</label>
                    <input className={inputClass} value={form.baseUrl} onChange={(e) => setField("baseUrl", e.target.value)} />
                  </div>
                  {provider.needsModel && (
                    <div>
                      <label className="mb-1 block text-xs font-medium text-stone-600">Model</label>
                      <input className={inputClass} value={form.model} onChange={(e) => setField("model", e.target.value)} />
                    </div>
                  )}
                </div>

                <div>
                  <label className="mb-1 block text-xs font-medium text-stone-600">
                    API Key {provider.apiKeySet ? <span className="text-green-600">（已配置，留空则保持不变）</span> : null}
                  </label>
                  <input
                    type="password"
                    className={inputClass}
                    value={form.apiKey}
                    onChange={(e) => setField("apiKey", e.target.value)}
                    placeholder={provider.apiKeySet ? "••••••••（保持不变）" : "输入 Provider API Key"}
                    autoComplete="off"
                  />
                </div>

                <div className="flex justify-end">
                  <Button onClick={() => void saveProvider()} isLoading={saving} className="!bg-[#c96442] hover:!bg-[#b55538]">
                    保存该 Provider
                  </Button>
                </div>
              </div>
            )}
          </section>
        </div>
      )}
    </AdminShell>
  );
}
