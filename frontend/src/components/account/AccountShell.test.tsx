import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AccountShell from "./AccountShell";

vi.mock("next/navigation", () => ({ usePathname: () => "/account/security" }));

describe("AccountShell", () => {
  it("marks the active personal-center section and keeps chat reachable", () => {
    render(<AccountShell><p>content</p></AccountShell>);
    expect(screen.getByRole("link", { name: /Security/ })).toHaveClass("bg-[#c96442]");
    expect(screen.getByRole("link", { name: /Back to chat/ })).toHaveAttribute("href", "/chat");
  });
});
