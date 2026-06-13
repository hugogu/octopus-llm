"use client";

import { useCallback, useEffect, useState } from "react";
import { getToken } from "@/lib/api/auth";
import {
  getAnalyticsSummary,
  getModelAnalytics,
  getResponseAnalytics,
  getSessionAnalytics,
} from "@/lib/api/analytics";
import type {
  AnalyticsSummary,
  ModelAnalytics,
  ResponseAnalytics,
  SessionAnalytics,
} from "@/lib/types/api";

function costs(values: Record<string, number>): string {
  const entries = Object.entries(values);
  return entries.length ? entries.map(([currency, amount]) => `${amount.toFixed(4)} ${currency}`).join(" · ") : "—";
}

export default function AnalyticsDashboard() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [models, setModels] = useState<ModelAnalytics[]>([]);
  const [sessions, setSessions] = useState<SessionAnalytics[]>([]);
  const [responses, setResponses] = useState<ResponseAnalytics[]>([]);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [configuredModelId, setConfiguredModelId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const token = getToken();
    if (!token) return setLoading(false);
    const query = {
      from: from ? new Date(from).toISOString() : undefined,
      to: to ? new Date(to).toISOString() : undefined,
      configuredModelId: configuredModelId || undefined,
      page: 0,
      size: 25,
    };
    setLoading(true);
    setError(null);
    try {
      const [summaryValue, modelPage, sessionPage, responsePage] = await Promise.all([
        getAnalyticsSummary(token, query),
        getModelAnalytics(token, query),
        getSessionAnalytics(token, query),
        getResponseAnalytics(token, query),
      ]);
      setSummary(summaryValue);
      setModels(modelPage.items);
      setSessions(sessionPage.items);
      setResponses(responsePage.items);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to load analytics");
    } finally {
      setLoading(false);
    }
  }, [configuredModelId, from, to]);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  return (
    <div className="space-y-5">
      <section className="rounded-2xl border border-stone-200 bg-white/80 p-4 shadow-sm">
        <div className="grid gap-3 sm:grid-cols-4">
          <label className="text-sm text-stone-600">From<input type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} className="mt-1 w-full rounded-lg border border-stone-300 px-2 py-2" /></label>
          <label className="text-sm text-stone-600">To<input type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} className="mt-1 w-full rounded-lg border border-stone-300 px-2 py-2" /></label>
          <label className="text-sm text-stone-600">Configured model UUID<input value={configuredModelId} onChange={(event) => setConfiguredModelId(event.target.value)} className="mt-1 w-full rounded-lg border border-stone-300 px-2 py-2" /></label>
          <button onClick={() => void load()} className="self-end rounded-lg bg-[#c96442] px-4 py-2 text-sm font-medium text-white">Apply filters</button>
        </div>
      </section>
      {error ? <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div> : null}
      {loading ? <div className="h-48 animate-pulse rounded-2xl bg-white/70" /> : summary && summary.totalResponses > 0 ? (
        <>
          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Metric label="Responses" value={summary.totalResponses.toLocaleString()} />
            <Metric label="Success" value={`${(summary.successRate * 100).toFixed(1)}%`} />
            <Metric label="Average latency" value={`${Math.round(summary.avgLatencyMs)} ms`} />
            <Metric label="Estimated cost" value={costs(summary.estimatedCostsByCurrency)} />
          </section>
          <AnalyticsTable title="By model" headers={["Model", "Responses", "Success", "Latency", "Tokens", "Cost"]} rows={models.map((item) => [
            item.modelDisplayName, item.responseCount, `${(item.successRate * 100).toFixed(1)}%`,
            `${Math.round(item.avgLatencyMs)} / p95 ${Math.round(item.p95LatencyMs)} ms`,
            `${item.inputTokens} in / ${item.outputTokens} out`, costs(item.estimatedCostsByCurrency),
          ])} />
          <AnalyticsTable title="By conversation" headers={["Conversation", "Responses", "Models", "Success", "Cost"]} rows={sessions.map((item) => [
            item.title || "Untitled", item.responseCount, item.models.join(", "),
            `${(item.successRate * 100).toFixed(1)}%`, costs(item.estimatedCostsByCurrency),
          ])} />
          <AnalyticsTable title="Response detail" headers={["Time", "Model", "Status", "Latency", "IP", "Likes", "Cost"]} rows={responses.map((item) => [
            new Date(item.createdAt).toLocaleString(), item.modelDisplayName, item.status,
            `${item.latencyMs} ms`, item.clientIp ?? "—",
            `${item.namedLikeCount} named / ${item.anonymousLikeCount} anonymous`,
            item.estimatedCost ? `${item.estimatedCost.amount} ${item.estimatedCost.currency}` : "—",
          ])} />
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

function AnalyticsTable({ title, headers, rows }: { title: string; headers: string[]; rows: Array<Array<string | number>> }) {
  return (
    <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white/80">
      <h2 className="border-b border-stone-100 px-4 py-3 font-semibold text-stone-900">{title}</h2>
      <div className="overflow-x-auto">
        <table className="min-w-full text-left text-sm"><thead className="bg-stone-50 text-stone-500"><tr>{headers.map((header) => <th key={header} className="px-4 py-2 font-medium">{header}</th>)}</tr></thead>
          <tbody>{rows.map((row, index) => <tr key={index} className="border-t border-stone-100">{row.map((cell, cellIndex) => <td key={cellIndex} className="whitespace-nowrap px-4 py-2 text-stone-700">{cell}</td>)}</tr>)}</tbody>
        </table>
      </div>
    </section>
  );
}
