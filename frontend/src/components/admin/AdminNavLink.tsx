"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Shield } from "lucide-react";
import { getToken } from "@/lib/api/auth";
import { getMe } from "@/lib/api/admin";

/**
 * Renders a link to the admin control panel, but only for administrators. Self-contained: it does its
 * own `/api/v2/me` check so it can be dropped into any nav without prop plumbing. Non-admins (and
 * unauthenticated users) render nothing. The backend independently enforces authorization.
 */
export default function AdminNavLink() {
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    const token = getToken();
    if (!token) return;
    let active = true;
    getMe(token)
      .then((me) => {
        if (active && me.isAdmin) setIsAdmin(true);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  if (!isAdmin) return null;

  return (
    <div className="border-t border-stone-200 p-2">
      <Link
        href="/admin"
        className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-sm font-medium text-stone-600 transition-colors hover:bg-white/70 hover:text-stone-900"
      >
        <Shield className="h-4 w-4 text-[#c96442]" />
        Admin panel
      </Link>
    </div>
  );
}
