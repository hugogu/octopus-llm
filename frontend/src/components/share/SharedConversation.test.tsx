import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import SharedConversation from "./SharedConversation";

const api = vi.hoisted(() => ({
  getSharedSession: vi.fn(),
  anonymousLike: vi.fn(),
  sharedNamedLike: vi.fn(),
}));
const auth = vi.hoisted(() => ({ token: null as string | null }));
vi.mock("@/lib/api/auth", () => ({ getToken: () => auth.token }));
vi.mock("@/lib/api/shares", () => api);

function sessionWith(overrides: Record<string, unknown>) {
  return {
    title: "Shared",
    turns: [{
      sequenceNum: 1,
      promptText: "hello",
      responses: [{
        responseId: "r1", modelDisplayName: "Model", status: "complete",
        responseText: "world", reasoningText: null, errorMessage: null,
        namedLikeCount: 3, likedByMe: false,
        anonymousLikeCount: 1, likedByThisVisitor: false,
        ...overrides,
      }],
    }],
  };
}

afterEach(() => {
  auth.token = null;
  vi.clearAllMocks();
});

describe("SharedConversation", () => {
  it("shows named loves and anonymous thumbs separately and never leaks identity", async () => {
    auth.token = null; // anonymous visitor
    api.getSharedSession.mockResolvedValue(sessionWith({}));
    api.anonymousLike.mockResolvedValue({ responseId: "r1", anonymousLikeCount: 2, likedByThisVisitor: true });
    render(<SharedConversation shareToken="opaque" />);
    expect(await screen.findByText("Shared")).toBeInTheDocument();
    expect(screen.queryByText(/userId|clientIp|connectionId/)).not.toBeInTheDocument();

    // The love count from chat is visible (3), and the love button is disabled for anonymous visitors.
    const loves = screen.getByRole("button", { name: "Loves" });
    expect(loves).toHaveTextContent("3");
    expect(loves).toBeDisabled();

    // The anonymous thumb is the interactive control for a signed-out visitor.
    fireEvent.click(screen.getByRole("button", { name: "Anonymous thumbs up" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Anonymous thumbs up" })).toHaveTextContent("2"));
  });

  it("lets a signed-in viewer toggle a named love", async () => {
    auth.token = "viewer-token";
    api.getSharedSession.mockResolvedValue(sessionWith({}));
    api.sharedNamedLike.mockResolvedValue({ responseId: "r1", likeCount: 4, likedByMe: true });
    render(<SharedConversation shareToken="opaque" />);
    const loves = await screen.findByRole("button", { name: "Loves" });
    expect(loves).toBeEnabled();
    fireEvent.click(loves);
    await waitFor(() => expect(screen.getByRole("button", { name: "Loves" })).toHaveTextContent("4"));
    expect(api.sharedNamedLike).toHaveBeenCalledWith("opaque", "r1", "viewer-token", true);
  });

  it("lets a signed-in viewer also give an anonymous thumb", async () => {
    auth.token = "viewer-token";
    api.getSharedSession.mockResolvedValue(sessionWith({}));
    api.anonymousLike.mockResolvedValue({ responseId: "r1", anonymousLikeCount: 2, likedByThisVisitor: true });
    render(<SharedConversation shareToken="opaque" />);
    const thumb = await screen.findByRole("button", { name: "Anonymous thumbs up" });
    expect(thumb).toBeEnabled();
    fireEvent.click(thumb);
    await waitFor(() => expect(screen.getByRole("button", { name: "Anonymous thumbs up" })).toHaveTextContent("2"));
    expect(api.anonymousLike).toHaveBeenCalledWith("opaque", "r1");
  });
});
