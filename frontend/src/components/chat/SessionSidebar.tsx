'use client';

import { useState } from 'react';
import { MessageSquare, Trash2, Plus, Clock, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import type { ChatSessionV2 } from '@/lib/types/api';
import Button from '@/components/ui/Button';
import AdminNavLink from '@/components/admin/AdminNavLink';

interface SessionSidebarProps {
  sessions: ChatSessionV2[];
  currentSessionId?: string | null;
  onSelectSession: (sessionId: string) => void;
  onDeleteSession: (sessionId: string) => void;
  onNewSession: () => void;
  loading?: boolean;
}

export default function SessionSidebar({
  sessions,
  currentSessionId,
  onSelectSession,
  onDeleteSession,
  onNewSession,
  loading = false,
}: SessionSidebarProps) {
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState(false);

  const handleDelete = async (sessionId: string) => {
    if (!confirm('Are you sure you want to delete this conversation?')) {
      return;
    }
    setDeletingId(sessionId);
    try {
      await onDeleteSession(sessionId);
    } finally {
      setDeletingId(null);
    }
  };

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffHours < 1) return 'Just now';
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  if (collapsed) {
    return (
      <div className="flex h-full w-14 flex-col items-center gap-2 border-r border-stone-200 bg-[#f5f4ee] py-3">
        <button
          type="button"
          onClick={() => setCollapsed(false)}
          title="Expand sidebar"
          aria-label="Expand sidebar"
          className="rounded-lg p-2 text-stone-500 transition hover:bg-white/70 hover:text-stone-800"
        >
          <PanelLeftOpen className="h-5 w-5" />
        </button>
        <button
          type="button"
          onClick={onNewSession}
          title="New chat"
          aria-label="New chat"
          className="rounded-lg bg-[#c96442] p-2 text-white transition hover:bg-[#b55538]"
        >
          <Plus className="h-5 w-5" />
        </button>
      </div>
    );
  }

  return (
    <div className="w-64 h-full flex flex-col border-r border-stone-200 bg-[#f5f4ee]">
      <div className="flex items-center gap-2 p-3">
        <Button
          onClick={onNewSession}
          fullWidth
          className="justify-center !bg-[#c96442] hover:!bg-[#b55538]"
        >
          <Plus className="w-4 h-4 mr-2" />
          New Chat
        </Button>
        <button
          type="button"
          onClick={() => setCollapsed(true)}
          title="Collapse sidebar"
          aria-label="Collapse sidebar"
          className="shrink-0 rounded-lg p-2 text-stone-500 transition hover:bg-white/70 hover:text-stone-800"
        >
          <PanelLeftClose className="h-5 w-5" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto">
        {loading ? (
          <div className="p-4 space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="animate-pulse">
                <div className="h-12 bg-gray-200 dark:bg-gray-800 rounded-lg"></div>
              </div>
            ))}
          </div>
        ) : sessions.length === 0 ? (
          <div className="p-4 text-center">
            <MessageSquare className="w-8 h-8 text-gray-300 mx-auto mb-2" />
            <p className="text-sm text-gray-500 dark:text-gray-400">No conversations yet</p>
          </div>
        ) : (
          <div className="p-2 space-y-1">
            {sessions.map((session) => (
              <div
                key={session.id}
                className={`group flex items-center gap-2 p-2.5 rounded-lg cursor-pointer transition-colors ${
                  currentSessionId === session.id
                    ? 'bg-white border border-stone-200 shadow-sm'
                    : 'hover:bg-white/70 border border-transparent'
                }`}
                onClick={() => onSelectSession(session.id)}
              >
                <MessageSquare className="w-4 h-4 text-stone-400 flex-shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-stone-800 truncate">
                    {session.title || 'New Chat'}
                  </p>
                  <div className="flex items-center gap-1 text-xs text-stone-400">
                    <Clock className="w-3 h-3" />
                    {formatDate(session.updatedAt)}
                  </div>
                </div>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDelete(session.id);
                  }}
                  disabled={deletingId === session.id}
                  className="opacity-0 group-hover:opacity-100 p-1.5 rounded hover:bg-red-50 dark:hover:bg-red-900/20 text-gray-400 hover:text-red-600 transition-opacity"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      <AdminNavLink />
    </div>
  );
}
