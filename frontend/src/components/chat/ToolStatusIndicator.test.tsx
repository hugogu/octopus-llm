import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ToolCallState } from "@/lib/types/api";
import ToolStatusIndicator from "./ToolStatusIndicator";

describe("ToolStatusIndicator", () => {
  it("renders nothing when there are no tool calls", () => {
    const { container } = render(<ToolStatusIndicator toolCalls={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows a chip per tool call with name and status", () => {
    const calls: ToolCallState[] = [
      { callId: "c1", toolName: "current_time", status: "success", result: { time: "10:30" } },
      { callId: "c2", toolName: "stock_quote", status: "running" },
    ];
    render(<ToolStatusIndicator toolCalls={calls} />);

    expect(screen.getByText("current_time")).toBeInTheDocument();
    expect(screen.getByText("已完成")).toBeInTheDocument();
    expect(screen.getByText("stock_quote")).toBeInTheDocument();
    expect(screen.getByText("调用中")).toBeInTheDocument();
  });

  it("exposes the error message on a failed call via the chip title", () => {
    const calls: ToolCallState[] = [
      { callId: "c3", toolName: "weather", status: "failed", error: "provider 503 after retry" },
    ];
    render(<ToolStatusIndicator toolCalls={calls} />);

    expect(screen.getByText("失败")).toBeInTheDocument();
    expect(screen.getByTitle("provider 503 after retry")).toBeInTheDocument();
  });
});
