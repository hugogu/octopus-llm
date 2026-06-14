import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CodeBlock from './CodeBlock';

describe('CodeBlock', () => {
  it('shows the language label and a copy control', () => {
    render(<CodeBlock code="const x = 1;" language="javascript" />);
    expect(screen.getByText('javascript')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /copy/i })).toBeInTheDocument();
  });

  it('caps the block height with an internal scroll container (FR-001)', () => {
    const { container } = render(<CodeBlock code={'line\n'.repeat(200)} language="text" />);
    const scroll = container.querySelector('[style*="max-height"]');
    expect(scroll).toBeTruthy();
  });

  it('copies exactly this block raw text (FR-003)', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    render(<CodeBlock code="payload-123" language="text" />);
    await userEvent.click(screen.getByRole('button', { name: /copy/i }));

    expect(writeText).toHaveBeenCalledWith('payload-123');
  });
});
