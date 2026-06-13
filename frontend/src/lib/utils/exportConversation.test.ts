import { describe, it, expect } from 'vitest';
import { conversationToMarkdown, conversationFilename } from './exportConversation';
import type { GetSessionResponseV2 } from '@/lib/types/api';

const session: GetSessionResponseV2 = {
  id: 's1',
  title: '几何面积公式',
  turns: [
    {
      id: 't1',
      sequenceNum: 1,
      promptText: '给出圆的面积公式',
      selectedModelIds: ['m1', 'm2'],
      selectedConfiguredModelIds: ['cm1', 'cm2'],
      createdAt: '2026-06-11T00:00:00Z',
      responses: [
        {
          responseId: 'r1',
          configuredModelId: 'cm1',
          modelId: 'm1',
          modelDisplayName: 'Model One',
          protocol: 'openai-compatible',
          connectionLabel: 'Primary',
          status: 'complete',
          responseText: '$S = \\pi r^2$',
          reasoningText: '用户要圆面积……',
          errorMessage: null,
          inputTokens: 10,
          outputTokens: 20,
          latencyMs: 1200,
          likeCount: 1,
          likedByMe: true,
        },
        {
          responseId: 'r2',
          configuredModelId: 'cm2',
          modelId: 'm2',
          modelDisplayName: 'Model Two',
          protocol: 'anthropic',
          connectionLabel: null,
          status: 'error',
          responseText: null,
          reasoningText: null,
          errorMessage: 'rate limited',
          inputTokens: null,
          outputTokens: null,
          latencyMs: 300,
          likeCount: 0,
          likedByMe: false,
        },
      ],
    },
  ],
};

describe('conversationToMarkdown', () => {
  it('includes title, prompt, and responses with display names', () => {
    const md = conversationToMarkdown(session);
    expect(md).toContain('# 几何面积公式');
    expect(md).toContain('## You');
    expect(md).toContain('给出圆的面积公式');
    expect(md).toContain('### Model One (Primary)');
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

  it('uses immutable snapshot names instead of current configuration', () => {
    const md = conversationToMarkdown(session);
    expect(md).toContain('### Model Two');
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
