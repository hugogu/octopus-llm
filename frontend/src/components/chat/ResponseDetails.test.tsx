import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ResponseDetails from './ResponseDetails';

describe('ResponseDetails', () => {
  it('shows latency, tokens, and cache figures when opened', async () => {
    render(
      <ResponseDetails
        latencyMs={1500}
        inputTokens={120}
        outputTokens={45}
        cacheReadTokens={1024}
        cacheWriteTokens={256}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /response details/i }));

    expect(screen.getByText('1.50s')).toBeInTheDocument();
    expect(screen.getByText('120')).toBeInTheDocument();
    expect(screen.getByText('45')).toBeInTheDocument();
    expect(screen.getByText('1,024')).toBeInTheDocument();
    expect(screen.getByText('256')).toBeInTheDocument();
  });

  it('renders "—" for cache figures the provider did not report (FR-014)', async () => {
    render(
      <ResponseDetails
        latencyMs={300}
        inputTokens={10}
        outputTokens={5}
        cacheReadTokens={null}
        cacheWriteTokens={null}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /response details/i }));

    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(2);
  });
});
