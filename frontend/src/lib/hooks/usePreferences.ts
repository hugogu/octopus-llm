'use client';

import { useState, useEffect, useCallback } from 'react';
import type { UserPreferences } from '@/lib/types/api';
import { getPreferences, updatePreferences } from '@/lib/api/userConfig';
import { getToken } from '@/lib/api/auth';

export function usePreferences() {
  const [preferences, setPreferences] = useState<UserPreferences | null>(null);
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
    loadPreferences();
  }, [loadPreferences]);

  const savePreferences = useCallback(async (updates: import("@/lib/types/api").UpdatePreferencesRequest) => {
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

  const setLastSelectedModel = useCallback(async (modelId: string | null) => {
    return savePreferences({ lastSelectedModelId: modelId ?? undefined });
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
