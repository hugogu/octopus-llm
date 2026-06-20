import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import UserMenu from "./UserMenu";
import type { MeResponse } from "@/lib/types/api";

const pushMock = vi.fn();
const logoutMock = vi.fn();
const getMeMock = vi.fn();

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));
vi.mock("@/lib/api/auth", () => ({
  getToken: () => "token",
  logout: (...args: unknown[]) => logoutMock(...args),
}));
vi.mock("@/lib/api/admin", () => ({ getMe: (...args: unknown[]) => getMeMock(...args) }));

const me = (overrides: Partial<MeResponse> = {}): MeResponse => ({
  id: "u1",
  email: "alice@example.com",
  displayName: "Alice",
  emailVerified: true,
  emailVerificationStatus: "verified",
  isAdmin: false,
  isActive: true,
  ...overrides,
});

beforeEach(() => {
  pushMock.mockReset();
  logoutMock.mockReset();
  getMeMock.mockReset();
});
afterEach(() => {
  vi.clearAllMocks();
});

describe("UserMenu", () => {
  it("shows the display name as the trigger label", async () => {
    getMeMock.mockResolvedValueOnce(me({ displayName: "Alice" }));
    render(<UserMenu />);
    await screen.findByRole("button", { name: /Alice/ });
  });

  it("falls back to email when there is no display name", async () => {
    getMeMock.mockResolvedValueOnce(me({ displayName: null }));
    render(<UserMenu />);
    await screen.findByText("alice@example.com");
  });

  it("lists personal center and analytics, and shows admin only for admins", async () => {
    getMeMock.mockResolvedValueOnce(me({ isAdmin: false }));
    render(<UserMenu />);
    await screen.findByRole("button", { name: /Alice/ });

    await userEvent.click(screen.getByRole("button", { name: /Alice/ }));
    expect(screen.getByRole("menuitem", { name: /Personal center/ })).toHaveAttribute("href", "/account");
    expect(screen.getByRole("menuitem", { name: /Public analytics/ })).toHaveAttribute("href", "/analytics");
    expect(screen.queryByRole("menuitem", { name: /Admin panel/ })).toBeNull();
  });

  it("shows the admin panel entry for administrators", async () => {
    getMeMock.mockResolvedValueOnce(me({ isAdmin: true }));
    render(<UserMenu />);
    await screen.findByRole("button", { name: /Alice/ });

    await userEvent.click(screen.getByRole("button", { name: /Alice/ }));
    expect(screen.getByRole("menuitem", { name: /Admin panel/ })).toHaveAttribute("href", "/admin");
  });

  it("logs out and redirects to /login", async () => {
    logoutMock.mockResolvedValueOnce(undefined);
    getMeMock.mockResolvedValueOnce(me());
    render(<UserMenu />);
    await screen.findByRole("button", { name: /Alice/ });

    await userEvent.click(screen.getByRole("button", { name: /Alice/ }));
    await userEvent.click(screen.getByRole("menuitem", { name: /Log out/i }));

    expect(logoutMock).toHaveBeenCalledTimes(1);
    expect(pushMock).toHaveBeenCalledWith("/login");
  });

  it("closes the menu on outside click", async () => {
    getMeMock.mockResolvedValueOnce(me());
    render(
      <div>
        <UserMenu />
        <button>outside</button>
      </div>,
    );
    await screen.findByRole("button", { name: /Alice/ });

    await userEvent.click(screen.getByRole("button", { name: /Alice/ }));
    expect(screen.getByRole("menuitem", { name: /Personal center/ })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "outside" }));
    expect(screen.queryByRole("menuitem", { name: /Personal center/ })).toBeNull();
  });
});
