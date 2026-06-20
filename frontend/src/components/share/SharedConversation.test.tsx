import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import SharedConversation from "./SharedConversation";

const api = vi.hoisted(() => ({
  getSharedSession: vi.fn(),
  anonymousLike: vi.fn(),
  sharedNamedLike: vi.fn(),
  importSharedSession: vi.fn(),
  newShareImportKey: vi.fn(() => "stable-import-key"),
}));
const auth = vi.hoisted(() => ({ token: null as string | null }));
const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ getToken: () => auth.token }));
vi.mock("@/lib/api/shares", () => api);
vi.mock("next/navigation", () => ({ useRouter: () => navigation }));

function sessionWith(overrides: Record<string, unknown>) {
  return {
    title: "Shared",
    scope: "public",
    canImport: true,
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
  api.newShareImportKey.mockReturnValue("stable-import-key");
});

describe("SharedConversation", () => {
  it("shows named loves and anonymous thumbs separately and never leaks identity", async () => {
    auth.token = null; // anonymous visitor
    api.getSharedSession.mockResolvedValue(sessionWith({}));
    api.anonymousLike.mockResolvedValue({ responseId: "r1", anonymousLikeCount: 2, likedByThisVisitor: true });
    render(<SharedConversation shareToken="opaque" />);
    // Title appears in the visible header and again in the off-screen (aria-hidden) export poster.
    expect((await screen.findAllByText("Shared")).length).toBeGreaterThan(0);
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

  it("preserves the share token and idempotency key while sending an anonymous viewer to sign in", async () => {
    api.getSharedSession.mockResolvedValue(sessionWith({}));
    render(<SharedConversation shareToken="opaque" />);
    fireEvent.click(await screen.findByRole("button", { name: "Sign in to import" }));
    expect(navigation.push).toHaveBeenCalledWith(
      "/login?returnTo=%2Fshare%2Fopaque%3Fimport%3Dstable-import-key",
    );
    expect(api.importSharedSession).not.toHaveBeenCalled();
  });

  it("imports for a signed-in viewer and navigates to the new Quest", async () => {
    auth.token = "viewer-token";
    api.getSharedSession.mockResolvedValue(sessionWith({}));
    api.importSharedSession.mockResolvedValue({
      sessionId: "new-session",
      title: "Shared",
      importedFromLabel: "Imported from a shared Quest",
    });
    render(<SharedConversation shareToken="opaque" />);
    fireEvent.click(await screen.findByRole("button", { name: "Import Quest" }));
    await waitFor(() => expect(api.importSharedSession).toHaveBeenCalledWith(
      "opaque",
      "stable-import-key",
      "viewer-token",
    ));
    expect(navigation.push).toHaveBeenCalledWith("/chat?session=new-session");
  });

  it("reuses the same key when a resumed import is retried after an error", async () => {
    auth.token = "viewer-token";
    window.history.replaceState({}, "", "/share/opaque?import=resumed-key");
    api.getSharedSession.mockResolvedValue(sessionWith({}));
    api.importSharedSession.mockRejectedValueOnce(new Error("Temporary failure"));
    render(<SharedConversation shareToken="opaque" />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Temporary failure");
    expect(api.importSharedSession).toHaveBeenCalledWith("opaque", "resumed-key", "viewer-token");
  });

  it("renders no Quest content when the share API requires authentication", async () => {
    api.getSharedSession.mockRejectedValue(Object.assign(new Error("Authentication required"), { status: 401 }));
    render(<SharedConversation shareToken="opaque" />);
    expect(await screen.findByText("Sign in to view this Quest")).toBeInTheDocument();
    expect(screen.queryByText("hello")).not.toBeInTheDocument();
    expect(screen.queryByText("world")).not.toBeInTheDocument();
    expect(screen.queryByText("Shared")).not.toBeInTheDocument();
  });
});
