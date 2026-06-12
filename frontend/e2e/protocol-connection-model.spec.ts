import { expect, test, type Page, type Route } from "@playwright/test";

const connectionId = "11111111-1111-4111-8111-111111111111";
const configuredModelId = "22222222-2222-4222-8222-222222222222";
const sessionId = "33333333-3333-4333-8333-333333333333";
const now = "2026-06-12T00:00:00Z";

const pageResponse = <T>(items: T[]) => ({
  items,
  page: 0,
  size: 100,
  totalElements: items.length,
  totalPages: items.length === 0 ? 0 : 1,
});

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

async function installApi(page: Page) {
  let connectionExists = false;
  let modelExists = false;
  let sessionExists = false;
  let turnComplete = false;

  const connection = {
    id: connectionId,
    protocol: "openai-compatible",
    label: "E2E connection",
    baseUrl: "https://api.example.com/v1",
    hasKey: true,
    modelCount: 1,
    createdAt: now,
    updatedAt: now,
  };
  const model = {
    id: configuredModelId,
    connectionId,
    connectionLabel: connection.label,
    protocol: connection.protocol,
    baseUrl: connection.baseUrl,
    modelId: "custom-model",
    displayName: "Custom model",
    capabilityOverrides: {},
    capabilityMatrix: {
      input_modalities: ["text"],
      output_modalities: ["text"],
      context_length_tokens: 128000,
      supports_streaming: true,
      supports_function_calling: false,
      supports_system_prompt: true,
      supports_video_input: false,
    },
    customParams: { temperature: 0.2 },
    isEnabled: true,
    sortOrder: 0,
    createdAt: now,
    updatedAt: now,
  };
  const session = { id: sessionId, title: "Snapshot chat", createdAt: now, updatedAt: now };
  const sessionDetail = () => ({
    id: sessionId,
    title: session.title,
    turns: turnComplete ? [{
      id: "44444444-4444-4444-8444-444444444444",
      sequenceNum: 1,
      promptText: "Keep this history",
      selectedModelIds: [model.modelId],
      selectedConfiguredModelIds: [configuredModelId],
      responses: [{
        configuredModelId,
        modelId: model.modelId,
        modelDisplayName: model.displayName,
        protocol: model.protocol,
        connectionLabel: connection.label,
        status: "complete",
        responseText: "Historical answer",
        reasoningText: null,
        errorMessage: null,
        inputTokens: 3,
        outputTokens: 2,
        latencyMs: 12,
      }],
      createdAt: now,
    }] : [],
  });

  await page.route("**/api/v2/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (path === "/api/v2/protocols") {
      return json(route, pageResponse([{
        id: "openai-compatible",
        displayName: "OpenAI-compatible",
        defaultBaseUrl: "https://api.example.com/v1",
        capabilities: model.capabilityMatrix,
      }]));
    }
    if (path === "/api/v2/catalogue") return json(route, pageResponse([]));
    if (path === "/api/v2/connections" && method === "GET") {
      return json(route, pageResponse(connectionExists ? [connection] : []));
    }
    if (path === "/api/v2/connections" && method === "POST") {
      connectionExists = true;
      return json(route, connection, 201);
    }
    if (path === `/api/v2/connections/${connectionId}` && method === "DELETE") {
      connectionExists = false;
      modelExists = false;
      return route.fulfill({ status: 204 });
    }
    if (path === "/api/v2/configured-models" && method === "GET") {
      return json(route, pageResponse(modelExists ? [model] : []));
    }
    if (path === "/api/v2/configured-models" && method === "POST") {
      modelExists = true;
      return json(route, model, 201);
    }
    if (path === "/api/v2/user/preferences" && method === "GET") {
      return json(route, { lastSelectedConfiguredModelId: null });
    }
    if (path === "/api/v2/user/preferences" && method === "PATCH") {
      return json(route, { lastSelectedConfiguredModelId: configuredModelId });
    }
    if (path === "/api/v2/chat/sessions" && method === "GET") {
      return json(route, pageResponse(sessionExists ? [session] : []));
    }
    if (path === "/api/v2/chat/sessions" && method === "POST") {
      sessionExists = true;
      return json(route, session, 201);
    }
    if (path === `/api/v2/chat/sessions/${sessionId}` && method === "GET") {
      return json(route, sessionDetail());
    }
    if (path === `/api/v2/chat/sessions/${sessionId}/turns` && method === "POST") {
      turnComplete = true;
      const events = [
        { event: "turn_created", turnId: "44444444-4444-4444-8444-444444444444", sequenceNum: 1 },
        { event: "token", configuredModelId, modelId: model.modelId, delta: "Historical answer" },
        { event: "model_complete", configuredModelId, modelId: model.modelId, inputTokens: 3, outputTokens: 2, latencyMs: 12 },
        { event: "all_complete" },
      ];
      return route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: events.map((event) => `data: ${JSON.stringify(event)}\n\n`).join(""),
      });
    }

    return json(route, { code: "NOT_FOUND", message: `${method} ${path} was not mocked` }, 404);
  });
}

test("connection, model, chat, deletion, and historical snapshot stay coherent", async ({ page, context }) => {
  await context.addCookies([{
    name: "auth_token",
    value: "e2e-token",
    domain: "localhost",
    path: "/",
  }]);
  await installApi(page);

  await page.goto("/settings/models");
  await page.getByRole("button", { name: "Add connection" }).click();
  await page.getByLabel("Label").fill("E2E connection");
  await page.getByLabel("API key").fill("never-render-this-key");
  await page.getByRole("button", { name: "Add connection" }).click();
  await expect(page.getByRole("heading", { name: "E2E connection" })).toBeVisible();
  await expect(page.getByText("never-render-this-key")).toHaveCount(0);

  await page.getByRole("button", { name: "Add model" }).click();
  await page.getByLabel("Model ID").fill("custom-model");
  await page.getByLabel("Display name").fill("Custom model");
  await page.getByLabel("Custom request parameters").fill('{"temperature":0.2}');
  await page.getByRole("button", { name: "Add model" }).click();
  await expect(page.getByText("Custom model")).toBeVisible();

  await page.getByRole("link", { name: "Back to chat" }).click();
  await expect(page.getByRole("button", { name: /Custom model/ })).toHaveAttribute("aria-pressed", "true");
  await page.getByPlaceholder(/Ask all selected models/).fill("Keep this history");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("Historical answer")).toBeVisible();

  await page.getByRole("link", { name: "Manage models" }).click();
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Delete connection" }).click();
  await expect(page.getByRole("heading", { name: "No connections yet" })).toBeVisible();

  await page.goto(`/chat?session=${sessionId}`);
  await expect(page.getByText("Keep this history")).toBeVisible();
  await expect(page.getByText("Historical answer")).toBeVisible();
  await expect(page.getByText("Custom model")).toBeVisible();
  await expect(page.getByText("E2E connection")).toBeVisible();
});
