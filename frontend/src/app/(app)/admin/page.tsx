import AdminGuard from "@/components/admin/AdminGuard";

export const metadata = { title: "Admin — Octopus LLM" };

export default function AdminHomePage() {
  return (
    <AdminGuard>
      <div className="p-6 max-w-5xl mx-auto">
        <h1 className="text-2xl font-bold mb-4">Admin control panel</h1>
        <ul className="flex flex-col gap-2">
          <li>
            <a href="/admin/users" className="text-blue-600 underline">
              User management
            </a>
          </li>
          <li>
            <a href="/admin/connections" className="text-blue-600 underline">
              Built-in connections
            </a>
          </li>
        </ul>
      </div>
    </AdminGuard>
  );
}
