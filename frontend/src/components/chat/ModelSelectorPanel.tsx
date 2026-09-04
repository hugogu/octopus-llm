"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, SlidersHorizontal, Wrench } from "lucide-react";
import type { CapabilityMatrix } from "@/lib/types/api";

export interface ModelSelectorModel {
  id: string;
  displayName: string;
  isEnabled: boolean;
  builtin: boolean;
  connectionLabel: string | null;
  protocol: string;
  capabilityMatrix: CapabilityMatrix;
}

interface ModelSelectorPanelProps {
  models: ModelSelectorModel[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
  manageHref?: string | null;
  emptyMessage?: string;
}

export default function ModelSelectorPanel({
  models,
  selectedIds,
  onChange,
  manageHref = "/settings/models",
  emptyMessage = "No enabled models. Configure one in Settings.",
}: ModelSelectorPanelProps) {
  // Closed by default. (selectedIds loads async from storage, so keying the initial state off it
  // would open the panel on every refresh before the saved selection arrives.)
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const enabledModels = useMemo(() => {
    return models.filter((model) => model.isEnabled);
  }, [models]);

  const selectedModels = useMemo(() => {
    const selectedSet = new Set(selectedIds);
    return enabledModels.filter((model) => selectedSet.has(model.id));
  }, [enabledModels, selectedIds]);

  // Group by provider (connection label, falling back to protocol) and sort both the groups and the
  // models within each group by name, so a long model list stays easy to scan.
  const groupedModels = useMemo(() => {
    const groups = new Map<string, ModelSelectorModel[]>();
    for (const model of enabledModels) {
      const key = model.connectionLabel ?? model.protocol;
      const bucket = groups.get(key);
      if (bucket) bucket.push(model);
      else groups.set(key, [model]);
    }
    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([label, items]) =>
        [label, [...items].sort((x, y) => x.displayName.localeCompare(y.displayName))] as const,
      );
  }, [enabledModels]);

  useEffect(() => {
    if (!open) return;
    function handlePointerDown(event: PointerEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [open]);

  function toggle(id: string) {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter((x) => x !== id));
    } else {
      onChange([...selectedIds, id]);
    }
  }

  const summary =
    selectedModels.length === 0
      ? "Select models"
      : selectedModels.length <= 2
        ? selectedModels.map((model) => model.displayName).join(", ")
        : `${selectedModels.length} models selected`;

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className="flex max-w-full items-center gap-1.5 whitespace-nowrap rounded-lg border border-stone-200 bg-white px-2.5 py-1.5 text-xs font-medium text-stone-600 transition hover:text-stone-900 sm:gap-2 sm:px-3"
      >
        <SlidersHorizontal className="h-3.5 w-3.5 text-stone-400" />
        <span className="max-w-[32vw] truncate sm:max-w-[180px]">{summary}</span>
        <span className="rounded-full bg-stone-100 px-1.5 py-0.5 text-[10px] font-medium tracking-normal text-stone-500">
          {selectedModels.length}/{enabledModels.length || 0}
        </span>
        <ChevronDown className={`h-3.5 w-3.5 text-stone-400 transition-transform ${open ? "rotate-180" : ""}`} />
      </button>

      {open ? (
        <div className="absolute right-0 z-30 mt-2 w-[22rem] max-w-[calc(100vw-2rem)] rounded-2xl border border-stone-200 bg-white p-3 shadow-lg">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-[0.18em] text-stone-500">Models</span>
            {manageHref ? (
              <a href={manageHref} className="text-xs font-medium text-[#b75536] transition hover:text-[#c96442]">
                Manage models
              </a>
            ) : null}
          </div>

          {enabledModels.length === 0 ? (
            <p className="py-3 text-sm text-stone-500">{emptyMessage}</p>
          ) : (
            <div className="flex max-h-72 flex-col gap-3 overflow-y-auto pr-1">
              {groupedModels.map(([label, items]) => (
                <div key={label}>
                  <p className="mb-1 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wide text-stone-400">
                    {label}
                    <span className="rounded-full bg-stone-100 px-1.5 py-0.5 text-[9px] font-medium text-stone-500">
                      {items.length}
                    </span>
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {items.map((model) => {
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
                          <span
                            className={`mr-1.5 rounded px-1 py-0.5 text-[9px] font-semibold uppercase tracking-wide ${
                              selected
                                ? "bg-white/20 text-white"
                                : model.builtin
                                  ? "bg-sky-100 text-sky-700"
                                  : "bg-amber-100 text-amber-700"
                            }`}
                          >
                            {model.builtin ? "Built-in" : "Custom"}
                          </span>
                          <span className="font-medium">{model.displayName}</span>
                          {model.capabilityMatrix?.supports_function_calling ? (
                            <Wrench
                              className={`ml-1 inline h-3 w-3 align-[-1px] ${selected ? "text-orange-100" : "text-stone-400"}`}
                              aria-label="支持工具调用 / 联网搜索"
                            />
                          ) : null}
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}
