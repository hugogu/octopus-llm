"use client";

import { useCallback, useEffect, useState } from "react";
import { Cable, DownloadCloud, Plus, Search, ShieldCheck, ShieldOff, Sparkles, Trash2, UserMinus, UserPlus } from "lucide-react";
import Button from "@/components/ui/Button";
import AdminShell from "@/components/admin/AdminShell";
import { buildCapabilityOverrides, togglesFromOverrides } from "@/components/settings/connections/formUtils";
import { getToken } from "@/lib/api/auth";
import {
  addBuiltinModel,
  allocateConnection,
  createBuiltinConnection,
  deleteBuiltinConnection,
  deleteBuiltinModel,
  detectBuiltinCapabilities,
  listAllocations,
  listBuiltinConnections,
  listBuiltinModels,
  listUsers,
  loadBuiltinEndpointModels,
  patchBuiltinModel,
  revokeConnection,
} from "@/lib/api/admin";
import { confirmDialog } from "@/lib/ui/confirm";
import type { AdminUser, BuiltinConnection, BuiltinModel, ConnectionAllocationView } from "@/lib/types/api";

const MODALITY_KEYS = ["image", "video", "audio"] as const;

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
    setError(
      status === 422
        ? "The user must be activated before allocation."
        : status === 502 || status === 504
          ? "Could not reach the provider endpoint to load models."
          : "Action failed.",
    );
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
      description="Create platform-owned connections with administrator-supplied keys, load their models from the provider, then allocate them read-only to activated users. Keys are encrypted and never returned."
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
          <div className="h-40 animate-pulse rounded-2xl bg-white/70" />
          <div className="h-40 animate-pulse rounded-2xl bg-white/70" />
        </div>
      ) : connections.length === 0 ? (
        <section className="rounded-3xl border border-dashed border-stone-300 bg-white/70 px-8 py-16 text-center">
          <h2 className="text-lg font-semibold text-stone-900">No built-in connections yet</h2>
          <p className="mx-auto mt-2 max-w-lg text-sm text-stone-500">
            Create one above, load or add its models, then allocate it to activated users.
          </p>
        </section>
      ) : (
        <div className="space-y-4">
          {connections.map((c) => (
            <ConnectionCard
              key={c.id}
              connection={c}
              token={token}
              onCountsChanged={load}
              onError={reportError}
              onNotice={setNotice}
            />
          ))}
        </div>
      )}
    </AdminShell>
  );
}

function ConnectionCard({
  connection,
  token,
  onCountsChanged,
  onError,
  onNotice,
}: {
  connection: BuiltinConnection;
  token: string;
  onCountsChanged: () => Promise<void>;
  onError: (e: unknown) => void;
  onNotice: (msg: string) => void;
}) {
  const [models, setModels] = useState<BuiltinModel[]>([]);
  const [allocations, setAllocations] = useState<ConnectionAllocationView[]>([]);
  const [modelId, setModelId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState(false);
  const [loadingModels, setLoadingModels] = useState(false);

  const [userQuery, setUserQuery] = useState("");
  const [userResults, setUserResults] = useState<AdminUser[]>([]);
  const [searching, setSearching] = useState(false);

  const refresh = useCallback(async () => {
    const [modelPage, allocationPage] = await Promise.all([
      listBuiltinModels(token, connection.id, 0, 100),
      listAllocations(token, connection.id, 0, 100),
    ]);
    setModels(modelPage.items);
    setAllocations(allocationPage.items);
  }, [token, connection.id]);

  useEffect(() => {
    queueMicrotask(() => {
      void refresh().catch(() => {});
    });
  }, [refresh]);

  async function act(action: () => Promise<unknown>) {
    setBusy(true);
    try {
      await action();
      await refresh();
      await onCountsChanged();
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }

  async function loadModels() {
    setLoadingModels(true);
    try {
      const { items } = await loadBuiltinEndpointModels(token, connection.id);
      const existing = new Set(models.map((m) => m.modelId));
      const missing = items.filter((id) => !existing.has(id));
      if (missing.length === 0) {
        onNotice(items.length === 0 ? "The endpoint returned no models." : "All endpoint models are already configured.");
        return;
      }
      if (!(await confirmDialog({
        title: `Add ${missing.length} model(s)?`,
        message: "These models discovered from the endpoint will be added.",
        confirmLabel: "Add",
      }))) return;
      await Promise.allSettled(missing.map((id) => addBuiltinModel(token, connection.id, { modelId: id, displayName: id })));
      onNotice(`Added ${missing.length} model(s) from the endpoint.`);
      await refresh();
      await onCountsChanged();
    } catch (e) {
      onError(e);
    } finally {
      setLoadingModels(false);
    }
  }

  async function searchUsers(e: React.FormEvent) {
    e.preventDefault();
    if (userQuery.trim().length === 0) return;
    setSearching(true);
    try {
      const page = await listUsers(token, userQuery.trim(), 0, 8);
      const allocatedIds = new Set(allocations.map((a) => a.userId));
      setUserResults(page.items.filter((u) => !u.isAdmin && !allocatedIds.has(u.id)));
    } catch (e) {
      onError(e);
    } finally {
      setSearching(false);
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
            {connection.hasKey ? "Encrypted key stored" : "No key stored"} · {models.length} model(s) · {allocations.length} user(s)
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <Button size="sm" variant="secondary" isLoading={loadingModels} onClick={() => void loadModels()}>
            <DownloadCloud className="mr-1 h-4 w-4" /> Load models
          </Button>
          <Button
            size="sm"
            variant="secondary"
            isLoading={busy}
            title="Detect image/video/audio capability for this connection's models (OpenRouter + catalogue)"
            onClick={() => void act(() => detectBuiltinCapabilities(token, connection.id))}
          >
            <Sparkles className="mr-1 h-4 w-4" /> Detect
          </Button>
          <Button size="sm" variant="ghost" isLoading={busy} className="text-red-600" onClick={() => void act(() => deleteBuiltinConnection(token, connection.id))}>
            <Trash2 className="mr-1 h-4 w-4" /> Delete
          </Button>
        </div>
      </div>

      {/* Models */}
      <div className="border-b border-stone-100 p-4">
        <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500">Models</h4>
        {models.length > 0 ? (
          <ul className="mb-3 divide-y divide-stone-100">
            {models.map((m) => (
              <li key={m.id} className="flex items-center justify-between gap-2 py-1.5 text-sm">
                <span className="min-w-0">
                  <span className="block truncate">
                    <span className="font-medium text-stone-800">{m.displayName}</span>{" "}
                    <span className="font-mono text-xs text-stone-400">{m.modelId}</span>
                  </span>
                  <span className="text-[11px] text-stone-400">
                    {m.priceCurrency
                      ? `${m.inputPricePerMtok ?? "—"} in / ${m.outputPricePerMtok ?? "—"} out per 1M ${m.priceCurrency}`
                      : "no pricing"}
                  </span>
                </span>
                <div className="flex shrink-0 items-center gap-1">
                  <button
                    type="button"
                    disabled={busy}
                    aria-pressed={m.isAnonymousAllowed}
                    title={m.isAnonymousAllowed ? "Revoke anonymous access" : "Open to anonymous users"}
                    onClick={() => void act(() => patchBuiltinModel(token, connection.id, m.id, { isAnonymousAllowed: !m.isAnonymousAllowed }))}
                    className={`inline-flex items-center rounded-full border px-1.5 py-0.5 text-[10px] transition disabled:cursor-not-allowed disabled:opacity-50 ${
                      m.isAnonymousAllowed
                        ? "border-[#c96442] bg-[#c96442]/10 text-[#b75536]"
                        : "border-stone-200 bg-white text-stone-400 hover:bg-stone-50"
                    }`}
                  >
                    {m.isAnonymousAllowed ? <ShieldCheck className="mr-1 h-3 w-3" /> : <ShieldOff className="mr-1 h-3 w-3" />}
                    {m.isAnonymousAllowed ? "Anonymous on" : "Open anonymous"}
                  </button>
                  {MODALITY_KEYS.map((key) => {
                    const toggles = togglesFromOverrides(m.capabilityOverrides);
                    const on = toggles[key];
                    return (
                      <button
                        key={key}
                        type="button"
                        aria-pressed={on}
                        title={`${on ? "Disable" : "Enable"} ${key} input`}
                        onClick={() =>
                          void act(() =>
                            patchBuiltinModel(token, connection.id, m.id, {
                              capabilityOverrides: buildCapabilityOverrides("", { ...toggles, [key]: !on }, true),
                            }),
                          )
                        }
                        className={`rounded-full border px-1.5 py-0.5 text-[10px] uppercase transition ${
                          on
                            ? "border-[#c96442] bg-[#c96442]/10 text-[#b75536]"
                            : "border-stone-200 bg-white text-stone-400 hover:bg-stone-50"
                        }`}
                      >
                        {key.slice(0, 3)}
                      </button>
                    );
                  })}
                  <button
                    onClick={() => void act(() => deleteBuiltinModel(token, connection.id, m.id))}
                    className="rounded p-1 text-stone-400 hover:bg-red-50 hover:text-red-600"
                    aria-label="Delete model"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mb-3 text-sm text-stone-400">No models yet. Load them from the endpoint, or add a model ID manually.</p>
        )}
        <div className="flex flex-wrap items-center gap-2">
          <input value={modelId} onChange={(e) => setModelId(e.target.value)} placeholder="model id" className={`${inputClass} flex-1`} />
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="display name" className={`${inputClass} flex-1`} />
          <Button
            size="sm"
            variant="secondary"
            isLoading={busy}
            onClick={() =>
              void act(() => addBuiltinModel(token, connection.id, { modelId, displayName: displayName || modelId })).then(() => {
                setModelId("");
                setDisplayName("");
              })
            }
          >
            <Plus className="mr-1 h-3.5 w-3.5" /> Add model
          </Button>
        </div>
      </div>

      {/* Allocations */}
      <div className="p-4">
        <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-stone-500">Allocated users</h4>
        {allocations.length > 0 ? (
          <ul className="mb-3 flex flex-wrap gap-2">
            {allocations.map((a) => (
              <li key={a.userId} className="inline-flex items-center gap-1.5 rounded-full bg-stone-100 py-1 pl-3 pr-1 text-sm text-stone-700">
                {a.email}
                <button
                  onClick={() => void act(() => revokeConnection(token, connection.id, a.userId))}
                  className="rounded-full p-1 text-stone-400 hover:bg-red-100 hover:text-red-600"
                  aria-label={`Revoke ${a.email}`}
                >
                  <UserMinus className="h-3.5 w-3.5" />
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mb-3 text-sm text-stone-400">Not allocated to anyone yet.</p>
        )}

        <form onSubmit={searchUsers} className="flex flex-wrap items-center gap-2">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-stone-400" />
            <input
              value={userQuery}
              onChange={(e) => setUserQuery(e.target.value)}
              placeholder="Search users by email to allocate"
              className={`${inputClass} w-full pl-9`}
            />
          </div>
          <Button type="submit" size="sm" variant="secondary" isLoading={searching}>
            Find users
          </Button>
        </form>

        {userResults.length > 0 ? (
          <ul className="mt-2 divide-y divide-stone-100 rounded-lg border border-stone-200">
            {userResults.map((u) => (
              <li key={u.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
                <span className="flex min-w-0 items-center gap-2">
                  <span className="truncate text-stone-800">{u.email}</span>
                  {!u.isActive ? (
                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-700">Not activated</span>
                  ) : null}
                </span>
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={!u.isActive}
                  isLoading={busy}
                  onClick={() =>
                    void act(() => allocateConnection(token, connection.id, u.id)).then(() => {
                      setUserResults((rs) => rs.filter((r) => r.id !== u.id));
                    })
                  }
                >
                  <UserPlus className="mr-1 h-3.5 w-3.5" /> Allocate
                </Button>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </article>
  );
}
