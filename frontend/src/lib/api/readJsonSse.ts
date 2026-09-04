type JsonPayload = Record<string, unknown>;

interface ReadJsonSseOptions {
  onMalformed?: () => void;
}

/** Reads browser Fetch streams using the SSE record boundary instead of assuming one network chunk per event. */
export async function readJsonSse<T extends JsonPayload>(
  response: Response,
  onEvent: (event: T) => void,
  options: ReadJsonSseOptions = {},
): Promise<void> {
  const reader = response.body?.getReader();
  if (!reader) throw new Error("No response body");

  const decoder = new TextDecoder();
  let buffer = "";
  let eventName: string | undefined;
  let dataLines: string[] = [];

  const dispatch = () => {
    if (dataLines.length === 0) {
      eventName = undefined;
      return;
    }
    const raw = dataLines.join("\n");
    dataLines = [];
    try {
      const payload = JSON.parse(raw) as T;
      if (eventName && payload.event == null) {
        onEvent({ ...payload, event: eventName } as T);
      } else {
        onEvent(payload);
      }
    } catch {
      options.onMalformed?.();
    } finally {
      eventName = undefined;
    }
  };

  const processLine = (line: string) => {
    const normalized = line.endsWith("\r") ? line.slice(0, -1) : line;
    if (normalized === "") {
      dispatch();
      return;
    }
    if (normalized.startsWith(":")) return;
    if (normalized.startsWith("event:")) {
      eventName = normalized.slice(6).trim();
      return;
    }
    if (normalized.startsWith("data:")) {
      dataLines.push(normalized.slice(5).trimStart());
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";
    lines.forEach(processLine);
  }

  buffer += decoder.decode();
  if (buffer) processLine(buffer);
  // A server or proxy may close immediately after the final data line without sending the blank
  // separator. Treat EOF as an event boundary so the last response is not silently discarded.
  dispatch();
}
