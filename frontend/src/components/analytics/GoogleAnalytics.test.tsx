import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import GoogleAnalytics from "./GoogleAnalytics";

const { usePathname } = vi.hoisted(() => ({
  usePathname: vi.fn(() => "/chat"),
}));

vi.mock("next/navigation", () => ({ usePathname }));
vi.mock("next/script", () => ({
  default: ({ children, onLoad, src, ...props }: { children?: ReactNode; onLoad?: () => void; src?: string; [key: string]: unknown }) =>
    src ? (
      <button type="button" data-testid="external-script" data-src={src} onClick={onLoad} {...props}>
        {children}
      </button>
    ) : (
      <script {...props}>{children}</script>
    ),
}));

describe("GoogleAnalytics", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    usePathname.mockReturnValue("/chat");
    window.gtag = undefined;
  });

  it("does not render scripts when no valid Measurement ID is configured", () => {
    const { container } = render(<GoogleAnalytics measurementId={null} />);

    expect(container.querySelectorAll("script")).toHaveLength(0);
  });

  it("loads GA4 and sends page views for the current route", () => {
    const gtag = vi.fn();
    window.gtag = gtag;

    const { container, rerender } = render(<GoogleAnalytics measurementId="G-ABC123" />);
    const externalScript = screen.getByTestId("external-script");

    expect(externalScript).toHaveAttribute("data-src", "https://www.googletagmanager.com/gtag/js?id=G-ABC123");
    expect(container.textContent).toContain("window.dataLayer = window.dataLayer || []");

    fireEvent.click(externalScript);

    return waitFor(() => {
      expect(gtag).toHaveBeenCalledWith("js", expect.any(Date));
      expect(gtag).toHaveBeenCalledWith("config", "G-ABC123", { page_path: "/chat" });
    }).then(() => {
      usePathname.mockReturnValue("/account");
      rerender(<GoogleAnalytics measurementId="G-ABC123" />);
      return waitFor(() => expect(gtag).toHaveBeenCalledWith("config", "G-ABC123", { page_path: "/account" }));
    });
  });
});
