"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { getPublicModelAnalytics } from "@/lib/api/analytics";
import type { PublicModelAnalytics as PublicModelAnalyticsRow } from "@/lib/types/api";

export default function PublicModelAnalytics() {
  const [items, setItems] = useState<PublicModelAnalyticsRow[]>([]);
  const [protocol, setProtocol] = useState("");
  const [modelId, setModelId] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getPublicModelAnalytics({ protocol, modelId, page, size: 25 });
      setItems(result.items);
      setTotalPages(result.totalPages);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to load public analytics");
    } finally {
      setLoading(false);
    }
  }, [modelId, page, protocol]);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  return (
    <main className="min-h-screen bg-[#faf9f5] px-4 py-10 sm:px-6">
      <div className="mx-auto max-w-5xl">
        <Link href="/" className="inline-flex items-center rounded-lg px-2.5 py-1.5 text-sm font-medium text-stone-600 hover:bg-white">
          <ArrowLeft className="mr-1.5 h-4 w-4" /> Back to home
        </Link>
        <p className="mt-4 text-xs font-semibold uppercase tracking-[0.2em] text-[#b75536]">Public analytics</p>
        <h1 className="mt-1 text-3xl font-semibold text-stone-900">Model usage across Octopus LLM</h1>
        <p className="mt-2 max-w-2xl text-sm text-stone-600">Anonymous aggregates only. No account, conversation, connection, prompt, or response data is included.</p>
        <section className="mt-6 grid gap-3 rounded-2xl border border-stone-200 bg-white p-4 sm:grid-cols-[1fr_1fr_auto]">
          <input value={protocol} onChange={(event) => setProtocol(event.target.value)} placeholder="Protocol" className="rounded-lg border border-stone-300 px-3 py-2" />
          <input value={modelId} onChange={(event) => setModelId(event.target.value)} placeholder="Literal model ID" className="rounded-lg border border-stone-300 px-3 py-2" />
          <button onClick={() => { setPage(0); void load(); }} className="rounded-lg bg-[#c96442] px-4 py-2 text-sm font-medium text-white">Filter</button>
        </section>
        {error ? <p className="mt-5 rounded-xl bg-red-50 p-4 text-sm text-red-700">{error}</p> : null}
        {loading ? <div className="mt-5 h-48 animate-pulse rounded-2xl bg-white" /> : items.length === 0 ? (
          <div className="mt-5 rounded-2xl border border-dashed border-stone-300 bg-white p-12 text-center text-sm text-stone-500">No aggregate data matches these filters.</div>
        ) : (
          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            {items.map((item) => (
              <article key={`${item.protocol}:${item.modelId}`} className="rounded-2xl border border-stone-200 bg-white p-5 shadow-sm">
                <p className="text-xs uppercase tracking-wide text-stone-400">{item.protocol}</p>
                <h2 className="mt-1 break-all font-mono text-sm font-semibold text-stone-900">{item.modelId}</h2>
                <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                  <Stat label="Responses" value={item.responseCount.toLocaleString()} />
                  <Stat label="Success" value={`${(item.successRate * 100).toFixed(1)}%`} />
                  <Stat label="Avg / p95 latency" value={`${Math.round(item.avgLatencyMs)} / ${Math.round(item.p95LatencyMs)} ms`} />
                  <Stat label="Likes" value={`${item.namedLikeCount} named · ${item.anonymousLikeCount} anonymous`} />
                  <Stat label="Input tokens" value={item.inputTokens.toLocaleString()} />
                  <Stat label="Output tokens" value={item.outputTokens.toLocaleString()} />
                </dl>
              </article>
            ))}
          </div>
        )}
        <div className="mt-5 flex items-center justify-between">
          <button disabled={page === 0} onClick={() => setPage((value) => value - 1)} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-40">Previous</button>
          <span className="text-sm text-stone-500">Page {page + 1} of {Math.max(totalPages, 1)}</span>
          <button disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-40">Next</button>
        </div>
      </div>
    </main>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs text-stone-400">{label}</dt><dd className="mt-0.5 text-stone-700">{value}</dd></div>;
}
