import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import AnonymousChatPage from "./AnonymousChatPage";

vi.mock("@/lib/api/anonymousChat", () => ({
  listAllAnonymousModels: vi.fn().mockResolvedValue([]),
  streamAnonymousTurn: vi.fn(),
}));

describe("AnonymousChatPage", () => {
  it("offers registration from guest mode", () => {
    render(<AnonymousChatPage />);

    expect(screen.getByRole("link", { name: "Create account" })).toHaveAttribute("href", "/register?returnTo=%2Fchat");
  });
});
