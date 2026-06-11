'use client';

import { useMemo } from 'react';
import type { ModelDefinition, UserModelConfig, ApiKeyMeta } from '@/lib/types/api';

interface ModelListProps {
  models: ModelDefinition[];
  configs: UserModelConfig[];
  apiKeys: ApiKeyMeta[];
  selectedIds: string[];
  onToggle: (id: string) => void;
}

export default function ModelList({ models, configs, apiKeys, selectedIds, onToggle }: ModelListProps) {
  const enabledModelIds = useMemo(
    () => new Set(configs.filter((c) => c.isEnabled).map((c) => c.modelId)),
    [configs],
  );

  const providerKeys = useMemo(
    () => new Set(apiKeys.map((k) => k.providerId)),
    [apiKeys],
  );

  const groupedModels = useMemo(() => {
    const enabled = models.filter((m) => enabledModelIds.has(m.id));
    const groups = new Map<string, ModelDefinition[]>();
    
    for (const model of enabled) {
      const group = groups.get(model.providerId) ?? [];
      group.push(model);
      groups.set(model.providerId, group);
    }
    
    return Array.from(groups.entries()).sort(([a], [b]) => a.localeCompare(b));
  }, [models, enabledModelIds]);

  const isProviderConfigured = (providerId: string) => providerKeys.has(providerId);

  if (groupedModels.length === 0) {
    return (
      <div className="text-center py-6">
        <p className="text-sm text-gray-500 dark:text-gray-400">
          No models available.{' '}
          <a href="/settings/models" className="text-blue-600 hover:underline">
            Configure API keys
          </a>
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {groupedModels.map(([providerId, providerModels]) => {
        const hasKey = isProviderConfigured(providerId);
        
        return (
          <div key={providerId} className="rounded-xl border border-gray-100 bg-gray-50/70 px-3 py-3">
            <div className="mb-2 flex items-center gap-2">
              <h3 className="text-xs font-semibold uppercase tracking-[0.16em] text-gray-700 dark:text-gray-100">
                {providerId}
              </h3>
              <span
                className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium ${
                  hasKey
                    ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                    : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400'
                }`}
              >
                {hasKey ? 'Active' : 'No key'}
              </span>
            </div>
            
            <div className="flex flex-wrap gap-2">
              {providerModels.map((model) => {
                const selected = selectedIds.includes(model.id);
                return (
                  <button
                    key={model.id}
                    onClick={() => onToggle(model.id)}
                    className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-sm transition-all ${
                      selected
                        ? 'bg-blue-600 text-white border-blue-600 shadow-sm'
                        : 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 border-gray-300 dark:border-gray-600 hover:border-blue-400 dark:hover:border-blue-500'
                    }`}
                  >
                    <span className="font-medium">{model.displayName}</span>
                    {model.capabilityMatrix.supports_streaming && (
                      <span className={`text-xs ${selected ? 'text-blue-200' : 'text-gray-400'}`}>
                        ⚡
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
