"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, Plus, Sparkles } from "lucide-react";
import Link from "next/link";
import Button from "@/components/ui/Button";
import AddConnectionDialog from "@/components/settings/connections/AddConnectionDialog";
import AddModelDialog from "@/components/settings/connections/AddModelDialog";
import ConnectionCard from "@/components/settings/connections/ConnectionCard";
import EditConnectionDialog from "@/components/settings/connections/EditConnectionDialog";
import EditModelDialog from "@/components/settings/connections/EditModelDialog";
import { getToken } from "@/lib/api/auth";
import {
  listConfiguredModels,
  listConnections,
  listProtocols,
  refreshModelCapabilities,
} from "@/lib/api/connections";
import type {
  ConfiguredModelV2,
  ConnectionV2,
  ProtocolDefinitionV2,
} from "@/lib/types/api";

export default function ModelsSettingsPage() {
  const [protocols, setProtocols] = useState<ProtocolDefinitionV2[]>([]);
  const [connections, setConnections] = useState<ConnectionV2[]>([]);
  const [models, setModels] = useState<ConfiguredModelV2[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [addingConnection, setAddingConnection] = useState(false);
  const [editingConnection, setEditingConnection] = useState<ConnectionV2 | null>(null);
  const [addingModelTo, setAddingModelTo] = useState<ConnectionV2 | null>(null);
  const [editingModel, setEditingModel] = useState<ConfiguredModelV2 | null>(null);
  const [detecting, setDetecting] = useState(false);
  const [detectNotice, setDetectNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    const token = getToken();
    if (!token) {
      setLoading(false);
      setError("Not authenticated");
      return;
    }
    setLoading(true);
    try {
      const [protocolPage, connectionPage, modelPage] = await Promise.all([
        listProtocols(),
        listConnections(token),
        listConfiguredModels(token),
      ]);
      setProtocols(protocolPage.items);
      setConnections(connectionPage.items);
      setModels(modelPage.items);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Failed to load model settings");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  const detectCapabilities = useCallback(async () => {
    const token = getToken();
    if (!token) return;
    setDetecting(true);
    setDetectNotice(null);
    try {
      const result = await refreshModelCapabilities(token);
      setDetectNotice(
        result.updatedCount === 0
          ? "No new capabilities detected — known models are already set up."
          : `Detected media capabilities for ${result.updatedCount} model(s).`,
      );
      await load();
    } catch (cause) {
      setDetectNotice(cause instanceof Error ? cause.message : "Capability detection failed");
    } finally {
      setDetecting(false);
    }
  }, [load]);

  const modelsByConnection = useMemo(() => {
    const grouped = new Map<string, ConfiguredModelV2[]>();
    for (const model of models) {
      const group = grouped.get(model.connectionId) ?? [];
      group.push(model);
      grouped.set(model.connectionId, group);
    }
    for (const group of grouped.values()) group.sort((a, b) => a.sortOrder - b.sortOrder);
    return grouped;
  }, [models]);

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,_#f8e9dc,_transparent_30%),linear-gradient(180deg,#faf9f5,#f2f0e8)] px-4 py-8 sm:px-6">
      <div className="mx-auto max-w-5xl">
        <header className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#b75536]">Connections</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight text-stone-900">Model settings</h1>
            <p className="mt-2 max-w-2xl text-sm text-stone-600">
              Connect protocol-compatible endpoints, then add the exact model IDs you want to use. Catalogue entries are suggestions only.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Link href="/chat" className="inline-flex items-center rounded-lg px-3 py-2 text-sm font-medium text-stone-600 hover:bg-white">
              <ArrowLeft className="mr-1.5 h-4 w-4" /> Back to chat
            </Link>
            <Button
              variant="secondary"
              onClick={() => void detectCapabilities()}
              isLoading={detecting}
              title="Auto-detect image/video/audio capability for known models from the catalogue"
            >
              <Sparkles className="mr-1.5 h-4 w-4" /> Detect capabilities
            </Button>
            <Button onClick={() => setAddingConnection(true)} className="!bg-[#c96442] hover:!bg-[#b55538]">
              <Plus className="mr-1.5 h-4 w-4" /> Add connection
            </Button>
          </div>
        </header>

        {detectNotice && (
          <div className="mb-4 rounded-xl border border-stone-200 bg-white px-3 py-2 text-sm text-stone-600 shadow-sm">
            {detectNotice}
          </div>
        )}

        {error ? <div className="mb-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div> : null}
        {loading ? (
          <div className="space-y-4">
            <div className="h-44 animate-pulse rounded-2xl bg-white/70" />
            <div className="h-44 animate-pulse rounded-2xl bg-white/70" />
          </div>
        ) : connections.length === 0 ? (
          <section className="rounded-3xl border border-dashed border-stone-300 bg-white/70 px-8 py-16 text-center">
            <h2 className="text-lg font-semibold text-stone-900">No connections yet</h2>
            <p className="mx-auto mt-2 max-w-lg text-sm text-stone-500">
              Add an endpoint and encrypted API key. Models are configured explicitly after the connection exists.
            </p>
            <Button onClick={() => setAddingConnection(true)} className="mt-5 !bg-[#c96442] hover:!bg-[#b55538]">
              <Plus className="mr-1.5 h-4 w-4" /> Add first connection
            </Button>
          </section>
        ) : (
          <div className="space-y-4">
            {connections.map((connection) => (
              <ConnectionCard
                key={connection.id}
                connection={connection}
                models={modelsByConnection.get(connection.id) ?? []}
                onAddModel={setAddingModelTo}
                onEditConnection={setEditingConnection}
                onEditModel={setEditingModel}
                onChanged={() => void load()}
              />
            ))}
          </div>
        )}
      </div>

      <AddConnectionDialog open={addingConnection} protocols={protocols} onClose={() => setAddingConnection(false)} onSaved={() => void load()} />
      {editingConnection ? (
        <EditConnectionDialog
          key={editingConnection.id}
          connection={editingConnection}
          onClose={() => setEditingConnection(null)}
          onSaved={() => void load()}
        />
      ) : null}
      {addingModelTo ? (
        <AddModelDialog
          key={addingModelTo.id}
          connection={addingModelTo}
          onClose={() => setAddingModelTo(null)}
          onSaved={() => void load()}
        />
      ) : null}
      {editingModel ? (
        <EditModelDialog
          key={editingModel.id}
          model={editingModel}
          onClose={() => setEditingModel(null)}
          onSaved={() => void load()}
        />
      ) : null}
    </main>
  );
}
