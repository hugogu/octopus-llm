"use client";

import type { ReactNode } from "react";

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
  return (
    <div className="flex h-screen max-h-screen bg-[#faf9f5]" data-testid={testId}>
      {sidebar}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-b border-stone-200 px-6 py-3">
          <div className="flex items-center justify-between gap-4">
            <div className="min-w-0">
              <h1 className="truncate text-base font-semibold text-stone-900">{title}</h1>
              <p className="truncate text-xs text-stone-500">{subtitle}</p>
            </div>
            <div className="flex shrink-0 items-center gap-1.5">{actions}</div>
          </div>
        </header>

        <div className="flex-1 overflow-y-auto px-6">
          <div className="flex w-full flex-col gap-6 py-6">{children}</div>
        </div>

        <div className="border-t border-stone-200 bg-[#faf9f5] px-6 py-2">{composer}</div>
      </div>
    </div>
  );
}
