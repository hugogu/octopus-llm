import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AddModelDialog from "./AddModelDialog";
import type { ConfiguredModelV2, ConnectionV2 } from "@/lib/types/api";

const api = vi.hoisted(() => ({
  addConfiguredModel: vi.fn(),
  listCatalogue: vi.fn(),
}));

vi.mock("@/lib/api/auth", () => ({
  getToken: () => "test-token",
}));

vi.mock("@/lib/api/connections", () => api);

const connection: ConnectionV2 = {
  id: "connection-1",
  protocol: "openai-compatible",
  label: "Primary",
  baseUrl: "https://api.example.com/v1",
  hasKey: true,
  modelCount: 0,
  createdAt: "2026-06-12T00:00:00Z",
  updatedAt: "2026-06-12T00:00:00Z",
};

const savedModel: ConfiguredModelV2 = {
  id: "configured-1",
  connectionId: connection.id,
  connectionLabel: connection.label,
  protocol: connection.protocol,
  baseUrl: connection.baseUrl,
  modelId: "provider-model-id",
  displayName: "Provider model",
  capabilityOverrides: {},
  capabilityMatrix: {
    input_modalities: ["text"],
    output_modalities: ["text"],
    context_length_tokens: null,
    supports_streaming: true,
    supports_function_calling: false,
    supports_system_prompt: true,
    supports_video_input: false,
  },
  customParams: {},
  isEnabled: true,
  sortOrder: 0,
  createdAt: "2026-06-12T00:00:00Z",
  updatedAt: "2026-06-12T00:00:00Z",
};

describe("AddModelDialog", () => {
  beforeEach(() => {
    api.addConfiguredModel.mockReset();
    api.listCatalogue.mockReset();
  });

  it("prefills fields from a catalogue suggestion and saves it", async () => {
    api.listCatalogue.mockResolvedValue({
      items: [{
        protocol: connection.protocol,
        providerLabel: "Moonshot",
        modelId: "kimi-k2.6",
        displayName: "Kimi K2.6",
        suggestedBaseUrl: connection.baseUrl,
        capabilityOverrides: { context_length_tokens: 256000 },
        customParams: { temperature: 0.2 },
      }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    api.addConfiguredModel.mockResolvedValue(savedModel);
    const onClose = vi.fn();
    const onSaved = vi.fn();

    render(<AddModelDialog connection={connection} onClose={onClose} onSaved={onSaved} />);

    const catalogue = await screen.findByLabelText("Catalogue suggestion");
    fireEvent.change(catalogue, { target: { value: "kimi-k2.6" } });
    expect(screen.getByLabelText("Model ID")).toHaveValue("kimi-k2.6");
    expect(screen.getByLabelText("Display name")).toHaveValue("Kimi K2.6");

    fireEvent.click(screen.getByRole("button", { name: "Add model" }));

    await waitFor(() => expect(api.addConfiguredModel).toHaveBeenCalledWith(
      "test-token",
      expect.objectContaining({
        connectionId: connection.id,
        modelId: "kimi-k2.6",
        displayName: "Kimi K2.6",
        capabilityOverrides: { context_length_tokens: 256000 },
        customParams: { temperature: 0.2 },
      }),
    ));
    expect(onSaved).toHaveBeenCalledWith(savedModel);
    expect(onClose).toHaveBeenCalled();
  });

  it("keeps manual model entry available when the catalogue fails", async () => {
    api.listCatalogue.mockRejectedValue(new Error("Catalogue unavailable"));
    api.addConfiguredModel.mockResolvedValue(savedModel);

    render(<AddModelDialog connection={connection} onClose={vi.fn()} onSaved={vi.fn()} />);

    expect(await screen.findByText(/Manual model entry remains available/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Model ID"), { target: { value: "provider-model-id" } });
    fireEvent.change(screen.getByLabelText("Display name"), { target: { value: "Provider model" } });
    fireEvent.change(screen.getByLabelText("Custom request parameters"), {
      target: { value: '{"temperature":0.4}' },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add model" }));

    await waitFor(() => expect(api.addConfiguredModel).toHaveBeenCalledWith(
      "test-token",
      expect.objectContaining({
        modelId: "provider-model-id",
        customParams: { temperature: 0.4 },
      }),
    ));
  });
});
