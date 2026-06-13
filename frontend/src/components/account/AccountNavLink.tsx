import Link from "next/link";
import { UserRound } from "lucide-react";

export default function AccountNavLink() {
  return (
    <Link href="/account" className="flex items-center gap-2 rounded-lg px-2.5 py-2 text-sm font-medium text-stone-600 transition-colors hover:bg-white/70 hover:text-stone-900">
      <UserRound className="h-4 w-4 text-[#c96442]" />
      Personal center
    </Link>
  );
}
