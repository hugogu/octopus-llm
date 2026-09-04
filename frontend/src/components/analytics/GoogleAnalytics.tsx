"use client";

import Script from "next/script";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

declare global {
  interface Window {
    dataLayer: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

interface Props {
  measurementId: string | null | undefined;
}

const measurementIdPattern = /^G-[A-Za-z0-9]{1,62}$/;

/** Loads GA4 only when an administrator has configured a valid Measurement ID. */
export default function GoogleAnalytics({ measurementId }: Props) {
  const pathname = usePathname();
  const [ready, setReady] = useState(false);
  const enabled = Boolean(measurementId && measurementIdPattern.test(measurementId));
  const pagePath = pathname ?? "/";

  useEffect(() => {
    if (!enabled || !ready || !measurementId || !window.gtag) return;
    window.gtag("config", measurementId, { page_path: pagePath });
  }, [enabled, measurementId, pagePath, ready]);

  if (!enabled || !measurementId) return null;

  return (
    <>
      <Script id="google-analytics-data-layer" strategy="afterInteractive">
        {"window.dataLayer = window.dataLayer || []; window.gtag = window.gtag || function () { window.dataLayer.push(arguments); };"}
      </Script>
      <Script
        id="google-analytics-script"
        src={`https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(measurementId)}`}
        strategy="afterInteractive"
        onLoad={() => {
          window.dataLayer = window.dataLayer || [];
          window.gtag = window.gtag || ((...args: unknown[]) => window.dataLayer.push(args));
          window.gtag?.("js", new Date());
          setReady(true);
        }}
      />
    </>
  );
}
