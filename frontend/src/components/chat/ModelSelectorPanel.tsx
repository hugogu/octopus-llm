"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, SlidersHorizontal } from "lucide-react";
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
        className="flex items-center gap-2 rounded-lg border border-stone-200 bg-white px-3 py-1.5 text-xs font-medium text-stone-600 transition hover:text-stone-900"
      >
        <SlidersHorizontal className="h-3.5 w-3.5 text-stone-400" />
        <span className="max-w-[180px] truncate">{summary}</span>
        <span className="rounded-full bg-stone-100 px-1.5 py-0.5 text-[10px] font-medium tracking-normal text-stone-500">
          {selectedModels.length}/{enabledModels.length || 0}
        </span>
        <ChevronDown className={`h-3.5 w-3.5 text-stone-400 transition-transform ${open ? "rotate-180" : ""}`} />
      </button>

      {open ? (
        <div className="absolute right-0 z-30 mt-2 w-[22rem] max-w-[calc(100vw-2rem)] rounded-2xl border border-stone-200 bg-white p-3 shadow-lg">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-[0.18em] text-stone-500">Models</span>
            <a
              href="/settings/models"
              className="text-xs font-medium text-[#b75536] transition hover:text-[#c96442]"
            >
              Manage models
            </a>
          </div>

          {enabledModels.length === 0 ? (
            <p className="py-3 text-sm text-stone-500">No enabled models. Configure one in Settings.</p>
          ) : (
            <div className="flex max-h-72 flex-wrap gap-2 overflow-y-auto pr-1">
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
    </div>
  );
}
