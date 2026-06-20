import React from "react";

interface AuthShellProps {
  eyebrow: string;
  title: string;
  description?: string;
  children: React.ReactNode;
}

export default function AuthShell({ eyebrow, title, description, children }: AuthShellProps) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[radial-gradient(circle_at_top_left,_#f8e9dc,_transparent_30%),linear-gradient(180deg,#faf9f5,#f2f0e8)] px-4 py-10">
      <div className="w-full max-w-md rounded-2xl border border-stone-200 bg-white shadow-sm">
        <div className="px-8 pb-2 pt-8">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#b75536]">{eyebrow}</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-stone-900">{title}</h1>
          {description ? <p className="mt-2 text-sm text-stone-600">{description}</p> : null}
        </div>
        <div className="p-8 pt-6">{children}</div>
      </div>
    </main>
  );
}
