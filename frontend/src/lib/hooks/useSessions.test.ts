import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useSessions } from './useSessions';
import * as chatApi from '@/lib/api/chat';
import * as authApi from '@/lib/api/auth';

describe('useSessions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads sessions on mount', async () => {
    const mockSessions = [
      { id: '1', title: 'Session 1', selectedModelId: 'gpt-4', createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
      { id: '2', title: 'Session 2', selectedModelId: null, createdAt: '2024-01-02T00:00:00Z', updatedAt: '2024-01-02T00:00:00Z' },
    ];
    
    vi.spyOn(authApi, 'getToken').mockReturnValue('test-token');
    vi.spyOn(chatApi, 'listSessions').mockResolvedValue({ sessions: mockSessions, total: 2 });

    const { result } = renderHook(() => useSessions());

    await waitFor(() => {
      expect(result.current.sessions).toEqual(mockSessions);
    });
    
    expect(result.current.loading).toBe(false);
    expect(result.current.sessions).toHaveLength(2);
  });

  it('handles missing token', async () => {
    vi.spyOn(authApi, 'getToken').mockReturnValue(null);

    const { result } = renderHook(() => useSessions());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });
    
    expect(result.current.sessions).toEqual([]);
  });

  it('deletes session', async () => {
    const mockSessions = [
      { id: '1', title: 'Session 1', selectedModelId: 'gpt-4', createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
      { id: '2', title: 'Session 2', selectedModelId: null, createdAt: '2024-01-02T00:00:00Z', updatedAt: '2024-01-02T00:00:00Z' },
    ];
    
    vi.spyOn(authApi, 'getToken').mockReturnValue('test-token');
    vi.spyOn(chatApi, 'listSessions').mockResolvedValue({ sessions: mockSessions, total: 2 });
    vi.spyOn(chatApi, 'deleteSession').mockResolvedValue(undefined);

    const { result } = renderHook(() => useSessions());

    await waitFor(() => {
      expect(result.current.sessions).toHaveLength(2);
    });

    await result.current.removeSession('1');

    await waitFor(() => {
      expect(result.current.sessions).toHaveLength(1);
      expect(result.current.sessions[0]?.id).toBe('2');
    });
  });

  it('reloads sessions', async () => {
    const mockSessions = [
      { id: '1', title: 'Session 1', selectedModelId: 'gpt-4', createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
    ];
    
    vi.spyOn(authApi, 'getToken').mockReturnValue('test-token');
    vi.spyOn(chatApi, 'listSessions').mockResolvedValue({ sessions: mockSessions, total: 1 });

    const { result } = renderHook(() => useSessions());

    await waitFor(() => {
      expect(result.current.sessions).toHaveLength(1);
    });

    const newSessions = [
      ...mockSessions,
      { id: '2', title: 'Session 2', selectedModelId: null, createdAt: '2024-01-02T00:00:00Z', updatedAt: '2024-01-02T00:00:00Z' },
    ];
    vi.spyOn(chatApi, 'listSessions').mockResolvedValue({ sessions: newSessions, total: 2 });

    await result.current.loadSessions();

    await waitFor(() => {
      expect(result.current.sessions).toHaveLength(2);
    });
  });

  it('handles API errors', async () => {
    vi.spyOn(authApi, 'getToken').mockReturnValue('test-token');
    vi.spyOn(chatApi, 'listSessions').mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useSessions());

    await waitFor(() => {
      expect(result.current.error).toBe('Network error');
    });
    
    expect(result.current.loading).toBe(false);
  });
});
