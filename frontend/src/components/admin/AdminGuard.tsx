"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { getToken } from "@/lib/api/auth";
import { getMe } from "@/lib/api/admin";

type State = "checking" | "allowed" | "denied";

/**
 * Gates admin routes: only users whose `/api/v2/me` reports `isAdmin` may see the children.
 * Everyone else is redirected to chat. Backend enforces authorization independently (FR-003).
 */
export default function AdminGuard({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [state, setState] = useState<State>("checking");

  useEffect(() => {
    const token = getToken();
    if (!token) {
      router.replace("/login");
      return;
    }
    getMe(token)
      .then((me) => {
        if (me.isAdmin) {
          setState("allowed");
        } else {
          setState("denied");
          router.replace("/chat");
        }
      })
      .catch(() => {
        setState("denied");
        router.replace("/chat");
      });
  }, [router]);

  if (state !== "allowed") {
    return (
      <main className="flex min-h-screen items-center justify-center bg-[radial-gradient(circle_at_top_left,_#f8e9dc,_transparent_30%),linear-gradient(180deg,#faf9f5,#f2f0e8)]">
        <p className="text-sm text-stone-500">Checking access…</p>
      </main>
    );
  }
  return <>{children}</>;
}
