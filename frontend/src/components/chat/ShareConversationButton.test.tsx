import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ShareConversationButton from "./ShareConversationButton";

const api = vi.hoisted(() => ({
  createShare: vi.fn(),
  listShares: vi.fn(),
  revokeShare: vi.fn(),
}));
vi.mock("@/lib/api/auth", () => ({ getToken: () => "token" }));
vi.mock("@/lib/api/shares", () => api);

describe("ShareConversationButton", () => {
  it("creates, copies, lists, and revokes an active share", async () => {
    api.listShares.mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    api.createShare.mockResolvedValue({
      token: "opaque", shareUrl: "/share/opaque", createdAt: "2026-06-13T00:00:00Z", revokedAt: null,
    });
    api.revokeShare.mockResolvedValue(undefined);
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    render(<ShareConversationButton sessionId="s1" />);
    fireEvent.click(screen.getByRole("button", { name: "Share" }));
    fireEvent.click(screen.getByRole("button", { name: "Create and copy link" }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/share/opaque`));
    fireEvent.click(await screen.findByRole("button", { name: "Revoke" }));
    await waitFor(() => expect(api.revokeShare).toHaveBeenCalledWith("s1", "opaque", "token"));
  });
});
