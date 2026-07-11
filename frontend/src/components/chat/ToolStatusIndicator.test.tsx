import { fireEvent, render, screen } from "@testing-library/react";
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

  it("expands a chip to show request arguments, the request URL, and the result", () => {
    const calls: ToolCallState[] = [
      {
        callId: "c1",
        toolName: "web_search",
        status: "success",
        arguments: { query: "上海天气" },
        result: { answer: "晴", endpoint: "https://openrouter.ai/api/v1/chat/completions" },
      },
    ];
    render(<ToolStatusIndicator toolCalls={calls} />);

    // Collapsed by default.
    expect(screen.queryByText("请求参数")).not.toBeInTheDocument();
    fireEvent.click(screen.getByText("web_search"));
    expect(screen.getByText("请求参数")).toBeInTheDocument();
    expect(screen.getByText(/上海天气/)).toBeInTheDocument();
    // The endpoint is surfaced in the request area...
    expect(screen.getByText("请求地址 (URL)")).toBeInTheDocument();
    expect(screen.getByText("https://openrouter.ai/api/v1/chat/completions")).toBeInTheDocument();
    // ...and no longer duplicated inside the result JSON.
    expect(screen.getByText("返回结果")).toBeInTheDocument();
    expect(screen.getByText(/晴/)).toBeInTheDocument();
    expect(screen.queryByText(/endpoint/)).not.toBeInTheDocument();
  });

  it("shows the error block instead of a result on a failed call", () => {
    const calls: ToolCallState[] = [
      { callId: "c4", toolName: "web_search", status: "timeout", arguments: { query: "x" }, error: "Tool timed out after 15s" },
    ];
    render(<ToolStatusIndicator toolCalls={calls} />);

    fireEvent.click(screen.getByText("web_search"));
    expect(screen.getByText("错误")).toBeInTheDocument();
    expect(screen.getByText("Tool timed out after 15s")).toBeInTheDocument();
    expect(screen.queryByText("返回结果")).not.toBeInTheDocument();
  });
});
