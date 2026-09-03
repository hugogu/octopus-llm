import { test, expect } from "@playwright/test";

test("admin model table supports page selection and frozen bulk scope", async ({ page }) => {
  await page.addInitScript(() => {
    document.cookie = "auth_token=test-admin-token; path=/";
  });
  await page.route("**/api/v2/me", (route) => route.fulfill({ contentType: "application/json", body: JSON.stringify({ isAdmin: true }) }));
  await page.route("**/api/v2/admin/models**", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ items: [{ id: "m1", connection: { id: "c1", label: "Platform" }, modelId: "m1", displayName: "Managed model", protocol: "openai-compatible", capabilities: { streaming: true, vision: false, tools: false }, isEnabled: true, isAnonymousAllowed: false }], page: 0, size: 50, totalElements: 1, totalPages: 1 }),
  }));
  await page.route("**/api/v2/admin/model-bulk-operations/preview", (route) => route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify({ operationId: "op1", action: "ALLOW_ANONYMOUS", targetCount: 1, expiresAt: new Date(Date.now() + 60_000).toISOString(), summary: { alreadySatisfied: 0 } }) }));
  await page.route("**/api/v2/admin/model-bulk-operations/op1/execute", (route) => route.fulfill({ contentType: "application/json", body: JSON.stringify({ operationId: "op1", status: "COMPLETED", action: "ALLOW_ANONYMOUS", targetCount: 1, changedCount: 1, alreadySatisfiedCount: 0, failedCount: 0, items: [] }) }));
  await page.goto("/admin/models");
  await expect(page.getByText("Managed model")).toBeVisible();
  await expect(page.getByRole("button", { name: "Allow anonymous" })).toBeDisabled();
});
