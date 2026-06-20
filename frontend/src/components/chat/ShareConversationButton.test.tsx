import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ShareConversationButton from "./ShareConversationButton";

const api = vi.hoisted(() => ({
  createShare: vi.fn(),
  changeShareScope: vi.fn(),
  listShares: vi.fn(),
  revokeShare: vi.fn(),
}));
vi.mock("@/lib/api/auth", () => ({ getToken: () => "token" }));
vi.mock("@/lib/api/shares", () => api);
const confirm = vi.hoisted(() => vi.fn());
vi.mock("@/lib/ui/confirm", () => ({ confirmDialog: confirm }));

beforeEach(() => vi.clearAllMocks());

describe("ShareConversationButton", () => {
  it("creates, copies, lists, and revokes an active share", async () => {
    api.listShares.mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    api.createShare.mockResolvedValue({
      token: "opaque", shareUrl: "/share/opaque", scope: "authenticated",
      createdAt: "2026-06-13T00:00:00Z", revokedAt: null,
    });
    api.revokeShare.mockResolvedValue(undefined);
    confirm.mockResolvedValue(true);
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    render(<ShareConversationButton sessionId="s1" />);
    fireEvent.click(screen.getByRole("button", { name: "Share" }));
    fireEvent.click(screen.getByRole("button", { name: "Create and copy link" }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/share/opaque`));
    expect(api.createShare).toHaveBeenCalledWith("s1", "token", "authenticated");
    fireEvent.click(await screen.findByRole("button", { name: "Revoke" }));
    await waitFor(() => expect(confirm).toHaveBeenCalled());
    await waitFor(() => expect(api.revokeShare).toHaveBeenCalledWith("s1", "opaque", "token"));
  });

  it("changes an active share scope before copying and can cancel revoke", async () => {
    const active = {
      token: "opaque", shareUrl: "/share/opaque", scope: "authenticated" as const,
      createdAt: "2026-06-13T00:00:00Z", revokedAt: null,
    };
    api.listShares.mockResolvedValue({ items: [active], page: 0, size: 25, totalElements: 1, totalPages: 1 });
    api.changeShareScope.mockResolvedValue({ ...active, scope: "public" });
    confirm.mockResolvedValue(false);
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
    render(<ShareConversationButton sessionId="s1" />);
    fireEvent.click(screen.getByRole("button", { name: "Share" }));
    fireEvent.click(await screen.findByLabelText("Public — anyone with the link"));
    fireEvent.click(screen.getByRole("button", { name: "Update and copy link" }));
    await waitFor(() => expect(api.changeShareScope).toHaveBeenCalledWith("s1", "opaque", "public", "token"));
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    await waitFor(() => expect(confirm).toHaveBeenCalled());
    expect(api.revokeShare).not.toHaveBeenCalled();
  });
});
