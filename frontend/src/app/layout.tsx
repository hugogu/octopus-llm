import type { Metadata } from "next";
import "./globals.css";
import "katex/dist/katex.min.css";
import ConfirmHost from "@/components/ui/ConfirmHost";
import SiteFooter from "@/components/layout/SiteFooter";
import GoogleAnalytics from "@/components/analytics/GoogleAnalytics";
import { getPublicSiteSettings } from "@/lib/api/siteSettings";

export const metadata: Metadata = {
  title: "Octopus LLM",
  description: "Configure provider keys and compare model responses side by side.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const siteSettings = await getPublicSiteSettings().catch(() => null);

  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col">
        {children}
        <GoogleAnalytics measurementId={siteSettings?.googleAnalyticsMeasurementId} />
        <ConfirmHost />
        <SiteFooter settings={siteSettings} />
      </body>
    </html>
  );
}
