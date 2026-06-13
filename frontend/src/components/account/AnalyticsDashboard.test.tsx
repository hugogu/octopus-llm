import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AnalyticsDashboard from "./AnalyticsDashboard";

vi.mock("@/lib/api/auth", () => ({ getToken: () => "token" }));
vi.mock("@/lib/api/analytics", () => ({
  getAnalyticsSummary: vi.fn().mockResolvedValue({
    totalResponses: 0, successRate: 0, avgLatencyMs: 0,
    totalInputTokens: 0, totalOutputTokens: 0, estimatedCostsByCurrency: {},
  }),
  getModelAnalytics: vi.fn().mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }),
  getSessionAnalytics: vi.fn().mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }),
  getResponseAnalytics: vi.fn().mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }),
}));

describe("AnalyticsDashboard", () => {
  it("renders a friendly empty state", async () => {
    render(<AnalyticsDashboard />);
    await waitFor(() => expect(screen.getByText(/No response history/)).toBeInTheDocument());
  });
});
