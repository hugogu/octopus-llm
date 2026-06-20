import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ModelResponsePanel from "./ModelResponsePanel";

const confirm = vi.hoisted(() => vi.fn());
vi.mock("@/lib/ui/confirm", () => ({ confirmDialog: confirm }));

describe("ModelResponsePanel", () => {
  beforeEach(() => vi.clearAllMocks());
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

  it("does not delete when confirmation is cancelled", async () => {
    confirm.mockResolvedValue(false);
    const onDelete = vi.fn();
    render(
      <ModelResponsePanel
        modelId="provider-model"
        responseId="response"
        text="answer"
        status="complete"
        onDelete={onDelete}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Delete response" }));
    await waitFor(() => expect(confirm).toHaveBeenCalled());
    expect(onDelete).not.toHaveBeenCalled();
  });

  it("surfaces delete errors inline", async () => {
    confirm.mockResolvedValue(true);
    const onDelete = vi.fn().mockRejectedValue(new Error("Delete unavailable"));
    render(
      <ModelResponsePanel
        modelId="provider-model"
        responseId="response"
        text="answer"
        status="complete"
        onDelete={onDelete}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Delete response" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Delete unavailable");
    expect(screen.getByText("provider-model")).toBeInTheDocument();
  });
});
