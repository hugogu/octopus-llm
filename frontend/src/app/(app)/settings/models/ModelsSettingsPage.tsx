'use client';

import { useMemo, useState } from 'react';
import { Plus, KeyRound, BrainCircuit, Trash2, AlertCircle, RefreshCw } from 'lucide-react';
import { useRouter } from 'next/navigation';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import SettingsLayout, { SettingsSection, EmptyState } from '@/components/models/SettingsLayout';
import ApiKeyForm from '@/components/models/ApiKeyForm';
import ApiKeyBaseUrlEditor from '@/components/models/ApiKeyBaseUrlEditor';
import CustomModelForm from '@/components/models/CustomModelForm';
import ModelCard from '@/components/models/ModelCard';
import ModelConfigControls from '@/components/models/ModelConfigControls';
import type { ModelDefinition, ApiKeyMeta, UserModelConfig } from '@/lib/types/api';
import { deleteApiKey, syncProviderModels } from '@/lib/api/userConfig';
import { getToken } from '@/lib/api/auth';

interface ModelsSettingsPageProps {
  models: ModelDefinition[];
  apiKeys: ApiKeyMeta[];
  modelConfigs: UserModelConfig[];
}

export default function ModelsSettingsPage({ models, apiKeys, modelConfigs }: ModelsSettingsPageProps) {
  const router = useRouter();
  const [showAddKeyModal, setShowAddKeyModal] = useState(false);
  const [showAddModelModal, setShowAddModelModal] = useState(false);
  const [deletingKeyId, setDeletingKeyId] = useState<string | null>(null);
  const [syncingKeyId, setSyncingKeyId] = useState<string | null>(null);

  const sortedModels = useMemo(
    () => [...models].sort((a, b) =>
      a.providerId.localeCompare(b.providerId) || a.displayName.localeCompare(b.displayName),
    ),
    [models],
  );

  const configMap = new Map(modelConfigs.map((c) => [c.modelId, c]));
  const keyMap = new Map(apiKeys.map((k) => [k.id, k]));
  const configuredProviders = new Set(apiKeys.map((key) => key.providerId));
  const providerGroups = useMemo(() => {
    const groups = new Map<string, ModelDefinition[]>();
    for (const model of sortedModels) {
      const group = groups.get(model.providerId) ?? [];
      group.push(model);
      groups.set(model.providerId, group);
    }
    return Array.from(groups.entries());
  }, [sortedModels]);

  const handleDeleteKey = async (keyId: string) => {
    if (!confirm('Are you sure you want to delete this API key? All associated model configs will be disabled.')) {
      return;
    }
    setDeletingKeyId(keyId);
    try {
      const token = getToken();
      if (!token) throw new Error('Not authenticated');
      await deleteApiKey(token, keyId);
      router.refresh();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to delete key');
    } finally {
      setDeletingKeyId(null);
    }
  };

  const handleSyncKey = async (key: ApiKeyMeta) => {
    setSyncingKeyId(key.id);
    try {
      const token = getToken();
      if (!token) throw new Error('Not authenticated');
      await syncProviderModels(token, {
        providerId: key.providerId,
        providerApiKeyId: key.id,
      });
      router.refresh();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to load provider models');
    } finally {
      setSyncingKeyId(null);
    }
  };

  return (
    <SettingsLayout
      title="Model Settings"
      subtitle="Manage your API keys and model configurations"
    >
      {/* API Keys Section */}
      <SettingsSection
        title="API Keys"
        icon={KeyRound}
        action={
          <Button
            size="sm"
            onClick={() => setShowAddKeyModal(true)}
          >
            <Plus className="w-4 h-4 mr-1.5" />
            Add Key
          </Button>
        }
        emptyState={
          <EmptyState
            icon={KeyRound}
            title="No API Keys"
            description="Add your first API key to start using models"
            action={
              <Button size="sm" onClick={() => setShowAddKeyModal(true)}>
                <Plus className="w-4 h-4 mr-1.5" />
                Add API Key
              </Button>
            }
          />
        }
      >
        {apiKeys.length > 0 && (
          <div className="grid gap-3 lg:grid-cols-2">
            {apiKeys.map((key) => (
              <div
                key={key.id}
                className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm text-gray-900 dark:text-gray-100">
                        {key.providerId}
                      </span>
                      {key.label && (
                        <span className="text-xs text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-800 px-2 py-0.5 rounded-full">
                          {key.label}
                        </span>
                      )}
                    </div>
                    <p className="mt-2 text-sm text-gray-600">
                      Use this key to sync provider models and bind them to model configs below.
                    </p>
                    <ApiKeyBaseUrlEditor keyId={key.id} baseUrl={key.baseUrl} />
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-2">
                      Added {new Date(key.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleDeleteKey(key.id)}
                    isLoading={deletingKeyId === key.id}
                    className="text-red-600 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-900/20"
                  >
                    <Trash2 className="w-4 h-4" />
                  </Button>
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => handleSyncKey(key)}
                    isLoading={syncingKeyId === key.id}
                  >
                    <RefreshCw className="mr-1.5 h-4 w-4" />
                    Load Models
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setShowAddModelModal(true)}
                  >
                    <Plus className="mr-1.5 h-4 w-4" />
                    Add Custom Model
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </SettingsSection>

      {/* Custom Models Section */}
      <SettingsSection
        title="Custom Models"
        icon={BrainCircuit}
        action={
          apiKeys.length > 0 && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => setShowAddModelModal(true)}
            >
              <Plus className="w-4 h-4 mr-1.5" />
              Add Model
            </Button>
          )
        }
        emptyState={
          <EmptyState
            icon={BrainCircuit}
            title="No Custom Models"
            description={
              apiKeys.length === 0
                ? "Add an API key first to create custom models"
                : "Add custom models not in the default catalogue"
            }
            action={
              apiKeys.length > 0 && (
                <Button size="sm" variant="secondary" onClick={() => setShowAddModelModal(true)}>
                  <Plus className="w-4 h-4 mr-1.5" />
                  Add Custom Model
                </Button>
              )
            }
          />
        }
      >
        {apiKeys.length === 0 && (
          <div className="flex items-center gap-2 p-3 rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800">
            <AlertCircle className="w-4 h-4 text-amber-600 flex-shrink-0" />
            <p className="text-sm text-amber-800 dark:text-amber-200">
              Add an API key to enable custom model creation
            </p>
          </div>
        )}
      </SettingsSection>

      {/* Available Models Section */}
      <SettingsSection title="Available Models" icon={BrainCircuit}>
        <div className="space-y-6">
          {providerGroups.map(([providerId, providerModels]) => (
            <div key={providerId} className="space-y-3">
              <div className="flex flex-wrap items-center gap-3">
                <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-gray-700">
                  {providerId}
                </h3>
                <span className="rounded-full bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-600">
                  {providerModels.length} models
                </span>
                <span
                  className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                    configuredProviders.has(providerId)
                      ? 'bg-green-100 text-green-700'
                      : 'bg-gray-100 text-gray-500'
                  }`}
                >
                  {configuredProviders.has(providerId) ? 'Key configured' : 'No key configured'}
                </span>
              </div>

              <div className="grid gap-4 xl:grid-cols-2">
                {providerModels.map((model: ModelDefinition) => {
                  const config = configMap.get(model.id);
                  const keyMeta = config?.providerApiKeyId ? keyMap.get(config.providerApiKeyId) : undefined;
                  return (
                    <ModelCard
                      key={model.id}
                      model={model}
                      status={(
                        <div className="flex flex-col items-end gap-1 text-right">
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                            config?.isEnabled
                              ? 'bg-green-100 text-green-700'
                              : config
                                ? 'bg-gray-100 text-gray-600'
                                : 'bg-amber-100 text-amber-700'
                          }`}>
                            {config ? (config.isEnabled ? 'Enabled' : 'Disabled') : 'Not configured'}
                          </span>
                          <span className="text-xs uppercase tracking-[0.14em] text-gray-400">
                            {model.source.toLowerCase()}
                          </span>
                          {keyMeta ? (
                            <span className="text-xs text-gray-500">{keyMeta.label || `${keyMeta.providerId} key`}</span>
                          ) : null}
                        </div>
                      )}
                    >
                      <ModelConfigControls
                        model={model}
                        apiKeys={apiKeys}
                        config={config}
                      />
                    </ModelCard>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </SettingsSection>

      {/* Add API Key Modal */}
      <Modal
        isOpen={showAddKeyModal}
        onClose={() => setShowAddKeyModal(false)}
        title="Add API Key"
      >
        <ApiKeyForm
          models={models}
          onClose={() => setShowAddKeyModal(false)}
        />
      </Modal>

      {/* Add Custom Model Modal */}
      <Modal
        isOpen={showAddModelModal}
        onClose={() => setShowAddModelModal(false)}
        title="Add Custom Model"
        size="lg"
      >
        <CustomModelForm
          apiKeys={apiKeys}
          onClose={() => setShowAddModelModal(false)}
        />
      </Modal>
    </SettingsLayout>
  );
}
