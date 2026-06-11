function getBrowserApiBase(): string {
  return window.location.origin;
}

function getServerApiBase(): string {
  return process.env.INTERNAL_API_URL ?? process.env.NEXT_PUBLIC_API_URL ?? "http://127.0.0.1:8080";
}

export function apiUrl(path: string): string {
  const base = typeof window === "undefined" ? getServerApiBase() : getBrowserApiBase();
  return `${base}${path}`;
}
