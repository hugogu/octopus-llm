"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Check, ChevronLeft, ChevronRight, Eye, EyeOff, Search, Trash2 } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import AdminShell from "@/components/admin/AdminShell";
import { getToken } from "@/lib/api/auth";
import {
  executeAdminModelBulk,
  listAdminModels,
  previewAdminModelBulk,
  type AdminModelAccessFilter,
  type AdminModelBulkAction,
  type AdminModelSelection,
} from "@/lib/api/adminModelAccess";
import { confirmDialog } from "@/lib/ui/confirm";
import type { AdminModelAccess } from "@/lib/types/api";

const inputClass = "rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";

export default function AdminModelAccessPage() {
  const token = getToken() ?? "";
  const router = useRouter();
  const searchParams = useSearchParams();
  const [models, setModels] = useState<AdminModelAccess[]>([]);
  const [q, setQ] = useState(searchParams.get("q") ?? "");
  const [protocol, setProtocol] = useState(searchParams.get("protocol") ?? "");
  const [enabled, setEnabled] = useState(searchParams.get("enabled") ?? "all");
  const [anonymousAllowed, setAnonymousAllowed] = useState(searchParams.get("anonymousAllowed") ?? "all");
  const [page, setPage] = useState(Number(searchParams.get("page") ?? "0") || 0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [selectAllMatching, setSelectAllMatching] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [result, setResult] = useState<Awaited<ReturnType<typeof executeAdminModelBulk>> | null>(null);

  const filter = useMemo<AdminModelAccessFilter>(() => ({
    q: q.trim() || undefined,
    protocol: protocol || undefined,
    enabled: enabled === "all" ? undefined : enabled === "true",
    anonymousAllowed: anonymousAllowed === "all" ? undefined : anonymousAllowed === "true",
  }), [anonymousAllowed, enabled, protocol, q]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listAdminModels(token, filter, page, 50);
      setModels(response.items);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
      setError(null);
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : "Unable to load models.");
    } finally {
      setLoading(false);
    }
  }, [filter, page, token]);

  useEffect(() => { queueMicrotask(() => void load()); }, [load]);
  useEffect(() => {
    const params = new URLSearchParams();
    if (q.trim()) params.set("q", q.trim());
    if (protocol) params.set("protocol", protocol);
    if (enabled !== "all") params.set("enabled", enabled);
    if (anonymousAllowed !== "all") params.set("anonymousAllowed", anonymousAllowed);
    if (page > 0) params.set("page", String(page));
    router.replace(`/admin/models${params.toString() ? `?${params.toString()}` : ""}`, { scroll: false });
  }, [anonymousAllowed, enabled, page, protocol, q, router]);
  useEffect(() => {
    queueMicrotask(() => {
      setSelected(new Set());
      setSelectAllMatching(false);
    });
  }, [filter, page]);

  function toggle(id: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
    if (!selectAllMatching) setSelectAllMatching(false);
  }

  const pageSelected = models.length > 0 && models.every((model) => selected.has(model.id));
  const selectionCount = selectAllMatching ? totalElements - selected.size : selected.size;

  function selection(): AdminModelSelection {
    return selectAllMatching
      ? { mode: "FILTER", filter, excludeIds: [...selected] }
      : { mode: "IDS", ids: [...selected] };
  }

  async function runAction(action: AdminModelBulkAction, retryIds?: string[]) {
    const chosen = retryIds ? { mode: "IDS" as const, ids: retryIds } : selection();
    const count = retryIds?.length ?? selectionCount;
    if (count <= 0) return;
    const destructive = action === "DELETE";
    if (!(await confirmDialog({
      title: destructive ? "Delete selected models?" : `${action.replaceAll("_", " ")} selected models?`,
      message: destructive
        ? `This removes ${count} configured model(s). Historical responses remain readable, but the models cannot be restored from this page.`
        : `This changes only the ${action.replaceAll("_", " ").toLowerCase()} state for ${count} configured model(s).`,
      confirmLabel: destructive ? "Delete models" : "Continue",
      danger: destructive,
    }))) return;

    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const preview = await previewAdminModelBulk(token, action, chosen);
      const approved = await confirmDialog({
        title: "Confirm frozen selection",
        message: `${preview.targetCount} model(s) are in the frozen preview. Already-satisfied: ${preview.summary.alreadySatisfied ?? 0}. Execute now?`,
        confirmLabel: "Execute",
        danger: destructive,
      });
      if (!approved) return;
      const execution = await executeAdminModelBulk(token, preview.operationId, crypto.randomUUID());
      setResult(execution);
      setNotice(`${execution.changedCount} changed, ${execution.alreadySatisfiedCount} already satisfied, ${execution.failedCount} failed.`);
      await load();
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : "Bulk operation failed.");
    } finally {
      setBusy(false);
    }
  }

  const failedIds = result?.items.filter((item) => item.outcome === "FAILED").map((item) => item.configuredModelId) ?? [];

  return (
    <AdminShell title="Model access" description="Search built-in models and safely manage anonymous access and display state in bulk.">
      {error ? <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div> : null}
      {notice ? <div className="mb-4 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{notice}</div> : null}
      <section className="mb-4 rounded-2xl border border-stone-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_180px_160px_180px]">
          <label className="relative block"><Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-stone-400" /><input value={q} onChange={(event) => { setQ(event.target.value); setPage(0); }} placeholder="Search model, ID, connection" className={`${inputClass} w-full pl-9`} /></label>
          <input value={protocol} onChange={(event) => { setProtocol(event.target.value); setPage(0); }} placeholder="Protocol" className={inputClass} />
          <select value={enabled} onChange={(event) => { setEnabled(event.target.value); setPage(0); }} className={inputClass}><option value="all">All display states</option><option value="true">Displayed</option><option value="false">Hidden</option></select>
          <select value={anonymousAllowed} onChange={(event) => { setAnonymousAllowed(event.target.value); setPage(0); }} className={inputClass}><option value="all">All anonymous states</option><option value="true">Anonymous allowed</option><option value="false">Anonymous blocked</option></select>
        </div>
      </section>

      <section className="rounded-2xl border border-stone-200 bg-white shadow-sm">
        <div className="flex flex-wrap items-center gap-2 border-b border-stone-200 px-4 py-3">
          <label className="inline-flex items-center gap-2 text-sm text-stone-700"><input type="checkbox" checked={pageSelected} onChange={(event) => {
            setSelected(event.target.checked ? new Set(models.map((model) => model.id)) : new Set());
            setSelectAllMatching(false);
          }} className="accent-[#c96442]" /> Select page</label>
          {pageSelected && totalElements > models.length && !selectAllMatching ? <button type="button" onClick={() => { setSelected(new Set()); setSelectAllMatching(true); }} className="text-xs font-medium text-[#a04a32] hover:underline">Select all {totalElements} matching models</button> : null}
          <span className="ml-auto text-xs text-stone-500">{selectionCount} selected</span>
          <button type="button" disabled={busy || selectionCount === 0} onClick={() => void runAction("ALLOW_ANONYMOUS")} className="rounded-lg bg-[#c96442] px-2.5 py-1.5 text-xs font-medium text-white disabled:opacity-40">Allow anonymous</button>
          <button type="button" disabled={busy || selectionCount === 0} onClick={() => void runAction("REVOKE_ANONYMOUS")} className="rounded-lg border border-stone-300 px-2.5 py-1.5 text-xs font-medium text-stone-700 disabled:opacity-40">Revoke</button>
          <button type="button" disabled={busy || selectionCount === 0} onClick={() => void runAction("SHOW")} title="Show selected models" className="rounded-lg border border-stone-300 p-1.5 text-stone-600 disabled:opacity-40"><Eye className="h-4 w-4" /></button>
          <button type="button" disabled={busy || selectionCount === 0} onClick={() => void runAction("HIDE")} title="Hide selected models" className="rounded-lg border border-stone-300 p-1.5 text-stone-600 disabled:opacity-40"><EyeOff className="h-4 w-4" /></button>
          <button type="button" disabled={busy || selectionCount === 0} onClick={() => void runAction("DELETE")} title="Delete selected models" className="rounded-lg border border-red-200 p-1.5 text-red-600 disabled:opacity-40"><Trash2 className="h-4 w-4" /></button>
        </div>
        {selectAllMatching ? <div className="border-b border-blue-100 bg-blue-50 px-4 py-2 text-xs text-blue-800">All matching models are selected. Uncheck rows to exclude them from this operation.</div> : null}
        {loading ? <div className="p-8 text-center text-sm text-stone-500">Loading models…</div> : models.length === 0 ? <div className="p-12 text-center text-sm text-stone-500">No built-in models match these filters.</div> : (
          <div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead className="bg-stone-50 text-xs uppercase tracking-wide text-stone-500"><tr><th className="w-10 px-4 py-3" /><th className="px-4 py-3">Model</th><th className="px-4 py-3">Connection</th><th className="px-4 py-3">Capabilities</th><th className="px-4 py-3">State</th></tr></thead><tbody className="divide-y divide-stone-100">{models.map((model) => <tr key={model.id} className="hover:bg-stone-50/70"><td className="px-4 py-3"><input type="checkbox" checked={selected.has(model.id)} onChange={() => toggle(model.id)} className="accent-[#c96442]" aria-label={`Select ${model.displayName}`} /></td><td className="px-4 py-3"><p className="font-medium text-stone-800">{model.displayName}</p><p className="text-xs text-stone-500">{model.modelId}</p></td><td className="px-4 py-3 text-stone-600">{model.connection.label || "Unlabelled"}<p className="text-xs text-stone-400">{model.protocol}</p></td><td className="px-4 py-3"><div className="flex flex-wrap gap-1">{model.capabilities.streaming ? <Badge>streaming</Badge> : null}{model.capabilities.vision ? <Badge>vision</Badge> : null}{model.capabilities.tools ? <Badge>tools</Badge> : null}</div></td><td className="px-4 py-3"><div className="flex flex-wrap gap-1"><Badge tone={model.isEnabled ? "green" : "gray"}>{model.isEnabled ? "displayed" : "hidden"}</Badge><Badge tone={model.isAnonymousAllowed ? "orange" : "gray"}>{model.isAnonymousAllowed ? "anonymous" : "private"}</Badge></div></td></tr>)}</tbody></table></div>
        )}
        <div className="flex items-center justify-between border-t border-stone-200 px-4 py-3 text-xs text-stone-500"><span>{totalElements} built-in models · page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span><div className="flex gap-1"><button type="button" disabled={page === 0 || loading} onClick={() => setPage((current) => current - 1)} className="rounded-lg border border-stone-200 p-1.5 disabled:opacity-40"><ChevronLeft className="h-4 w-4" /></button><button type="button" disabled={page + 1 >= totalPages || loading} onClick={() => setPage((current) => current + 1)} className="rounded-lg border border-stone-200 p-1.5 disabled:opacity-40"><ChevronRight className="h-4 w-4" /></button></div></div>
      </section>
      {result && failedIds.length > 0 ? <button type="button" disabled={busy} onClick={() => void runAction(result.action, failedIds)} className="mt-4 inline-flex items-center rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-medium text-amber-800 disabled:opacity-40"><Check className="mr-1.5 h-4 w-4" /> Retry {failedIds.length} failed items</button> : null}
    </AdminShell>
  );
}

function Badge({ children, tone = "gray" }: { children: React.ReactNode; tone?: "gray" | "green" | "orange" }) {
  const styles = { gray: "bg-stone-100 text-stone-600", green: "bg-green-50 text-green-700", orange: "bg-orange-50 text-orange-700" };
  return <span className={`rounded-full px-2 py-0.5 text-[11px] ${styles[tone]}`}>{children}</span>;
}
