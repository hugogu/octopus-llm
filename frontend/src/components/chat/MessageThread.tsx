'use client';

import { useState } from 'react';
import { User, Bot, Trash2 } from 'lucide-react';
import StreamingMarkdown from './StreamingMarkdown';
import { confirmDialog } from '@/lib/ui/confirm';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  modelId?: string;
  status?: 'streaming' | 'complete' | 'error';
  errorMessage?: string;
}

interface MessageThreadProps {
  messages: Message[];
  onDeleteMessage?: (message: Message) => Promise<void>;
}

export default function MessageThread({ messages, onDeleteMessage }: MessageThreadProps) {
  const [hiddenIds, setHiddenIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  async function remove(message: Message) {
    if (!onDeleteMessage) return;
    const confirmed = await confirmDialog({
      title: message.role === 'user' ? 'Delete this prompt?' : 'Delete this model response?',
      message: message.role === 'user'
        ? 'The prompt and every model response in this turn will be removed from Quest views.'
        : 'Only this model response will be removed from Quest views.',
      confirmLabel: 'Delete Dialog',
      danger: true,
    });
    if (!confirmed) return;
    setError(null);
    try {
      await onDeleteMessage(message);
      setHiddenIds((current) => new Set(current).add(message.id));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Delete failed');
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      {messages.filter((message) => !hiddenIds.has(message.id)).map((message) => (
        <div
          key={message.id}
          className={`flex gap-3 ${
            message.role === 'user' ? 'flex-row' : 'flex-row'
          }`}
        >
          <div className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${
            message.role === 'user'
              ? 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400'
              : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
          }`}>
            {message.role === 'user' ? (
              <User className="w-4 h-4" />
            ) : (
              <Bot className="w-4 h-4" />
            )}
          </div>

          <div className={`flex-1 min-w-0 ${
            message.role === 'user'
              ? 'bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800'
              : 'bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700'
          } rounded-lg px-4 py-3`}>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs font-medium text-gray-500 dark:text-gray-400">
                {message.role === 'user' ? 'You' : message.modelId}
              </span>
              {message.status === 'streaming' && (
                <span className="text-xs text-blue-500 animate-pulse">●</span>
              )}
              {message.status === 'error' && (
                <span className="text-xs text-red-500">Error</span>
              )}
              {onDeleteMessage && message.status !== 'streaming' && (
                <button
                  type="button"
                  onClick={() => void remove(message)}
                  aria-label={`Delete ${message.role === 'user' ? 'prompt' : 'response'}`}
                  className="ml-auto rounded p-1 text-stone-400 hover:bg-red-50 hover:text-red-600"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              )}
            </div>

            {message.status === 'error' ? (
              <p className="text-sm text-red-600">{message.errorMessage || 'An error occurred'}</p>
            ) : message.role === 'user' ? (
              <p className="text-sm text-gray-900 dark:text-gray-100 whitespace-pre-wrap">{message.content}</p>
            ) : (
              <StreamingMarkdown content={message.content} debounceMs={100} />
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
