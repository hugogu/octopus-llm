'use client';

import { useState, useEffect, useCallback } from 'react';
import type { ChatSessionV2 } from '@/lib/types/api';
import { deleteSessionV2, listSessionsV2 } from '@/lib/api/chatV2';
import { getToken } from '@/lib/api/auth';

export function useSessions() {
  const [sessions, setSessions] = useState<ChatSessionV2[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadSessions = useCallback(async () => {
    try {
      const token = getToken();
      if (!token) {
        setLoading(false);
        return;
      }
      const response = await listSessionsV2(0, 50, token);
      setSessions(response.items);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load sessions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    queueMicrotask(() => void loadSessions());
  }, [loadSessions]);

  const removeSession = useCallback(async (sessionId: string) => {
    try {
      const token = getToken();
      if (!token) throw new Error('Not authenticated');
      await deleteSessionV2(sessionId, token);
      setSessions((prev) => prev.filter((s) => s.id !== sessionId));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete session');
      throw err;
    }
  }, []);

  return {
    sessions,
    loading,
    error,
    loadSessions,
    removeSession,
  };
}
