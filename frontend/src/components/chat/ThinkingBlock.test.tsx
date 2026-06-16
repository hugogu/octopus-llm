import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ThinkingBlock from './ThinkingBlock';

describe('ThinkingBlock', () => {
  it('renders nothing when reasoning is empty', () => {
    const { container } = render(<ThinkingBlock reasoning="" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows a collapsed toggle by default', () => {
    render(<ThinkingBlock reasoning="step 1: think" />);
    expect(screen.getByText('Thought process')).toBeInTheDocument();
    expect(screen.queryByText(/step 1/)).not.toBeInTheDocument();
  });

  it('expands content on click', async () => {
    render(<ThinkingBlock reasoning="step 1: think" />);
    fireEvent.click(screen.getByText('Thought process'));
    // Reasoning renders through the lazily-loaded MarkdownRenderer.
    expect(await screen.findByText(/step 1/)).toBeInTheDocument();
  });

  it('auto-opens while streaming', async () => {
    render(<ThinkingBlock reasoning="step 1: think" autoOpen />);
    expect(await screen.findByText(/step 1/)).toBeInTheDocument();
  });

  it('user collapse wins over autoOpen', () => {
    render(<ThinkingBlock reasoning="step 1: think" autoOpen />);
    fireEvent.click(screen.getByText('Thought process'));
    expect(screen.queryByText(/step 1/)).not.toBeInTheDocument();
  });
});
