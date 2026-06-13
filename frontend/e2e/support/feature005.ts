import type { Page } from "@playwright/test";

export async function login(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByPlaceholder("Email").fill(email);
  await page.getByPlaceholder("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL(/\/chat/);
}

export async function seedAuthToken(page: Page, token: string) {
  await page.context().addCookies([{
    name: "auth_token",
    value: token,
    url: new URL("/", page.url() || "http://127.0.0.1:3000").origin,
    sameSite: "Lax",
  }]);
}

export async function jsonResponse(routeBody: unknown, status = 200) {
  return {
    status,
    contentType: "application/json",
    body: JSON.stringify(routeBody),
  };
}
