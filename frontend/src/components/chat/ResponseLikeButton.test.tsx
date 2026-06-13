import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ResponseLikeButton from "./ResponseLikeButton";

const likeResponse = vi.fn();
vi.mock("@/lib/api/auth", () => ({ getToken: () => "token" }));
vi.mock("@/lib/api/reactions", () => ({
  likeResponse: (...args: unknown[]) => likeResponse(...args),
  unlikeResponse: vi.fn(),
}));

describe("ResponseLikeButton", () => {
  beforeEach(() => likeResponse.mockReset());

  it("is disabled until a persisted response identity exists", () => {
    render(<ResponseLikeButton />);
    expect(screen.getByRole("button")).toBeDisabled();
  });

  it("updates count and pressed state after a successful like", async () => {
    likeResponse.mockResolvedValue({ responseId: "r1", likeCount: 2, likedByMe: true });
    render(<ResponseLikeButton responseId="r1" initialCount={1} />);
    fireEvent.click(screen.getByRole("button"));
    await waitFor(() => expect(screen.getByRole("button")).toHaveAttribute("aria-pressed", "true"));
    expect(screen.getByText("2")).toBeInTheDocument();
  });
});
