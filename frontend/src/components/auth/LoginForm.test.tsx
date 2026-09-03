import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import LoginForm from "./LoginForm";

describe("LoginForm", () => {
  it("offers a guest mode entry point", () => {
    render(<LoginForm />);

    expect(screen.getByRole("link", { name: "Continue as guest" })).toHaveAttribute("href", "/chat");
  });
});
