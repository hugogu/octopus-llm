import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ProfileForm from "./ProfileForm";

const api = vi.hoisted(() => ({
  getAccount: vi.fn(),
  updateProfile: vi.fn(),
  resendVerification: vi.fn(),
}));
vi.mock("@/lib/api/auth", () => ({ getToken: () => "token" }));
vi.mock("@/lib/api/account", () => api);

describe("ProfileForm", () => {
  it("loads an unverified profile, clears the name, and resends verification", async () => {
    const account = {
      id: "u1", email: "user@example.com", displayName: "Ada", emailVerified: false,
      emailVerificationStatus: "pending", isAdmin: false, isActive: true,
    };
    api.getAccount.mockResolvedValue(account);
    api.updateProfile.mockResolvedValue({ ...account, displayName: null });
    api.resendVerification.mockResolvedValue({ status: "verification_sent" });
    render(<ProfileForm />);

    const input = await screen.findByLabelText("Display name");
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save profile" }));
    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith("token", null));
    fireEvent.click(screen.getByRole("button", { name: "Resend verification" }));
    await waitFor(() => expect(screen.getByText("Verification email sent.")).toBeInTheDocument());
  });
});
