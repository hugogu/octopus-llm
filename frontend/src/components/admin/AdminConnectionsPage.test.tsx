import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import AdminConnectionsPage from "./AdminConnectionsPage";

const { patchBuiltinModel } = vi.hoisted(() => ({
  patchBuiltinModel: vi.fn().mockResolvedValue({}),
}));

vi.mock("@/lib/api/admin", () => ({
  listBuiltinConnections: vi.fn().mockResolvedValue({
    items: [{
      id: "connection-1",
      protocol: "openai-compatible",
      label: "Built-in",
      baseUrl: "https://example.com/v1",
      hasKey: true,
      modelCount: 1,
      allocatedUserCount: 0,
      createdAt: "2026-01-01T00:00:00.000Z",
      updatedAt: "2026-01-01T00:00:00.000Z",
    }],
    page: 0,
    size: 100,
    totalElements: 1,
    totalPages: 1,
  }),
  listBuiltinModels: vi.fn().mockResolvedValue({
    items: [{
      id: "model-1",
      connectionId: "connection-1",
      modelId: "test-model",
      displayName: "Test model",
      isEnabled: true,
      isAnonymousAllowed: false,
      sortOrder: 0,
      inputPricePerMtok: null,
      outputPricePerMtok: null,
      priceCurrency: null,
      capabilityOverrides: {},
      createdAt: "2026-01-01T00:00:00.000Z",
      updatedAt: "2026-01-01T00:00:00.000Z",
    }],
    page: 0,
    size: 100,
    totalElements: 1,
    totalPages: 1,
  }),
  listAllocations: vi.fn().mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
  patchBuiltinModel,
  createBuiltinConnection: vi.fn(),
  deleteBuiltinConnection: vi.fn(),
  deleteBuiltinModel: vi.fn(),
  detectBuiltinCapabilities: vi.fn(),
  addBuiltinModel: vi.fn(),
  allocateConnection: vi.fn(),
  revokeConnection: vi.fn(),
  loadBuiltinEndpointModels: vi.fn(),
  listUsers: vi.fn(),
}));

vi.mock("@/lib/ui/confirm", () => ({ confirmDialog: vi.fn().mockResolvedValue(true) }));

describe("AdminConnectionsPage", () => {
  afterEach(() => vi.clearAllMocks());

  it("toggles anonymous access for an individual built-in model", async () => {
    render(<AdminConnectionsPage />);

    const button = await screen.findByRole("button", { name: "Open anonymous" });
    fireEvent.click(button);

    await waitFor(() => expect(patchBuiltinModel).toHaveBeenCalledWith(
      "",
      "connection-1",
      "model-1",
      { isAnonymousAllowed: true },
    ));
  });
});
