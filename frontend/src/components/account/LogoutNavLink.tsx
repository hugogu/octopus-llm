"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
import { logout } from "@/lib/api/auth";

export default function LogoutNavLink() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  async function handleLogout() {
    setBusy(true);
    try {
      await logout();
    } catch {
      // best-effort: still clear the cookie client-side inside logout() and redirect
    }
    router.push("/login");
  }

  return (
    <button
      type="button"
      onClick={handleLogout}
      disabled={busy}
      className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-sm font-medium text-stone-600 transition-colors hover:bg-white/70 hover:text-stone-900 disabled:opacity-50"
    >
      <LogOut className="h-4 w-4 text-[#c96442]" />
      {busy ? "Logging out…" : "Log out"}
    </button>
  );
}
