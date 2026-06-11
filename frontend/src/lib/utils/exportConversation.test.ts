import { describe, it, expect } from 'vitest';
import { conversationToMarkdown, conversationFilename } from './exportConversation';
import type { GetSessionResponse } from '@/lib/types/api';

const session: GetSessionResponse = {
  id: 's1',
  title: '几何面积公式',
  turns: [
    {
      id: 't1',
      sequenceNum: 1,
      promptText: '给出圆的面积公式',
      attachments: [],
      selectedModelIds: ['m1', 'm2'],
      createdAt: '2026-06-11T00:00:00Z',
      responses: [
        {
          modelId: 'm1',
          status: 'complete',
          responseText: '$S = \\pi r^2$',
          reasoningText: '用户要圆面积……',
          errorMessage: null,
          inputTokens: 10,
          outputTokens: 20,
          latencyMs: 1200,
        },
        {
          modelId: 'm2',
          status: 'error',
          responseText: null,
          reasoningText: null,
          errorMessage: 'rate limited',
          inputTokens: null,
          outputTokens: null,
          latencyMs: 300,
        },
      ],
    },
  ],
};

describe('conversationToMarkdown', () => {
  it('includes title, prompt, and responses with display names', () => {
    const md = conversationToMarkdown(session, { m1: 'Model One' });
    expect(md).toContain('# 几何面积公式');
    expect(md).toContain('## You');
    expect(md).toContain('给出圆的面积公式');
    expect(md).toContain('### Model One');
    expect(md).toContain('$S = \\pi r^2$');
  });

  it('renders reasoning inside a details block', () => {
    const md = conversationToMarkdown(session);
    expect(md).toContain('<summary>Thought process</summary>');
    expect(md).toContain('用户要圆面积……');
  });

  it('renders errors as blockquotes', () => {
    const md = conversationToMarkdown(session);
    expect(md).toContain('> ⚠️ Error: rate limited');
  });

  it('falls back to modelId when no display name', () => {
    const md = conversationToMarkdown(session);
    expect(md).toContain('### m1');
  });
});

describe('conversationFilename', () => {
  it('sanitizes unsafe characters', () => {
    expect(conversationFilename('a/b: c?')).toBe('a-b-c.md');
  });

  it('falls back for null titles', () => {
    expect(conversationFilename(null)).toBe('conversation.md');
  });

  it('truncates long titles', () => {
    expect(conversationFilename('x'.repeat(100)).length).toBeLessThanOrEqual(63);
  });
});
