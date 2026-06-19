"use client";

import { useRef, useState } from "react";
import { AlertTriangle, Download, Upload } from "lucide-react";
import Button from "@/components/ui/Button";
import AdminShell from "@/components/admin/AdminShell";
import { getToken } from "@/lib/api/auth";
import { confirmDialog } from "@/lib/ui/confirm";
import {
  downloadBlob,
  exportAll,
  importBundle,
  newIdempotencyKey,
  type MigrationImportResult,
} from "@/lib/api/migration";

const inputClass =
  "w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 shadow-sm focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";
const cardClass = "rounded-2xl border border-stone-200 bg-white shadow-sm";
const cardHeaderClass = "border-b border-stone-200 bg-stone-50/70 p-4";
const accent = "!bg-[#c96442] hover:!bg-[#b55538]";
const MIN_PASSPHRASE = 16;

export default function MigrationPage() {
  // Export form
  const [ack, setAck] = useState(false);
  const [exportPass, setExportPass] = useState("");
  const [exportConfirm, setExportConfirm] = useState("");
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exportSuccess, setExportSuccess] = useState<string | null>(null);

  // Import form
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importPass, setImportPass] = useState("");
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [importResult, setImportResult] = useState<MigrationImportResult | null>(null);
  // One stable idempotency key per selected file, reused across retries of that import.
  const importKey = useRef<string | null>(null);

  async function onExport() {
    setExportError(null);
    setExportSuccess(null);
    if (!ack) {
      setExportError("Please acknowledge that the export contains sensitive data.");
      return;
    }
    if (exportPass && exportPass.length < MIN_PASSPHRASE) {
      setExportError(`Passphrase must be at least ${MIN_PASSPHRASE} characters.`);
      return;
    }
    if (exportPass && exportPass !== exportConfirm) {
      setExportError("Passphrase and confirmation do not match.");
      return;
    }
    const token = getToken();
    if (!token) {
      setExportError("Not authenticated.");
      return;
    }
    const confirmed = await confirmDialog({
      title: "Export all data?",
      message:
        "This produces an encrypted archive of every user's Quests, media, and connection keys. " +
        "Store it securely and only import it into a deployment you trust.",
      confirmLabel: "Export",
      danger: true,
    });
    if (!confirmed) return;
    setExporting(true);
    try {
      const blob = await exportAll(
        { acknowledgeSensitiveExport: true, passphrase: exportPass || undefined },
        token,
      );
      downloadBlob(blob, `octopus-export-${Date.now()}.octopus`);
      setExportSuccess("Export downloaded. Keep the archive and passphrase together and safe.");
      // Never keep the secret in component state after a successful submission.
      setExportPass("");
      setExportConfirm("");
    } catch (cause) {
      setExportError(messageFor(cause, "Export failed."));
    } finally {
      setExporting(false);
    }
  }

  function onSelectFile(file: File | null) {
    setImportFile(file);
    setImportResult(null);
    setImportError(null);
    importKey.current = file ? newIdempotencyKey() : null;
  }

  async function onImport() {
    setImportError(null);
    setImportResult(null);
    if (!importFile) {
      setImportError("Choose a .octopus archive to import.");
      return;
    }
    const token = getToken();
    if (!token) {
      setImportError("Not authenticated.");
      return;
    }
    if (!importKey.current) importKey.current = newIdempotencyKey();
    setImporting(true);
    try {
      const result = await importBundle(importFile, importPass || undefined, importKey.current, token);
      setImportResult(result);
      setImportPass("");
    } catch (cause) {
      setImportError(messageFor(cause, "Import failed."));
    } finally {
      setImporting(false);
    }
  }

  return (
    <AdminShell
      title="Data migration"
      description="Export every user's Quests, media, and connections into one encrypted archive, then import it into another deployment under your account."
    >
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Export */}
        <section className={cardClass}>
          <div className={cardHeaderClass}>
            <h2 className="flex items-center gap-2 text-sm font-semibold text-stone-800">
              <Download className="h-4 w-4 text-[#b75536]" /> Export
            </h2>
          </div>
          <div className="space-y-4 p-4">
            <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <p>
                The archive contains all users&apos; data and decryptable connection keys. It is
                encrypted with your passphrase — lose the passphrase and the archive is unrecoverable.
              </p>
            </div>
            <label className="flex items-start gap-2 text-sm text-stone-700">
              <input
                type="checkbox"
                checked={ack}
                onChange={(e) => setAck(e.target.checked)}
                className="mt-0.5 h-4 w-4 rounded border-stone-300 text-[#c96442] focus:ring-[#c96442]"
              />
              I understand this exports sensitive data for every user.
            </label>
            <div>
              <label className="mb-1 block text-xs font-medium text-stone-600">
                Passphrase (optional if the server configures one)
              </label>
              <input
                type="password"
                value={exportPass}
                onChange={(e) => setExportPass(e.target.value)}
                placeholder={`At least ${MIN_PASSPHRASE} characters`}
                className={inputClass}
                autoComplete="new-password"
              />
            </div>
            {exportPass && (
              <div>
                <label className="mb-1 block text-xs font-medium text-stone-600">Confirm passphrase</label>
                <input
                  type="password"
                  value={exportConfirm}
                  onChange={(e) => setExportConfirm(e.target.value)}
                  placeholder="Re-enter passphrase"
                  className={inputClass}
                  autoComplete="new-password"
                />
              </div>
            )}
            {exportError && <Banner kind="error">{exportError}</Banner>}
            {exportSuccess && <Banner kind="success">{exportSuccess}</Banner>}
            <Button className={accent} isLoading={exporting} disabled={exporting} onClick={onExport}>
              Export all data
            </Button>
          </div>
        </section>

        {/* Import */}
        <section className={cardClass}>
          <div className={cardHeaderClass}>
            <h2 className="flex items-center gap-2 text-sm font-semibold text-stone-800">
              <Upload className="h-4 w-4 text-[#b75536]" /> Import
            </h2>
          </div>
          <div className="space-y-4 p-4">
            <p className="text-sm text-stone-600">
              Upload an archive exported from another deployment. All Quests and connections are
              imported under your account; retrying the same upload will not duplicate data.
            </p>
            <div>
              <label className="mb-1 block text-xs font-medium text-stone-600">Archive (.octopus)</label>
              <input
                type="file"
                accept=".octopus,application/zip"
                onChange={(e) => onSelectFile(e.target.files?.[0] ?? null)}
                className="block w-full text-sm text-stone-700 file:mr-3 file:rounded-lg file:border-0 file:bg-stone-100 file:px-3 file:py-2 file:text-sm file:font-medium file:text-stone-700 hover:file:bg-stone-200"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-stone-600">
                Passphrase (optional if the server configures one)
              </label>
              <input
                type="password"
                value={importPass}
                onChange={(e) => setImportPass(e.target.value)}
                className={inputClass}
                autoComplete="new-password"
              />
            </div>
            {importError && <Banner kind="error">{importError}</Banner>}
            {importResult && (
              <Banner kind="success">
                Imported {importResult.questsImported} Quest(s), {importResult.connectionsImported}{" "}
                connection(s)
                {importResult.connectionsRenamed > 0 && ` (${importResult.connectionsRenamed} renamed)`}, and{" "}
                {importResult.mediaImported} media object(s).
              </Banner>
            )}
            <Button
              className={accent}
              isLoading={importing}
              disabled={importing || !importFile}
              onClick={onImport}
            >
              Import archive
            </Button>
          </div>
        </section>
      </div>
    </AdminShell>
  );
}

function Banner({ kind, children }: { kind: "error" | "success"; children: React.ReactNode }) {
  const styles =
    kind === "error"
      ? "border-red-200 bg-red-50 text-red-700"
      : "border-green-200 bg-green-50 text-green-700";
  return <div className={`rounded-xl border p-3 text-sm ${styles}`}>{children}</div>;
}

function messageFor(cause: unknown, fallback: string): string {
  if (cause instanceof Error && cause.message) return cause.message;
  return fallback;
}
