"use client";

import { Pencil, ToggleLeft, ToggleRight, Trash2 } from "lucide-react";
import Button from "@/components/ui/Button";
import { getToken } from "@/lib/api/auth";
import { deleteConfiguredModel, patchConfiguredModel } from "@/lib/api/connections";
import { confirmDialog } from "@/lib/ui/confirm";
import type { ConfiguredModelV2 } from "@/lib/types/api";

interface Props {
  model: ConfiguredModelV2;
  onEdit: (model: ConfiguredModelV2) => void;
  onChanged: () => void;
}

export default function ModelRow({ model, onEdit, onChanged }: Props) {
  const setEnabled = async (enabled: boolean) => {
    const token = getToken();
    if (!token) return;
    await patchConfiguredModel(token, model.id, { isEnabled: enabled });
    onChanged();
  };
  const remove = async () => {
    const confirmed = await confirmDialog({
      title: `Remove ${model.displayName}?`,
      message: "Historical responses will be preserved.",
      confirmLabel: "Remove",
      danger: true,
    });
    if (!confirmed) return;
    const token = getToken();
    if (!token) return;
    await deleteConfiguredModel(token, model.id);
    onChanged();
  };

  return (
    <div className="grid gap-3 border-t border-stone-100 px-4 py-3 first:border-t-0 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate text-sm font-semibold text-stone-900">{model.displayName}</p>
          <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${model.isEnabled ? "bg-emerald-100 text-emerald-700" : "bg-stone-100 text-stone-500"}`}>
            {model.isEnabled ? "Enabled" : "Disabled"}
          </span>
        </div>
        <p className="mt-0.5 truncate font-mono text-xs text-stone-500">{model.modelId}</p>
        <div className="mt-1 flex flex-wrap items-center gap-1.5">
          {(model.capabilityMatrix?.input_modalities ?? [])
            .filter((modality) => modality !== "text")
            .map((modality) => (
              <span
                key={modality}
                className="rounded-full bg-[#c96442]/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-[#b75536]"
              >
                {modality}
              </span>
            ))}
          {model.capabilityMatrix?.supports_function_calling ? (
            <span className="rounded-full bg-sky-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-sky-700">
              tools / search
            </span>
          ) : null}
          <span className="text-xs text-stone-400">
            {model.priceCurrency
              ? `${model.inputPricePerMtok ?? "—"} in / ${model.outputPricePerMtok ?? "—"} out per 1M ${model.priceCurrency}`
              : "Pricing not configured"}
          </span>
        </div>
        {Object.keys(model.customParams).length > 0 ? (
          <p className="mt-1 text-xs text-stone-400">{Object.keys(model.customParams).length} custom parameter(s)</p>
        ) : null}
      </div>
      <div className="flex items-center gap-1">
        <Button
          size="sm"
          variant="ghost"
          onClick={() => void setEnabled(!model.isEnabled)}
          aria-label={model.isEnabled ? `Disable ${model.displayName}` : `Enable ${model.displayName}`}
          title={model.isEnabled ? "Disable" : "Enable"}
        >
          {model.isEnabled ? (
            <ToggleRight className="h-4 w-4 text-emerald-600" />
          ) : (
            <ToggleLeft className="h-4 w-4 text-stone-400" />
          )}
        </Button>
        <Button size="sm" variant="ghost" onClick={() => onEdit(model)} aria-label={`Edit ${model.displayName}`}>
          <Pencil className="h-4 w-4" />
        </Button>
        <Button size="sm" variant="ghost" onClick={() => void remove()} className="text-red-600" aria-label={`Remove ${model.displayName}`}>
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
