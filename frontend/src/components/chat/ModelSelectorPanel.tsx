"use client";

import type { UserModelConfig, ModelDefinition, ApiKeyMeta } from "@/lib/types/api";
import ModelList from "@/components/models/ModelList";

interface ModelSelectorPanelProps {
  models: ModelDefinition[];
  configs: UserModelConfig[];
  apiKeys?: ApiKeyMeta[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
}

export default function ModelSelectorPanel({
  models,
  configs,
  apiKeys = [],
  selectedIds,
  onChange,
}: ModelSelectorPanelProps) {
  function toggle(id: string) {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter((x) => x !== id));
    } else {
      onChange([...selectedIds, id]);
    }
  }

  return (
    <ModelList
      models={models}
      configs={configs}
      apiKeys={apiKeys}
      selectedIds={selectedIds}
      onToggle={toggle}
    />
  );
}
