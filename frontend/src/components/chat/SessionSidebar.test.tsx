import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import SessionSidebar from './SessionSidebar';
import type { ChatSession } from '@/lib/types/api';

describe('SessionSidebar', () => {
  const mockSessions: ChatSession[] = [
    { id: '1', title: 'Session 1', selectedModelId: 'gpt-4', createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
    { id: '2', title: 'Session 2', selectedModelId: null, createdAt: '2024-01-02T00:00:00Z', updatedAt: '2024-01-02T00:00:00Z' },
  ];

  it('renders session list', () => {
    render(
      <SessionSidebar
        sessions={mockSessions}
        onSelectSession={vi.fn()}
        onDeleteSession={vi.fn()}
        onNewSession={vi.fn()}
      />
    );
    
    expect(screen.getByText('Session 1')).toBeInTheDocument();
    expect(screen.getByText('Session 2')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    render(
      <SessionSidebar
        sessions={[]}
        onSelectSession={vi.fn()}
        onDeleteSession={vi.fn()}
        onNewSession={vi.fn()}
        loading={true}
      />
    );
    
    expect(screen.getAllByRole('generic').some(el => el.className.includes('animate-pulse'))).toBe(true);
  });

  it('shows empty state when no sessions', () => {
    render(
      <SessionSidebar
        sessions={[]}
        onSelectSession={vi.fn()}
        onDeleteSession={vi.fn()}
        onNewSession={vi.fn()}
      />
    );
    
    expect(screen.getByText('No conversations yet')).toBeInTheDocument();
  });

  it('calls onSelectSession when session is clicked', () => {
    const onSelect = vi.fn();
    render(
      <SessionSidebar
        sessions={mockSessions}
        onSelectSession={onSelect}
        onDeleteSession={vi.fn()}
        onNewSession={vi.fn()}
      />
    );
    
    fireEvent.click(screen.getByText('Session 1'));
    expect(onSelect).toHaveBeenCalledWith('1');
  });

  it('calls onNewSession when new chat button is clicked', () => {
    const onNew = vi.fn();
    render(
      <SessionSidebar
        sessions={mockSessions}
        onSelectSession={vi.fn()}
        onDeleteSession={vi.fn()}
        onNewSession={onNew}
      />
    );
    
    fireEvent.click(screen.getByText('New Chat'));
    expect(onNew).toHaveBeenCalledTimes(1);
  });

  it('highlights current session', () => {
    render(
      <SessionSidebar
        sessions={mockSessions}
        currentSessionId="1"
        onSelectSession={vi.fn()}
        onDeleteSession={vi.fn()}
        onNewSession={vi.fn()}
      />
    );
    
    const session1 = screen.getByText('Session 1').closest('div[class*="shadow-sm"]');
    expect(session1).toBeInTheDocument();
  });
});
