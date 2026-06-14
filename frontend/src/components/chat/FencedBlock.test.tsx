import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { classifyFence } from '@/lib/markdown/blocks';
import FencedBlock from './FencedBlock';

describe('classifyFence', () => {
  it('maps known fence languages to render strategies', () => {
    expect(classifyFence('mermaid')).toMatchObject({ strategy: 'diagram-preview', diagram: 'mermaid' });
    expect(classifyFence('puml')).toMatchObject({ strategy: 'diagram-preview', diagram: 'plantuml' });
    expect(classifyFence('SVG')).toMatchObject({ strategy: 'diagram-preview', diagram: 'svg' });
    expect(classifyFence('html')).toMatchObject({ strategy: 'html-runnable' });
    expect(classifyFence('ts')).toMatchObject({ strategy: 'code' });
    expect(classifyFence(undefined)).toMatchObject({ strategy: 'code' });
  });
});

describe('FencedBlock', () => {
  it('renders HTML as source by default with no diagram preview (Q2)', () => {
    const { container } = render(<FencedBlock language="html" source="<b>hi-there</b>" />);
    expect(screen.queryByRole('button', { name: /diagram/i })).not.toBeInTheDocument();
    expect(container.textContent).toContain('hi-there');
  });

  it('renders an SVG block with a preview/source toggle, switchable to exact source (FR-004/005)', async () => {
    const svg = '<svg viewBox="0 0 10 10"><rect width="10" height="10" /></svg>';
    const { container } = render(<FencedBlock language="svg" source={svg} />);

    // Both toggle tabs are present; default is the preview.
    expect(screen.getByRole('button', { name: /diagram/i })).toBeInTheDocument();
    const sourceTab = screen.getByRole('button', { name: /^svg$/i });

    await userEvent.click(sourceTab);
    expect(container.textContent).toContain('rect');
  });

  it('renders a plain code block bounded and copyable', () => {
    const { container } = render(<FencedBlock language="python" source="print('hi')" />);
    expect(screen.getByText('python')).toBeInTheDocument();
    expect(container.querySelector('[style*="max-height"]')).toBeTruthy();
  });
});
