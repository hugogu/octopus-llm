import Link from "next/link";
import AdminGuard from "@/components/admin/AdminGuard";

export const metadata = { title: "Admin — Octopus LLM" };

export default function AdminHomePage() {
  return (
    <AdminGuard>
      <div className="p-6 max-w-5xl mx-auto">
        <Link href="/chat" className="text-sm text-stone-500 underline">← Back to chat</Link>
        <h1 className="text-2xl font-bold mt-2 mb-4">Admin control panel</h1>
        <ul className="flex flex-col gap-2">
          <li>
            <Link href="/admin/users" className="text-blue-600 underline">
              User management
            </Link>
          </li>
          <li>
            <Link href="/admin/connections" className="text-blue-600 underline">
              Built-in connections
            </Link>
          </li>
        </ul>
      </div>
    </AdminGuard>
  );
}
