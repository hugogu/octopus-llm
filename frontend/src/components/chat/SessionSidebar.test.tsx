import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SessionSidebar from './SessionSidebar';
import type { ChatSessionV2 } from '@/lib/types/api';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/lib/api/auth', () => ({ getToken: () => 'token', logout: vi.fn() }));
vi.mock('@/lib/api/admin', () => ({ getMe: vi.fn().mockResolvedValue({ id: 'u1', email: 'alice@example.com', displayName: 'Alice', isAdmin: false }) }));
vi.mock('@/lib/api/shares', () => ({
  importSharedSession: vi.fn(),
  newShareImportKey: vi.fn(() => 'key'),
}));

describe('SessionSidebar', () => {
  const mockSessions: ChatSessionV2[] = [
    { id: '1', title: 'Session 1', createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
    { id: '2', title: 'Session 2', createdAt: '2024-01-02T00:00:00Z', updatedAt: '2024-01-02T00:00:00Z' },
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

  it('calls onNewSession when the primary New Quest action is clicked', () => {
    const onNew = vi.fn();
    render(
      <SessionSidebar
        sessions={mockSessions}
        onSelectSession={vi.fn()}
        onDeleteSession={vi.fn()}
        onNewSession={onNew}
      />
    );
    
    fireEvent.click(screen.getByText('New Quest'));
    expect(onNew).toHaveBeenCalledTimes(1);
  });

  it('opens the import dialog from the attached secondary action by mouse and keyboard', async () => {
    const user = userEvent.setup();
    render(
      <SessionSidebar
        sessions={mockSessions}
        onSelectSession={vi.fn()}
        onDeleteSession={vi.fn()}
        onNewSession={vi.fn()}
      />
    );
    const secondary = screen.getByRole('button', { name: 'Import Quest' });
    await user.click(secondary);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    secondary.focus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
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
