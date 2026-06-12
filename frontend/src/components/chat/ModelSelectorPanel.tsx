"use client";

import { useMemo, useState } from "react";
import type { ConfiguredModelV2 } from "@/lib/types/api";

interface ModelSelectorPanelProps {
  models: ConfiguredModelV2[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
}

export default function ModelSelectorPanel({
  models,
  selectedIds,
  onChange,
}: ModelSelectorPanelProps) {
  const [expanded, setExpanded] = useState(selectedIds.length === 0);

  const enabledModels = useMemo(() => {
    return models.filter((model) => model.isEnabled);
  }, [models]);

  const selectedModels = useMemo(() => {
    const selectedSet = new Set(selectedIds);
    return enabledModels.filter((model) => selectedSet.has(model.id));
  }, [enabledModels, selectedIds]);

  const pickerExpanded = expanded || selectedIds.length === 0;

  function toggle(id: string) {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter((x) => x !== id));
    } else {
      onChange([...selectedIds, id]);
    }
  }

  return (
    <section className="rounded-2xl border border-gray-200 bg-white/90 px-3 py-3 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0 space-y-2">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-gray-500">
            <span>Models</span>
            <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium tracking-normal text-gray-600">
              {selectedModels.length}/{enabledModels.length || 0} selected
            </span>
          </div>

          {selectedModels.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {selectedModels.slice(0, 4).map((model) => (
                <span
                  key={model.id}
                  className="inline-flex items-center rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700"
                >
                  {model.displayName}
                </span>
              ))}
              {selectedModels.length > 4 ? (
                <span className="inline-flex items-center rounded-full bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-600">
                  +{selectedModels.length - 4} more
                </span>
              ) : null}
            </div>
          ) : (
            <p className="text-sm text-gray-500">
              No models selected. Pick at least one model to start chatting.
            </p>
          )}
        </div>

        <div className="flex items-center gap-2">
          <a
            href="/settings/models"
            className="inline-flex items-center rounded-full border border-gray-200 px-3 py-1.5 text-sm text-gray-600 transition hover:border-gray-300 hover:text-gray-900"
          >
            Manage models
          </a>
          <button
            type="button"
            onClick={() => {
              setExpanded((current) => !current);
            }}
            className="inline-flex items-center rounded-full bg-gray-900 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-gray-700"
          >
            {pickerExpanded ? "Hide picker" : "Change models"}
          </button>
        </div>
      </div>

      {pickerExpanded ? (
        <div className="mt-3 border-t border-gray-100 pt-3">
          {enabledModels.length === 0 ? (
            <p className="py-3 text-sm text-gray-500">
              No enabled models. Configure one in Settings.
            </p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {enabledModels.map((model) => {
                const selected = selectedIds.includes(model.id);
                return (
                  <button
                    key={model.id}
                    type="button"
                    aria-pressed={selected}
                    onClick={() => toggle(model.id)}
                    className={`rounded-full border px-3 py-1.5 text-sm transition ${
                      selected
                        ? "border-[#c96442] bg-[#c96442] text-white shadow-sm"
                        : "border-stone-300 bg-white text-stone-700 hover:border-stone-500"
                    }`}
                  >
                    <span className="font-medium">{model.displayName}</span>
                    <span className={`ml-1.5 text-xs ${selected ? "text-orange-100" : "text-stone-400"}`}>
                      {model.connectionLabel ?? model.protocol}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      ) : null}
    </section>
  );
}
