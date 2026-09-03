import { getPublicSiteSettings, type SiteSettingsPublic } from "@/lib/api/siteSettings";

/**
 * Tiny site-info footer rendered at the bottom of every page. Each public record (ICP, public-
 * security record) links to its official reference site per Chinese convention; the free-form site
 * name + footer text render as plain text — admin-controlled content is never interpreted as markup.
 *
 * The product attribution remains visible even when optional site settings are empty or unavailable.
 */
export default async function SiteFooter() {
  const settings = await fetchSettings();
  const siteInfo = settings ?? {
    siteName: null,
    footerText: null,
    chinaFilingEnabled: false,
    icpRecordNo: null,
    policeRecordNo: null,
  };

  return (
    <footer className="mt-4 border-t border-stone-200/80 bg-[#faf9f5] px-4 py-3 sm:px-6">
      <div className="mx-auto flex max-w-5xl flex-col gap-2 text-xs text-stone-500 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0 text-center sm:text-left">
          {siteInfo.siteName && <p className="font-medium text-stone-700">{siteInfo.siteName}</p>}
          {siteInfo.footerText && <p className="whitespace-pre-wrap">{siteInfo.footerText}</p>}
          {siteInfo.chinaFilingEnabled && (siteInfo.icpRecordNo || siteInfo.policeRecordNo) && (
            <p className="mt-1 flex flex-wrap items-center justify-center gap-x-3 gap-y-1 sm:justify-start">
              {siteInfo.icpRecordNo && (
                <a
                  href="https://beian.miit.gov.cn/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hover:text-stone-700 hover:underline"
                >
                  {siteInfo.icpRecordNo}
                </a>
              )}
              {siteInfo.icpRecordNo && siteInfo.policeRecordNo && (
                <span aria-hidden className="text-stone-300">
                  |
                </span>
              )}
              {siteInfo.policeRecordNo && (
                <a
                  href="https://beian.mps.gov.cn/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hover:text-stone-700 hover:underline"
                >
                  {siteInfo.policeRecordNo}
                </a>
              )}
            </p>
          )}
        </div>
        <p className="shrink-0 text-center sm:text-right">
          Powered by{" "}
          <a
            href="https://github.com/hugogu/octopus-llm"
            target="_blank"
            rel="noopener noreferrer"
            className="font-medium text-stone-700 hover:underline"
          >
            Octopus LLM
          </a>
        </p>
      </div>
    </footer>
  );
}

async function fetchSettings(): Promise<SiteSettingsPublic | null> {
  try {
    return await getPublicSiteSettings();
  } catch {
    // Footer must never break the page; swallow network / 5xx errors and render nothing.
    return null;
  }
}
