/**
 * App-wide, promise-based confirmation dialog (Constitution VIII: never use the browser's native
 * `window.confirm`/`alert`). A single <ConfirmHost /> mounted at the root subscribes to this store
 * and renders the styled dialog; any client code can call `confirmDialog(...)` and await the result.
 */

export interface ConfirmOptions {
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Render the confirm action in a destructive (red) style. */
  danger?: boolean;
}

export interface ConfirmRequest extends ConfirmOptions {
  id: number;
  resolve: (confirmed: boolean) => void;
}

type Listener = () => void;

let current: ConfirmRequest | null = null;
let nextId = 1;
const listeners = new Set<Listener>();

function emit() {
  for (const listener of listeners) listener();
}

export function subscribeConfirm(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getConfirmSnapshot(): ConfirmRequest | null {
  return current;
}

/** Open a confirmation dialog and resolve to the user's choice. */
export function confirmDialog(options: ConfirmOptions): Promise<boolean> {
  // If a dialog is already open, resolve it as cancelled before replacing it.
  if (current) current.resolve(false);
  return new Promise<boolean>((resolve) => {
    current = { id: nextId++, ...options, resolve };
    emit();
  });
}

/** Internal: called by the host when the user picks an option. */
export function settleConfirm(id: number, confirmed: boolean) {
  if (!current || current.id !== id) return;
  current.resolve(confirmed);
  current = null;
  emit();
}
