import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { SharedSession } from '@/lib/types/api';

vi.mock('qrcode', () => ({
  default: { toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,QR') },
}));

const toPng = vi.fn().mockResolvedValue('data:image/png;base64,IMG');
vi.mock('html-to-image', () => ({ toPng: (...args: unknown[]) => toPng(...args) }));

import ShareExportButton from './ShareExportButton';

const session: SharedSession = {
  title: 'Demo',
  scope: 'public',
  canImport: true,
  turns: [
    {
      sequenceNum: 1,
      promptText: 'hi',
      responses: [
        {
          responseId: 'r1',
          modelDisplayName: 'Model',
          status: 'complete',
          responseText: 'hello',
          reasoningText: null,
          errorMessage: null,
          inputTokens: 1,
          outputTokens: 2,
          cacheReadTokens: null,
          cacheWriteTokens: null,
          latencyMs: 5,
          namedLikeCount: 0,
          likedByMe: false,
          anonymousLikeCount: 0,
          likedByThisVisitor: false,
        },
      ],
    },
  ],
};

describe('ShareExportButton', () => {
  beforeEach(() => toPng.mockClear());

  it('generates a QR code and exports a PNG download (FR-019/020)', async () => {
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    render(<ShareExportButton session={session} />);

    await waitFor(() => expect(screen.getByAltText(/QR code/i)).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: /long image/i }));

    await waitFor(() => expect(toPng).toHaveBeenCalled(), { timeout: 2000 });
    expect(clickSpy).toHaveBeenCalled();
    clickSpy.mockRestore();
  });

  it('still renders an export for an empty conversation (FR-022)', async () => {
    render(<ShareExportButton session={{ title: null, turns: [], scope: 'public', canImport: true }} />);
    expect(screen.getByText(/no messages yet/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /long image/i })).toBeInTheDocument();
  });
});
