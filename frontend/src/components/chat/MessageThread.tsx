'use client';

import { User, Bot } from 'lucide-react';
import StreamingMarkdown from './StreamingMarkdown';

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
}

export default function MessageThread({ messages }: MessageThreadProps) {
  return (
    <div className="flex flex-col gap-4">
      {messages.map((message) => (
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
