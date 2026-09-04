import { expect, test } from "@playwright/test";

const blankSettings = {
  siteName: null,
  footerText: null,
  chinaFilingEnabled: false,
  icpRecordNo: null,
  policeRecordNo: null,
  googleAnalyticsMeasurementId: null,
  updatedAt: "2026-01-01T00:00:00.000Z",
  updatedBy: null,
};

test("admin site settings can configure Google Analytics", async ({ page }) => {
  let savedSettings: Record<string, unknown> | undefined;

  await page.addInitScript(() => {
    document.cookie = "auth_token=test-admin-token; path=/";
  });
  await page.route("**/api/v2/me", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ isAdmin: true }),
  }));
  await page.route("**/api/v2/admin/site-settings", async (route) => {
    if (route.request().method() === "PUT") {
      savedSettings = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ ...blankSettings, googleAnalyticsMeasurementId: "G-ABC123" }),
      });
      return;
    }
    await route.fulfill({ contentType: "application/json", body: JSON.stringify(blankSettings) });
  });

  await page.goto("/admin/site");
  await expect(page.getByRole("heading", { name: "Site info" })).toBeVisible();
  await page.getByLabel("Measurement ID").fill("G-ABC123");
  await page.getByRole("button", { name: "Save" }).click();

  await expect(page.getByText("Site settings saved.")).toBeVisible();
  expect(savedSettings).toMatchObject({ googleAnalyticsMeasurementId: "G-ABC123" });
});
