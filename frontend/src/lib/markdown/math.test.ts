import { describe, it, expect } from 'vitest';
import { normalizeMathDelimiters } from './math';

describe('normalizeMathDelimiters', () => {
  it('leaves plain text untouched', () => {
    expect(normalizeMathDelimiters('Hello world')).toBe('Hello world');
  });

  it('leaves dollar-style math untouched', () => {
    expect(normalizeMathDelimiters('Inline $a^2$ and block $$\\int x dx$$')).toBe(
      'Inline $a^2$ and block $$\\int x dx$$',
    );
  });

  describe('\\( \\) inline style (OpenAI / DeepSeek)', () => {
    it('converts simple inline math', () => {
      expect(normalizeMathDelimiters('面积 \\(S = a^2\\)')).toBe('面积 $S = a^2$');
    });

    it('converts inline math containing braces and commands', () => {
      expect(normalizeMathDelimiters('\\(S = \\frac{1}{2} b h\\)')).toBe(
        '$S = \\frac{1}{2} b h$',
      );
    });

    it('converts inline math spanning a line break', () => {
      expect(normalizeMathDelimiters('\\(a +\nb\\)')).toBe('$a +\nb$');
    });

    it('converts multiple occurrences on one line', () => {
      expect(normalizeMathDelimiters('长 \\(a\\)，宽 \\(b\\)，面积 \\(S = a \\times b\\)')).toBe(
        '长 $a$，宽 $b$，面积 $S = a \\times b$',
      );
    });

    it('converts inline math inside GFM table cells', () => {
      const input = '| **菱形** | \\(S = \\dfrac{d_1 \\times d_2}{2}\\) | \\(d_1, d_2\\) 为对角线 |';
      expect(normalizeMathDelimiters(input)).toBe(
        '| **菱形** | $S = \\dfrac{d_1 \\times d_2}{2}$ | $d_1, d_2$ 为对角线 |',
      );
    });
  });

  describe('\\[ \\] display style (OpenAI / DeepSeek)', () => {
    it('converts display math to $$ blocks', () => {
      expect(normalizeMathDelimiters('\\[\n\\int_0^1 x^2 dx\n\\]')).toBe(
        '$$\n\\int_0^1 x^2 dx\n$$',
      );
    });

    it('converts single-line display math', () => {
      expect(normalizeMathDelimiters('\\[E = mc^2\\]')).toBe('$$\nE = mc^2\n$$');
    });
  });

  describe('bare LaTeX environments (Gemini / GPT style)', () => {
    it('wraps a bare align environment in $$', () => {
      const input = '\\begin{align}\na &= b \\\\\nc &= d\n\\end{align}';
      expect(normalizeMathDelimiters(input)).toBe(`$$\n${input}\n$$`);
    });

    it('wraps starred environments', () => {
      const input = '\\begin{equation*}x\\end{equation*}';
      expect(normalizeMathDelimiters(input)).toBe(`$$\n${input}\n$$`);
    });

    it('does not double-wrap environments already inside $$', () => {
      const input = '$$\\begin{align}a &= b\\end{align}$$';
      expect(normalizeMathDelimiters(input)).toBe(input);
    });

    it('does not wrap non-math environments', () => {
      const input = '\\begin{theorem}text\\end{theorem}';
      expect(normalizeMathDelimiters(input)).toBe(input);
    });
  });

  describe('code protection', () => {
    it('skips fenced code blocks', () => {
      const input = '```tex\n\\(x+y\\)\n```';
      expect(normalizeMathDelimiters(input)).toBe(input);
    });

    it('skips tilde-fenced code blocks', () => {
      const input = '~~~\n\\[x\\]\n~~~';
      expect(normalizeMathDelimiters(input)).toBe(input);
    });

    it('skips unterminated fences (mid-stream content)', () => {
      const input = 'before \\(a\\)\n```tex\n\\(x+y\\)';
      expect(normalizeMathDelimiters(input)).toBe('before $a$\n```tex\n\\(x+y\\)');
    });

    it('skips inline code spans', () => {
      const input = 'use `\\(escaped\\)` here, but render \\(a\\)';
      expect(normalizeMathDelimiters(input)).toBe('use `\\(escaped\\)` here, but render $a$');
    });

    it('skips \\[ \\] inside inline code', () => {
      const input = 'literal `\\[x\\]` stays';
      expect(normalizeMathDelimiters(input)).toBe(input);
    });

    it('still converts text around code blocks', () => {
      const input = '\\(a\\)\n```\ncode\n```\n\\(b\\)';
      expect(normalizeMathDelimiters(input)).toBe('$a$\n```\ncode\n```\n$b$');
    });
  });

  it('handles a realistic mixed DeepSeek response', () => {
    const input = [
      '- **三角形**：底 \\(b\\)，高 \\(h\\)，面积 \\(S = \\frac{1}{2} b h\\)；已知三边 \\(a, b, c\\) 可用海伦公式 \\(S = \\sqrt{p(p-a)(p-b)(p-c)}\\)，其中 \\(p = \\frac{a+b+c}{2}\\)',
      '\\[',
      'S = \\pi r^2',
      '\\]',
    ].join('\n');
    const result = normalizeMathDelimiters(input);
    expect(result).toContain('$S = \\frac{1}{2} b h$');
    expect(result).toContain('$S = \\sqrt{p(p-a)(p-b)(p-c)}$');
    expect(result).toContain('$$\nS = \\pi r^2\n$$');
    expect(result).not.toContain('\\(');
  });
});
