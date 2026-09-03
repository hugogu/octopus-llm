import { test, expect } from "@playwright/test";

const model = {
  id: "public-model-1",
  modelId: "provider-model",
  displayName: "Public candidate",
  protocol: "openai-compatible",
  capabilities: { streaming: true, vision: false, tools: false },
};

test.describe("anonymous chat", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/api/v2/anonymous/models**", (route) => route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ items: [model], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
    }));
    await page.route("**/api/v2/anonymous/chat/turns", (route) => route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: [
        'event: status\ndata: {"state":"STARTED"}\n\n',
        'event: token\ndata: {"configuredModelId":"public-model-1","text":"Hello from the public model."}\n\n',
        'event: model_complete\ndata: {"configuredModelId":"public-model-1","status":"COMPLETE"}\n\n',
        'event: result\ndata: {"state":"COMPLETE"}\n\n',
      ].join(""),
    }));
  });

  test("discovers a public model and streams the first prompt", async ({ page }) => {
    await page.goto("/chat");
    await expect(page.getByTestId("anonymous-chat")).toBeVisible();
    await expect(page.getByText("Public candidate")).toBeVisible();
    await page.getByPlaceholder(/Ask all selected models/).fill("Hello");
    await page.getByPlaceholder(/Ask all selected models/).press("Enter");
    await expect(page.getByText("Hello from the public model.")).toBeVisible();
    await expect(page.getByText(/cannot be shared/)).toBeVisible();
  });

  test("does not open authenticated session history directly", async ({ request, baseURL }) => {
    const response = await request.get(`${baseURL}/api/v2/chat/sessions`);
    expect(response.status()).toBe(401);
  });
});
