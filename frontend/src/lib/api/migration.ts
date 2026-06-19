import { apiUrl } from "@/lib/api/base";

// Same-origin client for the admin migration endpoints (feature 008). All calls go through the Next
// proxy at /api/... so large artifact bodies stream (see app/api/[...path]/route.ts).

/** A fresh, opaque idempotency key for an import attempt; reuse the same value across retries. */
export function newIdempotencyKey(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** Triggers a browser download for a blob (the streamed encrypted artifact). */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export interface MigrationExportRequest {
  acknowledgeSensitiveExport: boolean;
  /** Omit when the deployment configures MIGRATION_ARTIFACT_PASSPHRASE. */
  passphrase?: string;
}

export interface MigrationImportResult {
  questsImported: number;
  connectionsImported: number;
  connectionsRenamed: number;
  mediaImported: number;
  formatVersion: number;
}

/** Streams the encrypted artifact and returns it as a downloadable blob. */
export async function exportAll(body: MigrationExportRequest, token: string): Promise<Blob> {
  const response = await fetch(apiUrl("/api/v2/admin/migration/export"), {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw Object.assign(new Error(error.message ?? "Export failed"), { status: response.status });
  }
  return response.blob();
}

/** Uploads an artifact for import. `idempotencyKey` is reused across retries of the same import. */
export async function importBundle(
  file: File,
  passphrase: string | undefined,
  idempotencyKey: string,
  token: string,
): Promise<MigrationImportResult> {
  const form = new FormData();
  form.append("file", file);
  if (passphrase) form.append("passphrase", passphrase);
  const response = await fetch(apiUrl("/api/v2/admin/migration/import"), {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Idempotency-Key": idempotencyKey },
    body: form,
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw Object.assign(new Error(error.message ?? "Import failed"), { status: response.status });
  }
  return response.json() as Promise<MigrationImportResult>;
}
