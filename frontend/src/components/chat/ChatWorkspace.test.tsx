import { describe, expect, it } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import ChatWorkspace from "./ChatWorkspace";

describe("ChatWorkspace", () => {
  it("provides a mobile conversation drawer without removing the desktop sidebar", () => {
    render(
      <ChatWorkspace
        sidebar={<div>Conversation list</div>}
        title="New conversation"
        subtitle="Compare models"
        actions={<button type="button">Models</button>}
        composer={<textarea aria-label="Prompt" />}
      >
        <p>Chat content</p>
      </ChatWorkspace>,
    );

    const sidebar = screen.getByRole("complementary", { name: "Conversations" });
    expect(sidebar).toHaveClass("-translate-x-full");
    expect(screen.getByRole("button", { name: "Open conversations" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Open conversations" }));
    expect(sidebar).toHaveClass("translate-x-0");
    expect(within(sidebar).getByText("Conversation list")).toBeInTheDocument();

    fireEvent.click(within(sidebar).getByRole("button", { name: "Close conversations" }));
    expect(sidebar).toHaveClass("-translate-x-full");
    expect(screen.getByRole("button", { name: "Open conversations" })).toBeInTheDocument();
  });
});
