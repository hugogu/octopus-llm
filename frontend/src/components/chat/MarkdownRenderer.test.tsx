import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import MarkdownRenderer from './MarkdownRenderer';

describe('MarkdownRenderer', () => {
  it('renders plain text', () => {
    render(<MarkdownRenderer content="Hello world" />);
    expect(screen.getByText('Hello world')).toBeInTheDocument();
  });

  it('renders bold text', () => {
    render(<MarkdownRenderer content="**bold**" />);
    expect(screen.getByText('bold')).toBeInTheDocument();
  });

  it('renders headings', () => {
    render(<MarkdownRenderer content="# Heading 1" />);
    expect(screen.getByText('Heading 1')).toBeInTheDocument();
  });

  it('renders lists', () => {
    render(<MarkdownRenderer content="- Item 1\n- Item 2" />);
    expect(screen.getByText((content) => content.includes('Item 1'))).toBeInTheDocument();
    expect(screen.getByText((content) => content.includes('Item 2'))).toBeInTheDocument();
  });

  it('renders code blocks', () => {
    render(<MarkdownRenderer content={'```javascript\nconst x = 1;\n```'} />);
    // Check for code block structure rather than exact text due to syntax highlighter
    expect(document.querySelector('pre')).toBeInTheDocument();
    expect(document.querySelector('code')).toBeInTheDocument();
  });

  it('renders inline code', () => {
    render(<MarkdownRenderer content="`code`" />);
    expect(screen.getByText('code')).toBeInTheDocument();
  });

  it('renders links', () => {
    render(<MarkdownRenderer content="[link](https://example.com)" />);
    const link = screen.getByText('link');
    expect(link).toHaveAttribute('href', 'https://example.com');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('renders blockquotes', () => {
    render(<MarkdownRenderer content="> quote" />);
    expect(screen.getByText('quote')).toBeInTheDocument();
  });

  it('renders tables', () => {
    render(<MarkdownRenderer content="| A | B |\n|---|---|\n| 1 | 2 |" />);
    // Table content may be split across multiple elements, so use a more flexible matcher
    expect(screen.getByText((content) => content.includes('A'))).toBeInTheDocument();
    expect(screen.getByText((content) => content.includes('1'))).toBeInTheDocument();
  });

  it('sanitizes dangerous HTML', () => {
    render(<MarkdownRenderer content="<script>alert('xss')</script>" />);
    expect(screen.queryByText("alert('xss')")).not.toBeInTheDocument();
  });
});
