import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import MigrationPage from "./MigrationPage";

// AdminShell pulls in next/navigation + next/link; stub it to a passthrough so the test focuses on
// the migration form behaviour.
vi.mock("@/components/admin/AdminShell", () => ({
  default: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/lib/api/auth", () => ({ getToken: () => "test-token" }));
vi.mock("@/lib/ui/confirm", () => ({ confirmDialog: vi.fn(async () => true) }));

const exportAll = vi.fn();
const importBundle = vi.fn();
const downloadBlob = vi.fn();
vi.mock("@/lib/api/migration", () => ({
  exportAll: (...args: unknown[]) => exportAll(...args),
  importBundle: (...args: unknown[]) => importBundle(...args),
  downloadBlob: (...args: unknown[]) => downloadBlob(...args),
  newIdempotencyKey: () => "fixed-key",
}));

const longPass = "a-very-long-passphrase-123";

describe("MigrationPage", () => {
  beforeEach(() => {
    exportAll.mockReset();
    importBundle.mockReset();
    downloadBlob.mockReset();
  });

  it("requires the sensitive-data acknowledgement before exporting", async () => {
    render(<MigrationPage />);
    fireEvent.click(screen.getByRole("button", { name: /export all data/i }));
    expect(await screen.findByText(/acknowledge/i)).toBeInTheDocument();
    expect(exportAll).not.toHaveBeenCalled();
  });

  it("blocks a too-short passphrase client-side", async () => {
    render(<MigrationPage />);
    fireEvent.click(screen.getByLabelText(/I understand/i));
    fireEvent.change(screen.getByPlaceholderText(/at least 16/i), { target: { value: "short" } });
    fireEvent.click(screen.getByRole("button", { name: /export all data/i }));
    expect(await screen.findByText(/at least 16 characters/i)).toBeInTheDocument();
    expect(exportAll).not.toHaveBeenCalled();
  });

  it("blocks a passphrase/confirmation mismatch", async () => {
    render(<MigrationPage />);
    fireEvent.click(screen.getByLabelText(/I understand/i));
    fireEvent.change(screen.getByPlaceholderText(/at least 16/i), { target: { value: longPass } });
    fireEvent.change(screen.getByPlaceholderText(/re-enter passphrase/i), { target: { value: "different-but-long-enough" } });
    fireEvent.click(screen.getByRole("button", { name: /export all data/i }));
    expect(await screen.findByText(/do not match/i)).toBeInTheDocument();
    expect(exportAll).not.toHaveBeenCalled();
  });

  it("downloads the archive and clears the passphrase after a successful export", async () => {
    exportAll.mockResolvedValue(new Blob(["zip"]));
    render(<MigrationPage />);
    fireEvent.click(screen.getByLabelText(/I understand/i));
    const passInput = screen.getByPlaceholderText(/at least 16/i) as HTMLInputElement;
    fireEvent.change(passInput, { target: { value: longPass } });
    fireEvent.change(screen.getByPlaceholderText(/re-enter passphrase/i), { target: { value: longPass } });
    fireEvent.click(screen.getByRole("button", { name: /export all data/i }));

    await waitFor(() => expect(downloadBlob).toHaveBeenCalled());
    expect(exportAll).toHaveBeenCalledWith(
      { acknowledgeSensitiveExport: true, passphrase: longPass },
      "test-token",
    );
    // Secret must not linger in the field after submission.
    expect(passInput.value).toBe("");
  });

  it("shows the imported counts after a successful import", async () => {
    importBundle.mockResolvedValue({
      questsImported: 3,
      connectionsImported: 2,
      connectionsRenamed: 1,
      mediaImported: 5,
      formatVersion: 1,
    });
    const { container } = render(<MigrationPage />);
    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(["bytes"], "data.octopus");
    fireEvent.change(fileInput, { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: /import archive/i }));

    expect(await screen.findByText(/Imported 3 Quest/i)).toBeInTheDocument();
    expect(importBundle).toHaveBeenCalledWith(file, undefined, "fixed-key", "test-token");
  });
});
