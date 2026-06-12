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
