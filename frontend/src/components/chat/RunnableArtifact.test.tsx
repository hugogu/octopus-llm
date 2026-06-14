import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import RunnableArtifact from './RunnableArtifact';

describe('RunnableArtifact', () => {
  it('runs in a sandboxed iframe without allow-same-origin (SC-004)', () => {
    const { container } = render(<RunnableArtifact html="<h1>artifact</h1>" />);
    const iframe = container.querySelector('iframe');
    expect(iframe).not.toBeNull();

    const sandbox = iframe?.getAttribute('sandbox') ?? '';
    expect(sandbox).toContain('allow-scripts');
    // The isolation guarantee: no same-origin access to host cookies/storage/token.
    expect(sandbox).not.toContain('allow-same-origin');

    expect(iframe?.getAttribute('srcdoc')).toContain('<h1>artifact</h1>');
  });
});
