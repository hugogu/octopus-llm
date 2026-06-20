import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ResponseGroup, { type ResponsePanelData } from "./ResponseGroup";

const likeResponse = vi.fn();
const unlikeResponse = vi.fn();
const confirm = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/auth", () => ({ getToken: () => "token" }));
vi.mock("@/lib/api/reactions", () => ({
  likeResponse: (...args: unknown[]) => likeResponse(...args),
  unlikeResponse: (...args: unknown[]) => unlikeResponse(...args),
}));
vi.mock("@/lib/ui/confirm", () => ({ confirmDialog: confirm }));

const panel = (over: Partial<ResponsePanelData> & { key: string }): ResponsePanelData => ({
  modelId: over.key,
  text: "",
  status: "complete",
  ...over,
});

describe("ResponseGroup", () => {
  beforeEach(() => {
    likeResponse.mockReset();
    unlikeResponse.mockReset();
    confirm.mockReset();
  });

  it("makes likes mutually exclusive within the group", async () => {
    likeResponse.mockResolvedValue({ responseId: "b", likeCount: 1, likedByMe: true });
    unlikeResponse.mockResolvedValue({ responseId: "a", likeCount: 0, likedByMe: false });

    render(
      <ResponseGroup
        panels={[
          panel({ key: "a", displayName: "Alpha", responseId: "a", likeCount: 1, likedByMe: true }),
          panel({ key: "b", displayName: "Bravo", responseId: "b", likeCount: 0, likedByMe: false }),
        ]}
      />,
    );

    // Click the not-yet-liked response (Bravo).
    fireEvent.click(screen.getByRole("button", { name: "Like response" }));

    await waitFor(() => expect(likeResponse).toHaveBeenCalledWith("b", "token"));
    // The previously-liked sibling (Alpha) is un-liked automatically.
    expect(unlikeResponse).toHaveBeenCalledWith("a", "token");
  });

  it("maximizes one panel and collapses the others into chips", () => {
    render(
      <ResponseGroup
        panels={[
          panel({ key: "a", displayName: "Alpha", responseId: "a" }),
          panel({ key: "b", displayName: "Bravo", responseId: "b" }),
        ]}
      />,
    );

    fireEvent.click(screen.getAllByRole("button", { name: "Maximize this response" })[0]!);

    // The other panel collapses to a chip (labelled by its model name) that can re-maximize it.
    expect(screen.getByRole("button", { name: "Bravo" })).toBeInTheDocument();
    // The maximized panel exposes a restore control.
    expect(screen.getByRole("button", { name: "Restore side-by-side" })).toBeInTheDocument();
  });

  it("deletes one response after confirmation and keeps its sibling", async () => {
    confirm.mockResolvedValue(true);
    const removeAlpha = vi.fn().mockResolvedValue(undefined);
    render(
      <ResponseGroup
        panels={[
          panel({ key: "a", displayName: "Alpha", responseId: "a", text: "alpha", onDelete: removeAlpha }),
          panel({ key: "b", displayName: "Bravo", responseId: "b", text: "bravo" }),
        ]}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Delete response" }));
    await waitFor(() => expect(removeAlpha).toHaveBeenCalledTimes(1));
    expect(screen.queryByText("Alpha")).not.toBeInTheDocument();
    expect(screen.getByText("Bravo")).toBeInTheDocument();
  });
});
