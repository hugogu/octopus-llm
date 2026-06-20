"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { BarChart3, ChevronUp, LogOut, Shield, UserRound } from "lucide-react";
import { getToken, logout } from "@/lib/api/auth";
import { getMe } from "@/lib/api/admin";
import type { MeResponse } from "@/lib/types/api";

interface MenuLinkProps {
  href: string;
  icon: typeof UserRound;
  label: string;
  onNavigate: () => void;
}

function MenuLink({ href, icon: Icon, label, onNavigate }: MenuLinkProps) {
  return (
    <Link
      role="menuitem"
      href={href}
      onClick={onNavigate}
      className="flex items-center gap-2 px-3 py-2 text-sm text-stone-600 hover:bg-stone-50 hover:text-stone-900"
    >
      <Icon className="h-4 w-4 text-[#c96442]" />
      {label}
    </Link>
  );
}

export default function UserMenu() {
  const router = useRouter();
  const [me, setMe] = useState<MeResponse | null>(null);
  const [open, setOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const token = getToken();
    if (!token) return;
    let active = true;
    getMe(token)
      .then((res) => {
        if (active) setMe(res);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(e: MouseEvent) {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
    } catch {
      // best-effort: still clear the cookie client-side inside logout() and redirect
    }
    router.push("/login");
  }

  const label = me?.displayName?.trim() || me?.email || "Account";
  const initial = label.charAt(0).toUpperCase();
  const close = () => setOpen(false);

  return (
    <div ref={wrapRef} className="relative">
      {open ? (
        <div
          role="menu"
          aria-label="Account menu"
          className="absolute bottom-full left-0 right-0 mb-2 overflow-hidden rounded-xl border border-stone-200 bg-white shadow-lg"
        >
          <MenuLink href="/account" icon={UserRound} label="Personal center" onNavigate={close} />
          <MenuLink href="/analytics" icon={BarChart3} label="Public analytics" onNavigate={close} />
          {me?.isAdmin ? (
            <MenuLink href="/admin" icon={Shield} label="Admin panel" onNavigate={close} />
          ) : null}
          <div className="my-1 border-t border-stone-200" />
          <button
            role="menuitem"
            type="button"
            disabled={loggingOut}
            onClick={handleLogout}
            className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-stone-600 hover:bg-stone-50 hover:text-stone-900 disabled:opacity-50"
          >
            <LogOut className="h-4 w-4 text-[#c96442]" />
            {loggingOut ? "Logging out…" : "Log out"}
          </button>
        </div>
      ) : null}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left transition-colors hover:bg-white/70"
      >
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#c96442] text-sm font-semibold text-white">
          {initial}
        </span>
        <span className="min-w-0 flex-1 truncate text-sm font-medium text-stone-700">{label}</span>
        <ChevronUp
          className={`h-4 w-4 shrink-0 text-stone-400 transition-transform ${open ? "" : "rotate-180"}`}
        />
      </button>
    </div>
  );
}
