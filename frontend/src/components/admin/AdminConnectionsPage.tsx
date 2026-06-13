"use client";

import { useCallback, useEffect, useState } from "react";
import { Cable, Plus, Trash2, UserPlus, UserMinus } from "lucide-react";
import Button from "@/components/ui/Button";
import AdminShell from "@/components/admin/AdminShell";
import { getToken } from "@/lib/api/auth";
import {
  addBuiltinModel,
  allocateConnection,
  createBuiltinConnection,
  deleteBuiltinConnection,
  listBuiltinConnections,
  revokeConnection,
} from "@/lib/api/admin";
import type { BuiltinConnection } from "@/lib/types/api";

const inputClass =
  "rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 shadow-sm focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";

export default function AdminConnectionsPage() {
  const token = getToken() ?? "";
  const [connections, setConnections] = useState<BuiltinConnection[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);

  const [protocol, setProtocol] = useState("openai-compatible");
  const [baseUrl, setBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [label, setLabel] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listBuiltinConnections(token, 0, 100);
      setConnections(page.items);
      setError(null);
    } catch {
      setError("Failed to load built-in connections.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  function reportError(e: unknown) {
    const status = (e as { status?: number }).status;
    setError(status === 422 ? "The user must be activated before allocation." : "Action failed.");
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setNotice(null);
    setCreating(true);
    try {
      await createBuiltinConnection(token, { protocol, baseUrl, apiKey, label: label || undefined });
      setNotice("Built-in connection created.");
      setBaseUrl("");
      setApiKey("");
      setLabel("");
      await load();
    } catch (e) {
      reportError(e);
    } finally {
      setCreating(false);
    }
  }

  return (
    <AdminShell
      title="Built-in connections"
      description="Create platform-owned connections with administrator-supplied keys, then allocate them read-only to activated users. Keys are encrypted and never returned."
    >
      {error ? (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      ) : null}
      {notice ? (
        <div className="mb-4 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{notice}</div>
      ) : null}

      <section className="mb-6 rounded-2xl border border-stone-200 bg-white p-5 shadow-sm">
        <h2 className="mb-3 text-sm font-semibold text-stone-900">New built-in connection</h2>
        <form onSubmit={handleCreate} className="grid gap-3 sm:grid-cols-2">
          <input value={protocol} onChange={(e) => setProtocol(e.target.value)} placeholder="Protocol (e.g. openai-compatible)" className={inputClass} required />
          <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Label (optional)" className={inputClass} />
          <input value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} placeholder="https://api.example.com" className={inputClass} required />
          <input value={apiKey} onChange={(e) => setApiKey(e.target.value)} placeholder="API key" type="password" className={inputClass} required />
          <div className="sm:col-span-2">
            <Button type="submit" isLoading={creating} className="!bg-[#c96442] hover:!bg-[#b55538]">
              <Plus className="mr-1.5 h-4 w-4" /> Create connection
            </Button>
          </div>
        </form>
      </section>

      {loading ? (
        <div className="space-y-4">
          <div className="h-32 animate-pulse rounded-2xl bg-white/70" />
          <div className="h-32 animate-pulse rounded-2xl bg-white/70" />
        </div>
      ) : connections.length === 0 ? (
        <section className="rounded-3xl border border-dashed border-stone-300 bg-white/70 px-8 py-16 text-center">
          <h2 className="text-lg font-semibold text-stone-900">No built-in connections yet</h2>
          <p className="mx-auto mt-2 max-w-lg text-sm text-stone-500">
            Create one above, add its model IDs, then allocate it to activated users.
          </p>
        </section>
      ) : (
        <div className="space-y-4">
          {connections.map((c) => (
            <ConnectionCard key={c.id} connection={c} token={token} onChange={load} onError={reportError} />
          ))}
        </div>
      )}
    </AdminShell>
  );
}

function ConnectionCard({
  connection,
  token,
  onChange,
  onError,
}: {
  connection: BuiltinConnection;
  token: string;
  onChange: () => Promise<void>;
  onError: (e: unknown) => void;
}) {
  const [modelId, setModelId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [userId, setUserId] = useState("");
  const [busy, setBusy] = useState(false);

  async function act(action: () => Promise<unknown>) {
    setBusy(true);
    try {
      await action();
      await onChange();
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b border-stone-200 bg-stone-50/70 p-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <Cable className="h-4 w-4 text-[#c96442]" />
            <h3 className="truncate font-semibold text-stone-900">{connection.label ?? "Unnamed connection"}</h3>
            <span className="rounded-full bg-stone-200 px-2 py-0.5 text-[11px] text-stone-600">{connection.protocol}</span>
            <span className="rounded-full bg-[#c96442]/10 px-2 py-0.5 text-[11px] text-[#b75536]">Built-in</span>
          </div>
          <p className="mt-1 truncate font-mono text-xs text-stone-500">{connection.baseUrl}</p>
          <p className="mt-1 text-xs text-stone-400">
            {connection.hasKey ? "Encrypted key stored" : "No key stored"} · {connection.modelCount} model(s) ·{" "}
            {connection.allocatedUserCount} user(s)
          </p>
        </div>
        <Button size="sm" variant="ghost" isLoading={busy} className="text-red-600" onClick={() => void act(() => deleteBuiltinConnection(token, connection.id))}>
          <Trash2 className="mr-1 h-4 w-4" /> Delete
        </Button>
      </div>

      <div className="space-y-3 p-4">
        <div className="flex flex-wrap items-center gap-2">
          <input value={modelId} onChange={(e) => setModelId(e.target.value)} placeholder="model id" className={`${inputClass} flex-1`} />
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="display name" className={`${inputClass} flex-1`} />
          <Button
            size="sm"
            variant="secondary"
            isLoading={busy}
            onClick={() =>
              void act(() => addBuiltinModel(token, connection.id, { modelId, displayName })).then(() => {
                setModelId("");
                setDisplayName("");
              })
            }
          >
            <Plus className="mr-1 h-3.5 w-3.5" /> Add model
          </Button>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <input value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="user id to allocate" className={`${inputClass} flex-1`} />
          <Button size="sm" variant="secondary" isLoading={busy} onClick={() => void act(() => allocateConnection(token, connection.id, userId))}>
            <UserPlus className="mr-1 h-3.5 w-3.5" /> Allocate
          </Button>
          <Button size="sm" variant="ghost" isLoading={busy} className="text-red-600" onClick={() => void act(() => revokeConnection(token, connection.id, userId))}>
            <UserMinus className="mr-1 h-3.5 w-3.5" /> Revoke
          </Button>
        </div>
      </div>
    </article>
  );
}
