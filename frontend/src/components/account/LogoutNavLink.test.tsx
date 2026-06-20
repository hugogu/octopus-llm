import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import LogoutNavLink from "./LogoutNavLink";

const pushMock = vi.fn();
const logoutMock = vi.fn();

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: pushMock }) }));
vi.mock("@/lib/api/auth", () => ({ logout: (...args: unknown[]) => logoutMock(...args) }));

afterEach(() => {
  pushMock.mockClear();
  logoutMock.mockClear();
});

describe("LogoutNavLink", () => {
  it("revokes the session and redirects to /login", async () => {
    logoutMock.mockResolvedValueOnce(undefined);
    render(<LogoutNavLink />);

    await userEvent.click(screen.getByRole("button", { name: /Log out/i }));

    expect(logoutMock).toHaveBeenCalledTimes(1);
    expect(pushMock).toHaveBeenCalledWith("/login");
  });

  it("redirects even if the logout request fails", async () => {
    logoutMock.mockRejectedValueOnce(new Error("network"));
    render(<LogoutNavLink />);

    await userEvent.click(screen.getByRole("button", { name: /Log out/i }));

    expect(pushMock).toHaveBeenCalledWith("/login");
  });
});
