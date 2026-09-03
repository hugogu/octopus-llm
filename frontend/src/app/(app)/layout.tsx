"use client";

import { useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { getToken } from "@/lib/api/auth";

export default function AppLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const publicChat = pathname === "/chat";
  const [allowed, setAllowed] = useState(publicChat);

  useEffect(() => {
    queueMicrotask(() => {
      if (publicChat) {
        setAllowed(true);
        return;
      }
      if (getToken()) {
        setAllowed(true);
        return;
      }
      setAllowed(false);
      router.replace(`/login?returnTo=${encodeURIComponent(pathname || "/chat")}`);
    });
  }, [pathname, publicChat, router]);

  if (!allowed) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-[#faf9f5]">
        <p className="text-sm text-stone-500">Checking access…</p>
      </main>
    );
  }
  return <>{children}</>;
}
