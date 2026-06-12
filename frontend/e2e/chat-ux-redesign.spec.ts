import { test, expect, type Page } from "@playwright/test";

// Helper function to register and login a test user
async function loginUser(page: Page) {
  // Go to register page
  await page.goto("/register");
  
  // Fill registration form with unique email
  const testEmail = `test-${Date.now()}@example.com`;
  await page.getByPlaceholder(/email/i).fill(testEmail);
  await page.getByPlaceholder(/password.*min/i).fill("TestPassword123!");
  await page.getByPlaceholder(/confirm/i).fill("TestPassword123!");
  await page.getByRole("button", { name: /create account/i }).click();
  
  // Wait for redirect to login or chat
  await page.waitForURL(/.*(login|chat).*/, { timeout: 15000 });
  
  // If redirected to login, login
  if (page.url().includes("login")) {
    await page.getByPlaceholder(/email/i).fill(testEmail);
    await page.getByPlaceholder(/password/i).fill("TestPassword123!");
    await page.getByRole("button", { name: /sign in/i }).click();
    await page.waitForURL(/.*chat.*/, { timeout: 10000 });
  }
}

test.describe("Landing Page", () => {
  test("should redirect to login when unauthenticated", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveURL(/.*login.*/, { timeout: 10000 });
  });
});

test.describe("Authentication", () => {
  test("should show login form", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByPlaceholder(/email/i)).toBeVisible();
    await expect(page.getByPlaceholder(/password/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /sign in/i })).toBeVisible();
  });

  test("should show register form", async ({ page }) => {
    await page.goto("/register");
    await expect(page.getByPlaceholder(/email/i)).toBeVisible();
    await expect(page.getByPlaceholder(/password.*min/i)).toBeVisible();
    await expect(page.getByPlaceholder(/confirm/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /create account/i })).toBeVisible();
  });

  test("should register a new user and redirect", async ({ page }) => {
    await page.goto("/register");
    const testEmail = `test-${Date.now()}@example.com`;
    await page.getByPlaceholder(/email/i).fill(testEmail);
    await page.getByPlaceholder(/password.*min/i).fill("TestPassword123!");
    await page.getByPlaceholder(/confirm/i).fill("TestPassword123!");
    await page.getByRole("button", { name: /create account/i }).click();
    
    // Should redirect to login or chat
    await expect(page).toHaveURL(/.*(login|chat).*/, { timeout: 10000 });
  });
});

test.describe("Chat Interface (Authenticated)", () => {
  test.beforeEach(async ({ page }) => {
    await loginUser(page);
  });

  test("should display chat page with input", async ({ page }) => {
    await expect(page.getByPlaceholder(/ask all selected models/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /send/i })).toBeVisible();
  });

  test("should show session sidebar", async ({ page }) => {
    await expect(page.getByText(/new chat/i)).toBeVisible();
  });

  test("should create a new session", async ({ page }) => {
    await page.getByText(/new chat/i).click();
    await expect(page).toHaveURL(/.*chat.*/);
  });
});

test.describe("Settings (Authenticated)", () => {
  test.beforeEach(async ({ page }) => {
    await loginUser(page);
  });

  test("should display settings page", async ({ page }) => {
    await page.goto("/settings/models");
    await expect(page.getByRole("heading", { name: /model settings/i })).toBeVisible();
  });
});

test.describe("Responsive Design (Authenticated)", () => {
  test.beforeEach(async ({ page }) => {
    await loginUser(page);
  });

  test("should adapt to mobile viewport", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto("/chat");
    await expect(page.getByPlaceholder(/ask all selected models/i)).toBeVisible();
  });

  test("should adapt to tablet viewport", async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto("/chat");
    await expect(page.getByPlaceholder(/ask all selected models/i)).toBeVisible();
  });

  test("should adapt to desktop viewport", async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto("/chat");
    await expect(page.getByPlaceholder(/ask all selected models/i)).toBeVisible();
  });
});
