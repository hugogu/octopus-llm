/**
 * Client-side media limits (feature 007). These defaults mirror the backend `storage_settings`
 * defaults and give instant feedback before any upload. US5 replaces them with admin-configured
 * values fetched from the server.
 */
export interface MediaLimits {
  maxImageBytes: number;
  maxVideoBytes: number;
  maxAudioBytes: number;
  maxFilesPerPrompt: number;
  maxTotalBytesPerPrompt: number;
}

export const DEFAULT_MEDIA_LIMITS: MediaLimits = {
  maxImageBytes: 1_048_576, // 1 MB
  maxVideoBytes: 10_485_760, // 10 MB
  maxAudioBytes: 10_485_760, // 10 MB
  maxFilesPerPrompt: 5,
  maxTotalBytesPerPrompt: 15_728_640, // 15 MB
};

export type MediaKind = "image" | "video" | "audio" | "file";

export function mediaKindOf(file: File): MediaKind {
  if (file.type.startsWith("image/")) return "image";
  if (file.type.startsWith("video/")) return "video";
  if (file.type.startsWith("audio/")) return "audio";
  return "file";
}

function perTypeLimit(kind: MediaKind, limits: MediaLimits): number {
  switch (kind) {
    case "video":
      return limits.maxVideoBytes;
    case "audio":
      return limits.maxAudioBytes;
    default:
      return limits.maxImageBytes;
  }
}

export function formatBytes(bytes: number): string {
  if (bytes >= 1_048_576) return `${(bytes / 1_048_576).toFixed(1)} MB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${bytes} B`;
}

/**
 * Validate one candidate file against the per-type size limit and the per-prompt ceiling (count +
 * combined size, given what is already attached). Returns an error message, or null if acceptable.
 */
export function validateCandidate(
  file: File,
  existing: File[],
  limits: MediaLimits = DEFAULT_MEDIA_LIMITS,
): string | null {
  const kind = mediaKindOf(file);
  const perType = perTypeLimit(kind, limits);
  if (file.size > perType) {
    return `${file.name} is ${formatBytes(file.size)}; the ${kind} limit is ${formatBytes(perType)}.`;
  }
  if (existing.length + 1 > limits.maxFilesPerPrompt) {
    return `At most ${limits.maxFilesPerPrompt} attachments per message.`;
  }
  const total = existing.reduce((sum, f) => sum + f.size, 0) + file.size;
  if (total > limits.maxTotalBytesPerPrompt) {
    return `Attachments would exceed the ${formatBytes(limits.maxTotalBytesPerPrompt)} per-message total.`;
  }
  return null;
}
