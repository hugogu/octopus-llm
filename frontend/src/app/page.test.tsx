import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import Home from "./page";

const { cookies, redirect } = vi.hoisted(() => ({
  cookies: vi.fn(),
  redirect: vi.fn(),
}));

vi.mock("next/headers", () => ({ cookies }));
vi.mock("next/navigation", () => ({ redirect }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: ReactNode }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

describe("Home", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    cookies.mockResolvedValue({ get: () => undefined });
  });

  it("offers anonymous chat from the landing page", async () => {
    render(await Home());

    expect(screen.getByRole("link", { name: "Continue as guest" })).toHaveAttribute("href", "/chat");
    expect(redirect).not.toHaveBeenCalled();
  });
});
