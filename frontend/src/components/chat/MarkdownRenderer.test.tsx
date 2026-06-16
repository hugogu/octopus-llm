import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import MarkdownRenderer from './MarkdownRenderer';

// MarkdownBlock is loaded via next/dynamic (ssr: false), so it mounts after a
// microtask — assertions wait for the lazy chunk with findBy*/waitFor.

describe('MarkdownRenderer', () => {
  it('renders plain text', async () => {
    render(<MarkdownRenderer content="Hello world" />);
    expect(await screen.findByText('Hello world')).toBeInTheDocument();
  });

  it('renders bold text', async () => {
    render(<MarkdownRenderer content="**bold**" />);
    expect(await screen.findByText('bold')).toBeInTheDocument();
  });

  it('renders headings', async () => {
    render(<MarkdownRenderer content="# Heading 1" />);
    expect(await screen.findByText('Heading 1')).toBeInTheDocument();
  });

  it('renders lists', async () => {
    render(<MarkdownRenderer content="- Item 1\n- Item 2" />);
    expect(await screen.findByText((content) => content.includes('Item 1'))).toBeInTheDocument();
    expect(screen.getByText((content) => content.includes('Item 2'))).toBeInTheDocument();
  });

  it('renders code blocks', async () => {
    render(<MarkdownRenderer content={'```javascript\nconst x = 1;\n```'} />);
    // Check for code block structure rather than exact text due to syntax highlighter
    await waitFor(() => expect(document.querySelector('pre')).toBeInTheDocument());
    expect(document.querySelector('code')).toBeInTheDocument();
  });

  it('renders inline code', async () => {
    render(<MarkdownRenderer content="`code`" />);
    expect(await screen.findByText('code')).toBeInTheDocument();
  });

  it('renders links', async () => {
    render(<MarkdownRenderer content="[link](https://example.com)" />);
    const link = await screen.findByText('link');
    expect(link).toHaveAttribute('href', 'https://example.com');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('renders blockquotes', async () => {
    render(<MarkdownRenderer content="> quote" />);
    expect(await screen.findByText('quote')).toBeInTheDocument();
  });

  it('renders tables', async () => {
    render(<MarkdownRenderer content="| A | B |\n|---|---|\n| 1 | 2 |" />);
    // Table content may be split across multiple elements, so use a more flexible matcher
    expect(await screen.findByText((content) => content.includes('A'))).toBeInTheDocument();
    expect(screen.getByText((content) => content.includes('1'))).toBeInTheDocument();
  });

  it('sanitizes dangerous HTML', async () => {
    render(<MarkdownRenderer content="Safe text<script>alert('xss')</script>" />);
    // Wait for the lazy block to mount before asserting the script was stripped.
    await screen.findByText((content) => content.includes('Safe text'));
    expect(screen.queryByText("alert('xss')")).not.toBeInTheDocument();
  });

  it('renders inline latex wrapped with \\( \\)', async () => {
    const { container } = render(<MarkdownRenderer content={"Euler: \\(e^{i\\pi}+1=0\\)"} />);
    await waitFor(() => expect(container.querySelector('.katex')).toBeInTheDocument());
  });

  it('renders block latex wrapped with \\[ \\]', async () => {
    const { container } = render(<MarkdownRenderer content={"\\[\n\\int_0^1 x^2 dx\n\\]"} />);
    await waitFor(() => expect(container.querySelector('.katex-display')).toBeInTheDocument());
  });

  it('does not parse latex delimiters inside code fences', async () => {
    const { container } = render(<MarkdownRenderer content={"```tex\n\\(x+y\\)\n```"} />);
    expect(await screen.findByText('\\(x+y\\)')).toBeInTheDocument();
    expect(container.querySelector('.katex')).not.toBeInTheDocument();
  });

  it('renders inline latex wrapped with single dollars', async () => {
    const { container } = render(<MarkdownRenderer content={'Area: $S = \\pi r^2$'} />);
    await waitFor(() => expect(container.querySelector('.katex')).toBeInTheDocument());
  });

  it('renders block latex wrapped with double dollars', async () => {
    const { container } = render(<MarkdownRenderer content={'$$\nE = mc^2\n$$'} />);
    await waitFor(() => expect(container.querySelector('.katex-display')).toBeInTheDocument());
  });

  it('renders bare align environments as display math', async () => {
    const { container } = render(
      <MarkdownRenderer content={'\\begin{align}\na &= b \\\\\nc &= d\n\\end{align}'} />,
    );
    await waitFor(() => expect(container.querySelector('.katex-display')).toBeInTheDocument());
  });

  it('renders latex inside table cells', async () => {
    const { container } = render(
      <MarkdownRenderer content={'| 图形 | 公式 |\n|---|---|\n| 圆 | \\(S = \\pi r^2\\) |'} />,
    );
    await waitFor(() => expect(container.querySelector('table .katex')).toBeInTheDocument());
  });
});
