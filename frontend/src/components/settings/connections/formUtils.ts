export function parseJsonObject(value: string, field: string): Record<string, unknown> {
  if (!value.trim()) return {};
  const parsed: unknown = JSON.parse(value);
  if (parsed === null || Array.isArray(parsed) || typeof parsed !== "object") {
    throw new Error(`${field} must be a JSON object`);
  }
  return parsed as Record<string, unknown>;
}

export function prettyJson(value: Record<string, unknown>): string {
  return Object.keys(value).length === 0 ? "" : JSON.stringify(value, null, 2);
}

export function parseOptionalPrice(value: string, field: string): number | null {
  if (!value.trim()) return null;
  const price = Number(value);
  if (!Number.isFinite(price) || price < 0) throw new Error(`${field} must be a non-negative number`);
  return price;
}

export function normalizeCurrency(value: string, hasPrice: boolean): string | null {
  const currency = value.trim().toUpperCase();
  if (!hasPrice && !currency) return null;
  if (!/^[A-Z]{3}$/.test(currency)) throw new Error("Currency must be a three-letter code");
  return currency;
}

// --- Media capability toggles (feature 007) -------------------------------------------------------

export interface ModalityToggles {
  image: boolean;
  video: boolean;
  audio: boolean;
}

/** Derive the image/video/audio toggle state from a model's `input_modalities`. */
export function togglesFromOverrides(overrides: Record<string, unknown>): ModalityToggles {
  const mods = Array.isArray(overrides.input_modalities) ? (overrides.input_modalities as unknown[]) : [];
  const has = (m: string) => mods.includes(m);
  return { image: has("image"), video: has("video"), audio: has("audio") };
}

/** Capability overrides with `input_modalities` removed, pretty-printed for the advanced JSON box. */
export function advancedOverridesJson(overrides: Record<string, unknown>): string {
  const rest = { ...overrides };
  delete rest.input_modalities;
  return prettyJson(rest);
}

/**
 * Combine the advanced-overrides JSON with the modality toggles. When `explicit` is false (Add) and no
 * modality is on, `input_modalities` is omitted so the backend can auto-detect from the catalogue.
 * When `explicit` is true (Edit), it is always written so a toggle can turn a modality off.
 */
export function buildCapabilityOverrides(
  advancedJson: string,
  toggles: ModalityToggles,
  explicit: boolean,
): Record<string, unknown> {
  const base = parseJsonObject(advancedJson, "Advanced capability overrides");
  delete base.input_modalities;
  const on = (["image", "video", "audio"] as const).filter((m) => toggles[m]);
  if (on.length > 0 || explicit) {
    base.input_modalities = ["text", ...on];
  }
  return base;
}
