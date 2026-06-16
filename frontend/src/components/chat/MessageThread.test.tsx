import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import MessageThread from './MessageThread';

describe('MessageThread', () => {
  const mockMessages = [
    { id: '1', role: 'user' as const, content: 'Hello' },
    { id: '2', role: 'assistant' as const, content: 'Hi there!', modelId: 'gpt-4' },
    { id: '3', role: 'user' as const, content: 'How are you?' },
    { id: '4', role: 'assistant' as const, content: 'I am doing well!', modelId: 'claude-3' },
  ];

  it('renders all messages', async () => {
    render(<MessageThread messages={mockMessages} />);

    // Message bodies render through the lazily-loaded markdown components
    // (user and assistant turns use separate dynamic chunks).
    expect(await screen.findByText('Hello')).toBeInTheDocument();
    expect(await screen.findByText('Hi there!')).toBeInTheDocument();
    expect(await screen.findByText('How are you?')).toBeInTheDocument();
    expect(await screen.findByText('I am doing well!')).toBeInTheDocument();
  });

  it('shows user label for user messages', () => {
    render(<MessageThread messages={mockMessages} />);
    expect(screen.getAllByText('You').length).toBe(2);
  });

  it('shows modelId for assistant messages', () => {
    render(<MessageThread messages={mockMessages} />);
    expect(screen.getByText('gpt-4')).toBeInTheDocument();
    expect(screen.getByText('claude-3')).toBeInTheDocument();
  });

  it('renders empty thread', () => {
    render(<MessageThread messages={[]} />);
    expect(screen.queryByText('You')).not.toBeInTheDocument();
  });

  it('renders streaming indicator', () => {
    const messages = [
      { id: '1', role: 'user' as const, content: 'Hello' },
      { id: '2', role: 'assistant' as const, content: '...', modelId: 'gpt-4', status: 'streaming' as const },
    ];
    render(<MessageThread messages={messages} />);
    
    expect(screen.getByText('●')).toBeInTheDocument();
  });

  it('renders error state', () => {
    const messages = [
      { id: '1', role: 'user' as const, content: 'Hello' },
      { id: '2', role: 'assistant' as const, content: '', modelId: 'gpt-4', status: 'error' as const, errorMessage: 'Network error' },
    ];
    render(<MessageThread messages={messages} />);
    
    expect(screen.getByText('Error')).toBeInTheDocument();
    expect(screen.getByText('Network error')).toBeInTheDocument();
  });
});
