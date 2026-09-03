import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import SiteFooter from "./SiteFooter";

const { getPublicSiteSettings } = vi.hoisted(() => ({
  getPublicSiteSettings: vi.fn(),
}));

vi.mock("@/lib/api/siteSettings", () => ({ getPublicSiteSettings }));

const settings = (chinaFilingEnabled: boolean) => ({
  siteName: "Octopus LLM",
  footerText: "Footer",
  chinaFilingEnabled,
  icpRecordNo: "京ICP备12345678号",
  policeRecordNo: "京公网安备11010102000001号",
});

describe("SiteFooter", () => {
  beforeEach(() => vi.clearAllMocks());

  it("hides Chinese filing records when the visibility toggle is off", async () => {
    getPublicSiteSettings.mockResolvedValue(settings(false));

    render(await SiteFooter());

    expect(screen.getByText("Octopus LLM", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Octopus LLM" })).toHaveAttribute(
      "href",
      "https://github.com/hugogu/octopus-llm",
    );
    expect(screen.queryByText("京ICP备12345678号")).not.toBeInTheDocument();
    expect(screen.queryByText("京公网安备11010102000001号")).not.toBeInTheDocument();
  });

  it("shows Chinese filing records when the visibility toggle is on", async () => {
    getPublicSiteSettings.mockResolvedValue(settings(true));

    render(await SiteFooter());

    expect(screen.getByText("京ICP备12345678号")).toBeInTheDocument();
    expect(screen.getByText("京公网安备11010102000001号")).toBeInTheDocument();
  });
});
