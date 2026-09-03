import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import AdminModelAccessPage from "./AdminModelAccessPage";

const { setAdminModelAnonymousDefault } = vi.hoisted(() => ({
  setAdminModelAnonymousDefault: vi.fn().mockResolvedValue({ isAnonymousDefault: true }),
}));

vi.mock("@/lib/api/adminModelAccess", () => ({
  listAdminModels: vi.fn().mockResolvedValue({
    items: [{
      id: "m1",
      connection: { id: "c1", label: "Platform" },
      modelId: "provider-model",
      displayName: "Public candidate",
      protocol: "openai-compatible",
      capabilities: { streaming: true, vision: false, tools: false },
      isEnabled: true,
      isAnonymousAllowed: true,
      isAnonymousDefault: false,
    }],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
  }),
  previewAdminModelBulk: vi.fn().mockResolvedValue({
    operationId: "op1",
    action: "ALLOW_ANONYMOUS",
    targetCount: 1,
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    summary: { alreadySatisfied: 0 },
  }),
  executeAdminModelBulk: vi.fn().mockResolvedValue({
    operationId: "op1",
    status: "COMPLETED",
    action: "ALLOW_ANONYMOUS",
    targetCount: 1,
    changedCount: 1,
    alreadySatisfiedCount: 0,
    failedCount: 0,
    items: [{ configuredModelId: "m1", displayName: "Public candidate", outcome: "CHANGED", errorCode: null, errorMessage: null }],
  }),
  setAdminModelAnonymousDefault,
}));

vi.mock("@/lib/ui/confirm", () => ({ confirmDialog: vi.fn().mockResolvedValue(true) }));

describe("AdminModelAccessPage", () => {
  afterEach(() => vi.clearAllMocks());

  it("selects a model and executes the frozen anonymous-access preview", async () => {
    render(<AdminModelAccessPage />);
    expect(await screen.findByText("Public candidate")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "Select Public candidate" }));
    fireEvent.click(screen.getByRole("button", { name: "Allow anonymous" }));
    await waitFor(() => expect(screen.getByText(/1 changed/)).toBeInTheDocument());
  });

  it("sets a model as a guest default", async () => {
    render(<AdminModelAccessPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Set default" }));

    await waitFor(() => expect(setAdminModelAnonymousDefault).toHaveBeenCalledWith("", "c1", "m1", true));
  });
});
