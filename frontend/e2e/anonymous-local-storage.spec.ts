import { test, expect } from "@playwright/test";

test("anonymous conversation survives refresh and has no share action", async ({ page }) => {
  await page.route("**/api/v2/anonymous/models**", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      items: [{ id: "m1", modelId: "m1", displayName: "Local model", protocol: "openai-compatible", capabilities: { streaming: true, vision: false, tools: false } }],
      page: 0, size: 100, totalElements: 1, totalPages: 1,
    }),
  }));
  await page.route("**/api/v2/anonymous/chat/turns", (route) => route.fulfill({
    contentType: "text/event-stream",
    body: 'event: token\ndata: {"configuredModelId":"m1","text":"saved"}\n\nevent: model_complete\ndata: {"configuredModelId":"m1","status":"COMPLETE"}\n\nevent: result\ndata: {"state":"COMPLETE"}\n\n',
  }));
  await page.goto("/chat");
  await page.getByPlaceholder(/Ask all selected models/).fill("Remember this");
  await page.getByPlaceholder(/Ask all selected models/).press("Enter");
  await expect(page.getByText("saved")).toBeVisible();
  await page.reload();
  await expect(page.getByText("Remember this")).toBeVisible();
  await expect(page.getByText(/cannot be shared/)).toBeVisible();
  await expect(page.getByRole("button", { name: /Share/ })).toHaveCount(0);
});
