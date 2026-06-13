import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ConnectionCard from "./ConnectionCard";
import EditConnectionDialog from "./EditConnectionDialog";
import EditModelDialog from "./EditModelDialog";
import type { ConfiguredModelV2, ConnectionV2 } from "@/lib/types/api";

const api = vi.hoisted(() => ({
  deleteConnection: vi.fn(),
  patchConfiguredModel: vi.fn(),
  patchConnection: vi.fn(),
  rotateConnectionKey: vi.fn(),
}));

vi.mock("@/lib/api/auth", () => ({
  getToken: () => "test-token",
}));

vi.mock("@/lib/api/connections", () => ({
  ...api,
  deleteConfiguredModel: vi.fn(),
}));

const connection: ConnectionV2 = {
  id: "connection-1",
  protocol: "openai-compatible",
  label: "Primary",
  baseUrl: "https://api.example.com/v1",
  hasKey: true,
  builtin: false,
  readOnly: false,
  modelCount: 1,
  createdAt: "2026-06-12T00:00:00Z",
  updatedAt: "2026-06-12T00:00:00Z",
};

const model: ConfiguredModelV2 = {
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
  customParams: { temperature: 0.2 },
  isEnabled: true,
  sortOrder: 0,
  inputPricePerMtok: null,
  outputPricePerMtok: null,
  priceCurrency: null,
  createdAt: "2026-06-12T00:00:00Z",
  updatedAt: "2026-06-12T00:00:00Z",
};

describe("connection settings", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    Object.values(api).forEach((mock) => mock.mockReset());
  });

  it("patches connection metadata and rotates a non-empty API key", async () => {
    api.patchConnection.mockResolvedValue({ ...connection, label: "Renamed" });
    api.rotateConnectionKey.mockResolvedValue(undefined);
    const onSaved = vi.fn();

    render(<EditConnectionDialog connection={connection} onClose={vi.fn()} onSaved={onSaved} />);

    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "Renamed" } });
    fireEvent.change(screen.getByLabelText("API key"), { target: { value: "replacement-key" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(api.patchConnection).toHaveBeenCalledWith(
      "test-token",
      connection.id,
      { label: "Renamed", baseUrl: connection.baseUrl },
    ));
    expect(api.rotateConnectionKey).toHaveBeenCalledWith(
      "test-token",
      connection.id,
      "replacement-key",
    );
    expect(onSaved).toHaveBeenCalled();
  });

  it("parses and saves edited custom model parameters and pricing", async () => {
    api.patchConfiguredModel.mockResolvedValue({
      ...model,
      customParams: { temperature: 0.7, max_tokens: 2048 },
    });

    render(<EditModelDialog model={model} onClose={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Custom request parameters"), {
      target: { value: '{"temperature":0.7,"max_tokens":2048}' },
    });
    fireEvent.change(screen.getByLabelText("Input price / 1M tokens"), { target: { value: "2.5" } });
    fireEvent.change(screen.getByLabelText("Output price / 1M tokens"), { target: { value: "10" } });
    fireEvent.change(screen.getByLabelText("Currency"), { target: { value: "usd" } });
    fireEvent.click(screen.getByRole("button", { name: "Save model" }));

    await waitFor(() => expect(api.patchConfiguredModel).toHaveBeenCalledWith(
      "test-token",
      model.id,
      expect.objectContaining({
        customParams: { temperature: 0.7, max_tokens: 2048 },
        inputPricePerMtok: 2.5,
        outputPricePerMtok: 10,
        priceCurrency: "USD",
      }),
    ));
  });

  it("requires confirmation before deleting a connection and its models", async () => {
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    api.deleteConnection.mockResolvedValue(undefined);
    const onChanged = vi.fn();

    render(
      <ConnectionCard
        connection={connection}
        models={[model]}
        onAddModel={vi.fn()}
        onEditConnection={vi.fn()}
        onEditModel={vi.fn()}
        onChanged={onChanged}
      />,
    );

    const remove = screen.getByRole("button", { name: "Delete connection" });
    fireEvent.click(remove);
    expect(api.deleteConnection).not.toHaveBeenCalled();

    fireEvent.click(remove);
    await waitFor(() => expect(api.deleteConnection).toHaveBeenCalledWith(
      "test-token",
      connection.id,
    ));
    expect(confirmSpy).toHaveBeenCalledTimes(2);
    expect(onChanged).toHaveBeenCalled();
  });
});
