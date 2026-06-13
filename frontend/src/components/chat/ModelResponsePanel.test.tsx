import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ModelResponsePanel from "./ModelResponsePanel";

describe("ModelResponsePanel", () => {
  it("offers retry for a failed model response", () => {
    const onRetry = vi.fn();
    render(
      <ModelResponsePanel
        modelId="provider-model"
        displayName="Model"
        text=""
        status="error"
        errorMessage="Provider failed"
        onRetry={onRetry}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Provider failed")).toBeInTheDocument();
  });

  it("disables retry while that model is retrying", () => {
    render(
      <ModelResponsePanel
        modelId="provider-model"
        text=""
        status="error"
        onRetry={vi.fn()}
        retrying
      />,
    );

    expect(screen.getByRole("button", { name: "Retry" })).toBeDisabled();
  });
});
