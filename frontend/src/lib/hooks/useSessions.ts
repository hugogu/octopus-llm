'use client';

import { useState, useEffect, useCallback } from 'react';
import type { ChatSession } from '@/lib/types/api';
import { listSessions, deleteSession } from '@/lib/api/chat';
import { getToken } from '@/lib/api/auth';

export function useSessions() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadSessions = useCallback(async () => {
    try {
      const token = getToken();
      if (!token) {
        setLoading(false);
        return;
      }
      const { sessions: data } = await listSessions({ limit: 50 }, token);
      setSessions(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load sessions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const removeSession = useCallback(async (sessionId: string) => {
    try {
      const token = getToken();
      if (!token) throw new Error('Not authenticated');
      await deleteSession(sessionId, token);
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
