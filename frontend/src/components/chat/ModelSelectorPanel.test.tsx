import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ModelSelectorPanel from "./ModelSelectorPanel";
import type { ConfiguredModelV2 } from "@/lib/types/api";

const capabilityMatrix = {
  input_modalities: ["text"],
  output_modalities: ["text"],
  context_length_tokens: null,
  supports_streaming: true,
  supports_function_calling: false,
  supports_system_prompt: true,
  supports_video_input: false,
};

const models: ConfiguredModelV2[] = [
  {
    id: "configured-a",
    connectionId: "connection-a",
    connectionLabel: "Primary",
    protocol: "openai-compatible",
    baseUrl: "https://example.com/v1",
    modelId: "same-model",
    displayName: "Same model A",
    capabilityOverrides: {},
    capabilityMatrix,
    customParams: {},
    isEnabled: true,
    sortOrder: 0,
    createdAt: "2026-06-12T00:00:00Z",
    updatedAt: "2026-06-12T00:00:00Z",
  },
  {
    id: "configured-b",
    connectionId: "connection-b",
    connectionLabel: "Backup",
    protocol: "openai-compatible",
    baseUrl: "https://backup.example.com/v1",
    modelId: "same-model",
    displayName: "Same model B",
    capabilityOverrides: {},
    capabilityMatrix,
    customParams: {},
    isEnabled: true,
    sortOrder: 0,
    createdAt: "2026-06-12T00:00:00Z",
    updatedAt: "2026-06-12T00:00:00Z",
  },
];

describe("ModelSelectorPanel", () => {
  it("selects configured model UUIDs and exposes pressed state", () => {
    const onChange = vi.fn();
    render(<ModelSelectorPanel models={models} selectedIds={[]} onChange={onChange} />);

    const option = screen.getByRole("button", { name: /Same model A/ });
    expect(option).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(option);
    expect(onChange).toHaveBeenCalledWith(["configured-a"]);
  });

  it("uses a single settings entry point", () => {
    render(<ModelSelectorPanel models={models} selectedIds={["configured-a"]} onChange={vi.fn()} />);
    // The picker is collapsed when models are selected; open it to reveal the link.
    fireEvent.click(screen.getByRole("button", { expanded: false }));
    expect(screen.getAllByRole("link", { name: "Manage models" })).toHaveLength(1);
  });
});
