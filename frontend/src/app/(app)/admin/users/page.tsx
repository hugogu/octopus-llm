import AdminGuard from "@/components/admin/AdminGuard";
import AdminUsersPage from "@/components/admin/AdminUsersPage";

export const metadata = { title: "Admin · Users — Octopus LLM" };

export default function AdminUsersRoute() {
  return (
    <AdminGuard>
      <AdminUsersPage />
    </AdminGuard>
  );
}
