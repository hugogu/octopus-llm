"use client";

import { useEffect, useState, type ReactNode } from "react";
import { Menu, X } from "lucide-react";

interface ChatWorkspaceProps {
  sidebar: ReactNode;
  title: ReactNode;
  subtitle: ReactNode;
  actions: ReactNode;
  children: ReactNode;
  composer: ReactNode;
  testId?: string;
}

/** Shared chat shell used by authenticated and anonymous conversations. */
export default function ChatWorkspace({
  sidebar,
  title,
  subtitle,
  actions,
  children,
  composer,
  testId,
}: ChatWorkspaceProps) {
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);

  useEffect(() => {
    if (!mobileSidebarOpen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMobileSidebarOpen(false);
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [mobileSidebarOpen]);

  return (
    <div className="flex h-[100dvh] max-h-[100dvh] min-h-0 overflow-hidden bg-[#faf9f5]" data-testid={testId}>
      <div
        className={`fixed inset-0 z-40 md:static md:flex md:shrink-0 ${
          mobileSidebarOpen
            ? "pointer-events-auto visible"
            : "pointer-events-none invisible md:pointer-events-auto md:visible"
        }`}
      >
        <button
          type="button"
          aria-label="Close conversations"
          onClick={() => setMobileSidebarOpen(false)}
          className={`absolute inset-0 bg-stone-900/25 transition-opacity md:hidden ${
            mobileSidebarOpen ? "opacity-100" : "opacity-0"
          }`}
        />
        <aside
          aria-label="Conversations"
          onClick={() => setMobileSidebarOpen(false)}
          className={`relative z-10 h-full w-[min(18rem,86vw)] transform transition-transform duration-200 md:static md:w-auto md:translate-x-0 ${
            mobileSidebarOpen ? "translate-x-0" : "-translate-x-full"
          }`}
        >
          <button
            type="button"
            aria-label="Close conversations"
            onClick={() => setMobileSidebarOpen(false)}
            className="absolute right-2 top-2 z-20 rounded-lg bg-white/80 p-2 text-stone-500 shadow-sm md:hidden"
          >
            <X className="h-4 w-4" />
          </button>
          {sidebar}
        </aside>
      </div>
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <header className="shrink-0 border-b border-stone-200 px-3 py-2 sm:px-6 sm:py-3">
          <div className="flex min-w-0 flex-col gap-2 md:flex-row md:items-center md:justify-between md:gap-4">
            <div className="flex min-w-0 items-center gap-2">
              <button
                type="button"
                aria-label="Open conversations"
                onClick={() => setMobileSidebarOpen(true)}
                className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-stone-200 bg-white text-stone-600 shadow-sm transition hover:text-stone-900 md:hidden"
              >
                <Menu className="h-4 w-4" />
              </button>
              <div className="min-w-0">
              <h1 className="truncate text-base font-semibold text-stone-900">{title}</h1>
              <p className="truncate text-xs text-stone-500">{subtitle}</p>
            </div>
            </div>
            <div className="min-w-0 max-w-full overflow-x-auto pb-0.5 md:max-w-none md:overflow-visible">
              <div className="flex w-max min-w-full items-center justify-end gap-1.5 md:w-auto md:min-w-0">{actions}</div>
            </div>
          </div>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-3 sm:px-6">
          <div className="flex w-full min-w-0 flex-col gap-4 py-4 sm:gap-6 sm:py-6">{children}</div>
        </div>

        <div className="shrink-0 border-t border-stone-200 bg-[#faf9f5] px-3 pb-[calc(0.5rem+env(safe-area-inset-bottom))] pt-2 sm:px-6 sm:py-2">{composer}</div>
      </div>
    </div>
  );
}
