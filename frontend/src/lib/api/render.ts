/**
 * Client for the self-hosted PlantUML render proxy (clarification Q1). The browser posts source to the
 * same-origin `/api/v2/render/plantuml` route — which forwards to the internal PlantUML server — so
 * model content never reaches a third-party service. Used identically by the in-app conversation and
 * the public share view.
 */
export async function renderPlantUml(source: string): Promise<string> {
  const res = await fetch('/api/v2/render/plantuml', {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body: source,
  });
  if (!res.ok) {
    throw new Error(`PlantUML render failed (${res.status})`);
  }
  return res.text();
}
