import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PasswordChangeForm from "./PasswordChangeForm";

const changePassword = vi.fn();
vi.mock("@/lib/api/auth", () => ({ getToken: () => "old-token" }));
vi.mock("@/lib/api/account", () => ({ changePassword: (...args: unknown[]) => changePassword(...args) }));

describe("PasswordChangeForm", () => {
  it("validates matching passwords and reports replacement success", async () => {
    changePassword.mockResolvedValue({ status: "password_updated", token: "new", expiresAt: "2026-06-14T00:00:00Z" });
    render(<PasswordChangeForm />);
    fireEvent.change(screen.getByLabelText("Current password"), { target: { value: "OldPassword123!" } });
    fireEvent.change(screen.getByLabelText("New password"), { target: { value: "NewPassword123!" } });
    fireEvent.change(screen.getByLabelText("Confirm new password"), { target: { value: "NewPassword123!" } });
    fireEvent.click(screen.getByRole("button", { name: "Update password" }));
    await waitFor(() => expect(changePassword).toHaveBeenCalledWith("old-token", "OldPassword123!", "NewPassword123!"));
    expect(screen.getByText(/Other signed-in sessions/)).toBeInTheDocument();
  });
});
