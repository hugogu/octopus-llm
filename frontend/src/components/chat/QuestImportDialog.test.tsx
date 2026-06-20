import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuestImportDialog, { parseShareToken } from "./QuestImportDialog";

const api = vi.hoisted(() => ({
  importSharedSession: vi.fn(),
  newShareImportKey: vi.fn(() => "stable-key"),
}));
const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ getToken: () => "auth-token" }));
vi.mock("@/lib/api/shares", () => api);
vi.mock("next/navigation", () => ({ useRouter: () => navigation }));

afterEach(() => vi.clearAllMocks());

describe("QuestImportDialog", () => {
  it("accepts a local share link or opaque token and rejects foreign links", () => {
    expect(parseShareToken("opaque_token-1")).toBe("opaque_token-1");
    expect(parseShareToken("/share/opaque-token")).toBe("opaque-token");
    expect(parseShareToken("https://foreign.example/share/token")).toBeNull();
  });

  it("imports with one stable key and navigates to the created Quest", async () => {
    api.importSharedSession.mockResolvedValue({
      sessionId: "created",
      title: "Imported",
      importedFromLabel: "Imported from a shared Quest",
    });
    render(<QuestImportDialog isOpen onClose={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Share link or token"), { target: { value: "/share/opaque" } });
    fireEvent.click(screen.getByRole("button", { name: "Import Quest" }));
    await waitFor(() => expect(api.importSharedSession).toHaveBeenCalledWith("opaque", "stable-key", "auth-token"));
    expect(navigation.push).toHaveBeenCalledWith("/chat?session=created");
  });

  it("reuses the key when retrying the same failed submission", async () => {
    api.importSharedSession
      .mockRejectedValueOnce(new Error("Temporary failure"))
      .mockResolvedValueOnce({ sessionId: "created", title: null, importedFromLabel: "Imported from a shared Quest" });
    render(<QuestImportDialog isOpen onClose={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Share link or token"), { target: { value: "opaque" } });
    fireEvent.click(screen.getByRole("button", { name: "Import Quest" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Temporary failure");
    fireEvent.click(screen.getByRole("button", { name: "Import Quest" }));
    await waitFor(() => expect(api.importSharedSession).toHaveBeenCalledTimes(2));
    expect(api.importSharedSession.mock.calls[0]?.[1]).toBe("stable-key");
    expect(api.importSharedSession.mock.calls[1]?.[1]).toBe("stable-key");
    expect(api.newShareImportKey).toHaveBeenCalledTimes(1);
  });
});
