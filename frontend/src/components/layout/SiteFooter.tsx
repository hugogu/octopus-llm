import { getPublicSiteSettings, type SiteSettingsPublic } from "@/lib/api/siteSettings";

/**
 * Tiny site-info footer rendered at the bottom of every page. Each public record (ICP, public-
 * security record) links to its official reference site per Chinese convention; the free-form site
 * name + footer text render as plain text — admin-controlled content is never interpreted as markup.
 *
 * Returns nothing at all if every field is empty so a freshly-deployed instance does not draw an
 * empty bar at the bottom of every page.
 */
export default async function SiteFooter() {
  const settings = await fetchSettings();
  if (settings == null) return null;

  return (
    <footer className="mt-10 border-t border-stone-200 bg-white/40 px-4 py-6 sm:px-6">
      <div className="mx-auto flex max-w-5xl flex-col items-center gap-2 text-center text-xs text-stone-500">
        {settings.siteName && <p className="font-medium text-stone-700">{settings.siteName}</p>}
        {settings.footerText && <p className="whitespace-pre-wrap">{settings.footerText}</p>}
        {settings.chinaFilingEnabled && (settings.icpRecordNo || settings.policeRecordNo) && (
          <p className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1">
            {settings.icpRecordNo && (
              <a
                href="https://beian.miit.gov.cn/"
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-stone-700 hover:underline"
              >
                {settings.icpRecordNo}
              </a>
            )}
            {settings.icpRecordNo && settings.policeRecordNo && (
              <span aria-hidden className="text-stone-300">
                |
              </span>
            )}
            {settings.policeRecordNo && (
              <a
                href="https://beian.mps.gov.cn/"
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-stone-700 hover:underline"
              >
                {settings.policeRecordNo}
              </a>
            )}
          </p>
        )}
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
