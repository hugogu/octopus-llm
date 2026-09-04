import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import SiteSettingsPage from "./SiteSettingsPage";

const { getSiteSettings, updateSiteSettings } = vi.hoisted(() => ({
  getSiteSettings: vi.fn(),
  updateSiteSettings: vi.fn(),
}));

vi.mock("@/components/admin/AdminShell", () => ({
  default: ({ actions, children }: { actions?: ReactNode; children: ReactNode }) => (
    <div>
      {actions}
      {children}
    </div>
  ),
}));

vi.mock("@/lib/api/auth", () => ({ getToken: () => "test-token" }));
vi.mock("@/lib/api/siteSettings", () => ({ getSiteSettings, updateSiteSettings }));

const blankSettings = () => ({
  siteName: null,
  footerText: null,
  chinaFilingEnabled: false,
  icpRecordNo: null,
  policeRecordNo: null,
  googleAnalyticsMeasurementId: null,
  updatedAt: "2026-01-01T00:00:00.000Z",
  updatedBy: null,
});

describe("SiteSettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getSiteSettings.mockResolvedValue(blankSettings());
    updateSiteSettings.mockResolvedValue(blankSettings());
  });

  it("requires an ICP record number before enabling Chinese filing information", async () => {
    render(<SiteSettingsPage />);

    const toggle = await screen.findByRole("switch", { name: "Show Chinese filing information" });
    fireEvent.click(toggle);
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/Enter an ICP record number/i)).toBeInTheDocument();
    expect(updateSiteSettings).not.toHaveBeenCalled();
  });

  it("saves enabled Chinese filing information", async () => {
    render(<SiteSettingsPage />);

    fireEvent.click(await screen.findByRole("switch", { name: "Show Chinese filing information" }));
    fireEvent.change(screen.getByLabelText(/ICP record number/i), {
      target: { value: "京ICP备12345678号" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(updateSiteSettings).toHaveBeenCalledWith("test-token", {
      siteName: "",
      footerText: "",
      chinaFilingEnabled: true,
      icpRecordNo: "京ICP备12345678号",
      policeRecordNo: "",
      googleAnalyticsMeasurementId: "",
    }));
  });

  it("saves a Google Analytics 4 Measurement ID", async () => {
    render(<SiteSettingsPage />);

    fireEvent.change(await screen.findByLabelText("Measurement ID"), {
      target: { value: "G-ABC123" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(updateSiteSettings).toHaveBeenCalledWith("test-token", expect.objectContaining({
      googleAnalyticsMeasurementId: "G-ABC123",
    })));
  });

  it("rejects an invalid Google Analytics 4 Measurement ID before saving", async () => {
    render(<SiteSettingsPage />);

    fireEvent.change(await screen.findByLabelText("Measurement ID"), {
      target: { value: "UA-12345" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/valid Google Analytics 4 Measurement ID/i)).toBeInTheDocument();
    expect(updateSiteSettings).not.toHaveBeenCalled();
  });
});
