'use client';

import { useState } from 'react';
import { Plus, KeyRound, BrainCircuit, Trash2, AlertCircle } from 'lucide-react';
import { useRouter } from 'next/navigation';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import SettingsLayout, { SettingsSection, EmptyState } from '@/components/models/SettingsLayout';
import ApiKeyForm from '@/components/models/ApiKeyForm';
import CustomModelForm from '@/components/models/CustomModelForm';
import ModelCard from '@/components/models/ModelCard';
import ModelConfigControls from '@/components/models/ModelConfigControls';
import type { ModelDefinition, ApiKeyMeta, UserModelConfig } from '@/lib/types/api';
import { deleteApiKey } from '@/lib/api/userConfig';
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

  const sortedModels = [...models].sort((a, b) =>
    a.providerId.localeCompare(b.providerId) || a.displayName.localeCompare(b.displayName),
  );

  const configMap = new Map(modelConfigs.map((c) => [c.modelId, c]));
  const keyMap = new Map(apiKeys.map((k) => [k.id, k]));

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
          <div className="space-y-3">
            {apiKeys.map((key) => (
              <div
                key={key.id}
                className="flex items-center justify-between p-3 sm:p-4 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50"
              >
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
                  <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
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
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {sortedModels.map((model: ModelDefinition) => {
            const config = configMap.get(model.id);
            const keyMeta = config?.providerApiKeyId ? keyMap.get(config.providerApiKeyId) : undefined;
            return (
              <ModelCard key={model.id} model={model}>
                <div className="flex flex-col items-end gap-1 text-xs">
                  {config ? (
                    <>
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                        config.isEnabled
                          ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                          : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
                      }`}>
                        {config.isEnabled ? 'Enabled' : 'Disabled'}
                      </span>
                      <span className="text-gray-400">{model.source.toLowerCase()}</span>
                      {keyMeta && <span className="text-gray-400">{keyMeta.providerId}</span>}
                    </>
                  ) : (
                    <>
                      <span className="text-gray-400">Not configured</span>
                      <span className="text-gray-400">{model.source.toLowerCase()}</span>
                    </>
                  )}
                </div>
                <ModelConfigControls
                  model={model}
                  apiKeys={apiKeys}
                  config={config}
                />
              </ModelCard>
            );
          })}
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
