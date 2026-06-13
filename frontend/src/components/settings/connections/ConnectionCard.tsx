"use client";

import { useState } from "react";
import { Cable, DownloadCloud, Pencil, Plus, Trash2 } from "lucide-react";
import Button from "@/components/ui/Button";
import { getToken } from "@/lib/api/auth";
import {
  addConfiguredModel,
  deleteConnection,
  listConnectionEndpointModels,
} from "@/lib/api/connections";
import type { ConfiguredModelV2, ConnectionV2 } from "@/lib/types/api";
import ModelRow from "./ModelRow";

interface Props {
  connection: ConnectionV2;
  models: ConfiguredModelV2[];
  onAddModel: (connection: ConnectionV2) => void;
  onEditConnection: (connection: ConnectionV2) => void;
  onEditModel: (model: ConfiguredModelV2) => void;
  onChanged: () => void;
}

export default function ConnectionCard({
  connection,
  models,
  onAddModel,
  onEditConnection,
  onEditModel,
  onChanged,
}: Props) {
  const [loadingModels, setLoadingModels] = useState(false);
  const [loadNotice, setLoadNotice] = useState<string | null>(null);

  const loadModels = async () => {
    const token = getToken();
    if (!token) return;
    setLoadingModels(true);
    setLoadNotice(null);
    try {
      const { items } = await listConnectionEndpointModels(token, connection.id);
      const existing = new Set(models.map((model) => model.modelId));
      const missing = items.filter((id) => !existing.has(id));
      if (missing.length === 0) {
        setLoadNotice(
          items.length === 0
            ? "The endpoint returned no models."
            : "All models from the endpoint are already configured.",
        );
        return;
      }
      if (!confirm(`Add ${missing.length} model(s) from this endpoint?`)) return;
      const results = await Promise.allSettled(
        missing.map((id) =>
          addConfiguredModel(token, { connectionId: connection.id, modelId: id, displayName: id }),
        ),
      );
      const failed = results.filter((result) => result.status === "rejected").length;
      setLoadNotice(
        failed === 0
          ? `Added ${missing.length} model(s).`
          : `Added ${missing.length - failed} model(s); ${failed} failed.`,
      );
      onChanged();
    } catch (cause) {
      setLoadNotice(
        cause instanceof Error ? cause.message : "Failed to load models from the endpoint",
      );
    } finally {
      setLoadingModels(false);
    }
  };

  const remove = async () => {
    if (!confirm(`Delete ${connection.label ?? connection.baseUrl} and its configured models?`)) return;
    const token = getToken();
    if (!token) return;
    await deleteConnection(token, connection.id);
    onChanged();
  };

  return (
    <article className="overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm">
      <div className="flex flex-col gap-4 border-b border-stone-200 bg-stone-50/70 p-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <Cable className="h-4 w-4 text-[#c96442]" />
            <h2 className="truncate font-semibold text-stone-900">{connection.label ?? "Unnamed connection"}</h2>
            <span className="rounded-full bg-stone-200 px-2 py-0.5 text-[11px] text-stone-600">{connection.protocol}</span>
          </div>
          <p className="mt-1 truncate font-mono text-xs text-stone-500">{connection.baseUrl}</p>
          <p className="mt-1 text-xs text-stone-400">
            {connection.hasKey ? "Encrypted key stored" : "No key stored"} · {models.length} model(s)
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <Button size="sm" variant="secondary" isLoading={loadingModels} onClick={() => void loadModels()}>
            <DownloadCloud className="mr-1.5 h-4 w-4" /> Load models
          </Button>
          <Button size="sm" variant="secondary" onClick={() => onAddModel(connection)}>
            <Plus className="mr-1.5 h-4 w-4" /> Add model
          </Button>
          <Button size="sm" variant="ghost" onClick={() => onEditConnection(connection)} aria-label="Edit connection">
            <Pencil className="h-4 w-4" />
          </Button>
          <Button size="sm" variant="ghost" onClick={() => void remove()} className="text-red-600" aria-label="Delete connection">
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </div>
      {loadNotice ? (
        <p className="border-b border-stone-200 bg-amber-50 px-4 py-2 text-xs text-amber-700">{loadNotice}</p>
      ) : null}
      {models.length > 0 ? (
        models.map((model) => (
          <ModelRow key={model.id} model={model} onEdit={onEditModel} onChanged={onChanged} />
        ))
      ) : (
        <div className="px-4 py-7 text-center text-sm text-stone-500">
          Add a model ID manually or choose an optional catalogue suggestion.
        </div>
      )}
    </article>
  );
}
