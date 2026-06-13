import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PublicModelAnalytics from "./PublicModelAnalytics";

vi.mock("@/lib/api/analytics", () => ({
  getPublicModelAnalytics: vi.fn().mockResolvedValue({
    items: [], page: 0, size: 25, totalElements: 0, totalPages: 0,
  }),
}));

describe("PublicModelAnalytics", () => {
  it("loads without authentication and renders the empty state", async () => {
    render(<PublicModelAnalytics />);
    await waitFor(() => expect(screen.getByText(/No aggregate data/)).toBeInTheDocument());
    expect(screen.getByText(/Anonymous aggregates only/)).toBeInTheDocument();
  });
});
