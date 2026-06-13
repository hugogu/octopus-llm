import AdminGuard from "@/components/admin/AdminGuard";
import AdminConnectionsPage from "@/components/admin/AdminConnectionsPage";

export const metadata = { title: "Admin · Built-in Connections — Octopus LLM" };

export default function AdminConnectionsRoute() {
  return (
    <AdminGuard>
      <AdminConnectionsPage />
    </AdminGuard>
  );
}
