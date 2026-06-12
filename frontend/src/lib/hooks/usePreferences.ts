'use client';

import { useState, useEffect, useCallback } from 'react';
import type { UpdatePreferencesRequestV2, UserPreferencesV2 } from '@/lib/types/api';
import { getPreferences, updatePreferences } from '@/lib/api/userConfig';
import { getToken } from '@/lib/api/auth';

export function usePreferences() {
  const [preferences, setPreferences] = useState<UserPreferencesV2 | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadPreferences = useCallback(async () => {
    try {
      const token = getToken();
      if (!token) {
        setLoading(false);
        return;
      }
      const prefs = await getPreferences(token);
      setPreferences(prefs);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load preferences');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => void loadPreferences());
  }, [loadPreferences]);

  const savePreferences = useCallback(async (updates: UpdatePreferencesRequestV2) => {
    try {
      const token = getToken();
      if (!token) throw new Error('Not authenticated');
      
      const updated = await updatePreferences(token, updates);
      setPreferences(updated);
      setError(null);
      return updated;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save preferences');
      throw err;
    }
  }, []);

  const setLastSelectedModel = useCallback(async (configuredModelId: string | null) => {
    return savePreferences({ lastSelectedConfiguredModelId: configuredModelId });
  }, [savePreferences]);

  return {
    preferences,
    loading,
    error,
    loadPreferences,
    savePreferences,
    setLastSelectedModel,
  };
}
