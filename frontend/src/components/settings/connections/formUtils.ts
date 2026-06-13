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
