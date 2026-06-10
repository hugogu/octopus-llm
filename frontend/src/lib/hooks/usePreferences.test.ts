import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { usePreferences } from './usePreferences';
import * as userConfigApi from '@/lib/api/userConfig';
import * as authApi from '@/lib/api/auth';

describe('usePreferences', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads preferences on mount', async () => {
    const mockPreferences = {
      lastSelectedModelId: 'gpt-4o',
      themePreference: 'dark',
      sidebarCollapsed: true,
    };
    
    vi.spyOn(authApi, 'getToken').mockReturnValue('test-token');
    vi.spyOn(userConfigApi, 'getPreferences').mockResolvedValue(mockPreferences);

    const { result } = renderHook(() => usePreferences());

    await waitFor(() => {
      expect(result.current.preferences).toEqual(mockPreferences);
    });
    
    expect(result.current.loading).toBe(false);
  });

  it('handles missing token', async () => {
    vi.spyOn(authApi, 'getToken').mockReturnValue(null);

    const { result } = renderHook(() => usePreferences());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });
    
    expect(result.current.preferences).toBeNull();
  });

  it('saves preferences', async () => {
    const mockPreferences = {
      lastSelectedModelId: 'gpt-4o',
      themePreference: 'light',
      sidebarCollapsed: false,
    };
    
    vi.spyOn(authApi, 'getToken').mockReturnValue('test-token');
    vi.spyOn(userConfigApi, 'getPreferences').mockResolvedValue(mockPreferences);
    vi.spyOn(userConfigApi, 'updatePreferences').mockResolvedValue({
      ...mockPreferences,
      themePreference: 'dark',
    });

    const { result } = renderHook(() => usePreferences());

    await waitFor(() => {
      expect(result.current.preferences).toEqual(mockPreferences);
    });

    await result.current.savePreferences({ themePreference: 'dark' });

    await waitFor(() => {
      expect(result.current.preferences?.themePreference).toBe('dark');
    });
  });

  it('sets last selected model', async () => {
    const mockPreferences = {
      lastSelectedModelId: null,
      themePreference: 'system',
      sidebarCollapsed: false,
    };
    
    vi.spyOn(authApi, 'getToken').mockReturnValue('test-token');
    vi.spyOn(userConfigApi, 'getPreferences').mockResolvedValue(mockPreferences);
    vi.spyOn(userConfigApi, 'updatePreferences').mockResolvedValue({
      ...mockPreferences,
      lastSelectedModelId: 'claude-3',
    });

    const { result } = renderHook(() => usePreferences());

    await waitFor(() => {
      expect(result.current.preferences).toEqual(mockPreferences);
    });

    await result.current.setLastSelectedModel('claude-3');

    await waitFor(() => {
      expect(result.current.preferences?.lastSelectedModelId).toBe('claude-3');
    });
  });

  it('handles API errors', async () => {
    vi.spyOn(authApi, 'getToken').mockReturnValue('test-token');
    vi.spyOn(userConfigApi, 'getPreferences').mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => usePreferences());

    await waitFor(() => {
      expect(result.current.error).toBe('Network error');
    });
    
    expect(result.current.loading).toBe(false);
  });
});
