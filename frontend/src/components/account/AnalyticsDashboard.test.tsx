import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AnalyticsDashboard from "./AnalyticsDashboard";

vi.mock("@/lib/api/auth", () => ({ getToken: () => "token" }));
vi.mock("@/lib/api/connections", () => ({
  listConfiguredModels: vi.fn().mockResolvedValue({
    items: [{ id: "m1", displayName: "GPT-4o", isEnabled: true }],
    page: 0, size: 100, totalElements: 1, totalPages: 1,
  }),
}));

const getAnalyticsSummary = vi.fn();
const getAnalyticsTimeseries = vi.fn();
vi.mock("@/lib/api/analytics", () => ({
  getAnalyticsSummary: (...args: unknown[]) => getAnalyticsSummary(...args),
  getAnalyticsTimeseries: (...args: unknown[]) => getAnalyticsTimeseries(...args),
}));

describe("AnalyticsDashboard", () => {
  it("renders a friendly empty state", async () => {
    getAnalyticsSummary.mockResolvedValue({
      totalResponses: 0, successRate: 0, avgLatencyMs: 0,
      totalInputTokens: 0, totalOutputTokens: 0, estimatedCostsByCurrency: {},
    });
    getAnalyticsTimeseries.mockResolvedValue({ items: [] });
    render(<AnalyticsDashboard />);
    await waitFor(() => expect(screen.getByText(/No response history/)).toBeInTheDocument());
  });

  it("renders trend charts and a model name picker when history exists", async () => {
    getAnalyticsSummary.mockResolvedValue({
      totalResponses: 5, successRate: 0.8, avgLatencyMs: 1200,
      totalInputTokens: 100, totalOutputTokens: 200, estimatedCostsByCurrency: { USD: 0.12 },
    });
    getAnalyticsTimeseries.mockResolvedValue({
      items: [
        { bucket: "2026-06-12", responseCount: 2, avgLatencyMs: 1100, successRate: 1, inputTokens: 40, outputTokens: 80 },
        { bucket: "2026-06-13", responseCount: 3, avgLatencyMs: 1300, successRate: 0.66, inputTokens: 60, outputTokens: 120 },
      ],
    });
    render(<AnalyticsDashboard />);
    await waitFor(() => expect(screen.getByText("Latency")).toBeInTheDocument());
    expect(screen.getByText("Success rate")).toBeInTheDocument();
    expect(screen.getByText("Token usage")).toBeInTheDocument();
    // The filter is a readable model picker, not a UUID box.
    expect(await screen.findByRole("option", { name: "GPT-4o" })).toBeInTheDocument();
    expect(screen.queryByText(/UUID/)).not.toBeInTheDocument();
  });
});
