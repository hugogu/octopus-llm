import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import SharedConversation from "./SharedConversation";

const api = vi.hoisted(() => ({
  getSharedSession: vi.fn(),
  anonymousLike: vi.fn(),
  sharedNamedLike: vi.fn(),
}));
vi.mock("@/lib/api/auth", () => ({ getToken: () => null }));
vi.mock("@/lib/api/shares", () => api);

describe("SharedConversation", () => {
  it("renders only the anonymous-safe response and updates anonymous like state", async () => {
    api.getSharedSession.mockResolvedValue({
      title: "Shared",
      turns: [{
        sequenceNum: 1,
        promptText: "hello",
        responses: [{
          responseId: "r1", modelDisplayName: "Model", status: "complete",
          responseText: "world", reasoningText: null, errorMessage: null,
          anonymousLikeCount: 1, likedByThisVisitor: false,
        }],
      }],
    });
    api.anonymousLike.mockResolvedValue({ responseId: "r1", anonymousLikeCount: 2, likedByThisVisitor: true });
    render(<SharedConversation shareToken="opaque" />);
    expect(await screen.findByText("Shared")).toBeInTheDocument();
    expect(screen.queryByText(/userId|clientIp|connectionId/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button"));
    await waitFor(() => expect(screen.getByText("2")).toBeInTheDocument());
  });
});
