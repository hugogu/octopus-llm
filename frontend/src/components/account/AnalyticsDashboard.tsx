"use client";

import { useCallback, useEffect, useState } from "react";
import { getToken } from "@/lib/api/auth";
import { getAnalyticsSummary, getAnalyticsTimeseries } from "@/lib/api/analytics";
import { listConfiguredModels } from "@/lib/api/connections";
import type { AnalyticsSummary, AnalyticsTimePoint, ConfiguredModelV2 } from "@/lib/types/api";
import TrendChart from "@/components/account/TrendChart";

function costs(values: Record<string, number>): string {
  const entries = Object.entries(values);
  return entries.length ? entries.map(([currency, amount]) => `${amount.toFixed(4)} ${currency}`).join(" · ") : "—";
}

function dayLabel(bucket: string): string {
  const date = new Date(bucket);
  return Number.isNaN(date.getTime()) ? bucket : date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function AnalyticsDashboard() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [series, setSeries] = useState<AnalyticsTimePoint[]>([]);
  const [modelOptions, setModelOptions] = useState<ConfiguredModelV2[]>([]);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [configuredModelId, setConfiguredModelId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load the caller's configured models once so the filter is a readable name picker, not a UUID box.
  useEffect(() => {
    const token = getToken();
    if (!token) return;
    queueMicrotask(() => {
      void listConfiguredModels(token)
        .then((page) => setModelOptions(page.items))
        .catch(() => setModelOptions([]));
    });
  }, []);

  const load = useCallback(async () => {
    const token = getToken();
    if (!token) return setLoading(false);
    const query = {
      from: from ? new Date(from).toISOString() : undefined,
      to: to ? new Date(to).toISOString() : undefined,
      configuredModelId: configuredModelId || undefined,
    };
    setLoading(true);
    setError(null);
    try {
      const [summaryValue, timeseries] = await Promise.all([
        getAnalyticsSummary(token, query),
        getAnalyticsTimeseries(token, query),
      ]);
      setSummary(summaryValue);
      setSeries(timeseries.items);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to load analytics");
    } finally {
      setLoading(false);
    }
  }, [configuredModelId, from, to]);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  const labels = series.map((point) => ({ label: dayLabel(point.bucket), point }));

  return (
    <div className="space-y-5">
      <section className="rounded-2xl border border-stone-200 bg-white/80 p-4 shadow-sm">
        <div className="grid gap-3 sm:grid-cols-4">
          <label className="text-sm text-stone-600">From
            <input type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} className="mt-1 w-full rounded-lg border border-stone-300 px-2 py-2 focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]" />
          </label>
          <label className="text-sm text-stone-600">To
            <input type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} className="mt-1 w-full rounded-lg border border-stone-300 px-2 py-2 focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]" />
          </label>
          <label className="text-sm text-stone-600">Model
            <select value={configuredModelId} onChange={(event) => setConfiguredModelId(event.target.value)} className="mt-1 w-full rounded-lg border border-stone-300 bg-white px-2 py-2 focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]">
              <option value="">All models</option>
              {modelOptions.map((model) => (
                <option key={model.id} value={model.id}>{model.displayName}</option>
              ))}
            </select>
          </label>
          <button onClick={() => void load()} className="self-end rounded-lg bg-[#c96442] px-4 py-2 text-sm font-medium text-white hover:bg-[#b55538]">Apply filters</button>
        </div>
      </section>

      {error ? <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div> : null}

      {loading ? (
        <div className="space-y-3">
          <div className="h-24 animate-pulse rounded-2xl bg-white/70" />
          <div className="h-48 animate-pulse rounded-2xl bg-white/70" />
        </div>
      ) : summary && summary.totalResponses > 0 ? (
        <>
          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Metric label="Responses" value={summary.totalResponses.toLocaleString()} />
            <Metric label="Success" value={`${(summary.successRate * 100).toFixed(1)}%`} />
            <Metric label="Average latency" value={`${Math.round(summary.avgLatencyMs)} ms`} />
            <Metric label="Estimated cost" value={costs(summary.estimatedCostsByCurrency)} />
          </section>

          <div className="grid gap-4 lg:grid-cols-3">
            <TrendChart
              title="Latency"
              points={labels.map(({ label, point }) => ({ label, value: point.avgLatencyMs }))}
              format={(value) => `${Math.round(value)} ms`}
            />
            <TrendChart
              title="Success rate"
              points={labels.map(({ label, point }) => ({ label, value: point.successRate * 100 }))}
              format={(value) => `${value.toFixed(1)}%`}
              color="#16a34a"
            />
            <TrendChart
              title="Token usage"
              points={labels.map(({ label, point }) => ({ label, value: point.inputTokens + point.outputTokens }))}
              format={(value) => Math.round(value).toLocaleString()}
              color="#2563eb"
            />
          </div>
        </>
      ) : (
        <div className="rounded-2xl border border-dashed border-stone-300 bg-white/70 p-12 text-center text-sm text-stone-500">No response history matches these filters.</div>
      )}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-2xl border border-stone-200 bg-white/80 p-4"><p className="text-xs uppercase tracking-wide text-stone-400">{label}</p><p className="mt-2 text-xl font-semibold text-stone-900">{value}</p></div>;
}
