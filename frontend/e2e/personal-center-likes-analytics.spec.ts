import { expect, test } from "@playwright/test";

test("public analytics is reachable without authentication and is responsive", async ({ page }) => {
  await page.goto("/analytics");
  await expect(page.getByRole("heading", { name: /Model usage across Octopus LLM/ })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByText(/Anonymous aggregates only/)).toBeVisible();
});

test("forgot password uses a non-disclosing confirmation", async ({ page }) => {
  await page.route("**/api/v1/auth/password-reset/request", async (route) => {
    await route.fulfill({ status: 202, contentType: "application/json", body: '{"status":"accepted"}' });
  });
  await page.goto("/forgot-password");
  await page.getByPlaceholder("Email").fill("unknown@example.com");
  await page.getByRole("button", { name: "Request reset" }).click();
  await expect(page.getByText(/If an eligible account exists/)).toBeVisible();
});
