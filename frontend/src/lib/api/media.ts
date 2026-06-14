import { apiUrl } from "@/lib/api/base";
import type { MediaReference } from "@/lib/types/api";

/**
 * Upload one media file (image/video/audio) to the uniform media endpoint (feature 007). Returns an
 * opaque public reference; the chat submit then attaches it by id. Per-type size limits are enforced
 * server-side; oversize uploads reject with status 413.
 */
export async function uploadMedia(file: File, token: string): Promise<MediaReference> {
  const form = new FormData();
  form.append("file", file);
  const response = await fetch(apiUrl("/api/v2/media"), {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw Object.assign(new Error(error.message ?? "Upload failed"), {
      status: response.status,
      details: error.details,
    });
  }
  return response.json() as Promise<MediaReference>;
}

/** Delete an orphaned (not-yet-sent) upload discarded from the attachment tray (feature 007). */
export async function deleteMedia(mediaId: string, token: string): Promise<void> {
  const response = await fetch(apiUrl(`/api/v2/media/${mediaId}`), {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok && response.status !== 404) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw Object.assign(new Error(error.message ?? "Delete failed"), { status: response.status });
  }
}
