"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowLeft, BarChart3, LockKeyhole, UserRound } from "lucide-react";

const tabs: ReadonlyArray<{
  href: string;
  label: string;
  icon: typeof UserRound;
  exact?: boolean;
}> = [
  { href: "/account", label: "Profile", icon: UserRound, exact: true },
  { href: "/account/security", label: "Security", icon: LockKeyhole },
  { href: "/account/analytics", label: "Analytics", icon: BarChart3 },
] as const;

export default function AccountShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,_#f8e9dc,_transparent_30%),linear-gradient(180deg,#faf9f5,#f2f0e8)] px-4 py-8 sm:px-6">
      <div className="mx-auto max-w-5xl">
        <header className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#b75536]">Personal center</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight text-stone-900">Your account</h1>
            <p className="mt-2 text-sm text-stone-600">Manage your profile, security, and usage history.</p>
          </div>
          <Link href="/chat" className="inline-flex items-center rounded-lg px-3 py-2 text-sm font-medium text-stone-600 hover:bg-white">
            <ArrowLeft className="mr-1.5 h-4 w-4" /> Back to chat
          </Link>
        </header>
        <nav className="mb-6 flex w-fit max-w-full overflow-x-auto rounded-xl border border-stone-200 bg-white/70 p-1 shadow-sm">
          {tabs.map(({ href, label, icon: Icon, exact }) => {
            const active = exact ? pathname === href : pathname.startsWith(href);
            return (
              <Link key={href} href={href} className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${active ? "bg-[#c96442] text-white shadow-sm" : "text-stone-600 hover:bg-white"}`}>
                <Icon className="h-4 w-4" /> {label}
              </Link>
            );
          })}
        </nav>
        {children}
      </div>
    </main>
  );
}
