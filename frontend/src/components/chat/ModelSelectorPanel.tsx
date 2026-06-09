"use client";

import type { UserModelConfig, ModelDefinition } from "@/lib/types/api";
import CapabilityBadge from "@/components/models/CapabilityBadge";

interface ModelSelectorPanelProps {
  models: ModelDefinition[];
  configs: UserModelConfig[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
}

export default function ModelSelectorPanel({
  models,
  configs,
  selectedIds,
  onChange,
}: ModelSelectorPanelProps) {
  const enabledModelIds = new Set(
    configs.filter((c) => c.isEnabled).map((c) => c.modelId),
  );
  const modelMap = new Map(models.map((m) => [m.id, m]));
  const enabledModels = models.filter((m) => enabledModelIds.has(m.id));

  function toggle(id: string) {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter((x) => x !== id));
    } else {
      onChange([...selectedIds, id]);
    }
  }

  if (enabledModels.length === 0) {
    return (
      <p className="text-sm text-gray-500">
        No models enabled.{" "}
        <a href="/settings/models" className="text-blue-600 underline">
          Configure models
        </a>
      </p>
    );
  }

  return (
    <div className="flex flex-wrap gap-2">
      {enabledModels.map((model) => {
        const selected = selectedIds.includes(model.id);
        const caps = modelMap.get(model.id)?.capabilityMatrix;
        const inputMods = caps?.input_modalities.filter((m) => m !== "text") ?? [];
        return (
          <label
            key={model.id}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full border text-sm cursor-pointer transition-colors ${
              selected
                ? "bg-blue-600 text-white border-blue-600"
                : "bg-white text-gray-700 border-gray-300 hover:border-blue-400"
            }`}
          >
            <input
              type="checkbox"
              className="sr-only"
              checked={selected}
              onChange={() => toggle(model.id)}
            />
            <span>{model.displayName}</span>
            {inputMods.map((mod) => (
              <span
                key={mod}
                className={`text-xs px-1 rounded ${selected ? "bg-blue-500" : "bg-gray-100 text-gray-500"}`}
              >
                {mod}
              </span>
            ))}
          </label>
        );
      })}
    </div>
  );
}
