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
    return <p className="p-6 text-sm text-gray-500">Checking access…</p>;
  }
  return <>{children}</>;
}
