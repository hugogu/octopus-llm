"use client";

import { Cable, Pencil, Plus, Trash2 } from "lucide-react";
import Button from "@/components/ui/Button";
import { getToken } from "@/lib/api/auth";
import { deleteConnection } from "@/lib/api/connections";
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
          <p className="mt-1 text-xs text-stone-400">Encrypted key stored · {models.length} model(s)</p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
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
