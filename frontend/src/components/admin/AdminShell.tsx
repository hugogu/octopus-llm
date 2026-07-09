"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowLeft, Cable, DatabaseBackup, HardDrive, Info, Users } from "lucide-react";

const TABS = [
  { href: "/admin/users", label: "Users", icon: Users },
  { href: "/admin/connections", label: "Built-in connections", icon: Cable },
  { href: "/admin/storage", label: "Storage", icon: HardDrive },
  { href: "/admin/migration", label: "Data migration", icon: DatabaseBackup },
  { href: "/admin/site", label: "Site info", icon: Info },
] as const;

interface Props {
  title: string;
  description: string;
  actions?: ReactNode;
  children: ReactNode;
}

/**
 * Shared chrome for admin pages: warm gradient canvas, eyebrow + title header, a connected tab bar
 * between the admin sections, and a back-to-chat link. Keeps every admin page visually consistent
 * with the rest of the app (matches the Model settings page treatment).
 */
export default function AdminShell({ title, description, actions, children }: Props) {
  const pathname = usePathname();

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,_#f8e9dc,_transparent_30%),linear-gradient(180deg,#faf9f5,#f2f0e8)] px-4 py-8 sm:px-6">
      <div className="mx-auto max-w-5xl">
        <header className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#b75536]">Administration</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight text-stone-900">{title}</h1>
            <p className="mt-2 max-w-2xl text-sm text-stone-600">{description}</p>
          </div>
          <div className="flex items-center gap-2">
            <Link
              href="/chat"
              className="inline-flex items-center rounded-lg px-3 py-2 text-sm font-medium text-stone-600 hover:bg-white"
            >
              <ArrowLeft className="mr-1.5 h-4 w-4" /> Back to chat
            </Link>
            {actions}
          </div>
        </header>

        <nav className="mb-6 inline-flex rounded-xl border border-stone-200 bg-white/70 p-1 shadow-sm">
          {TABS.map((tab) => {
            const active = pathname?.startsWith(tab.href) ?? false;
            const Icon = tab.icon;
            return (
              <Link
                key={tab.href}
                href={tab.href}
                className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  active ? "bg-[#c96442] text-white shadow-sm" : "text-stone-600 hover:bg-white hover:text-stone-900"
                }`}
              >
                <Icon className="h-4 w-4" />
                {tab.label}
              </Link>
            );
          })}
        </nav>

        {children}
      </div>
    </main>
  );
}
