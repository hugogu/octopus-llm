import AdminGuard from "@/components/admin/AdminGuard";
import MigrationPage from "@/components/admin/MigrationPage";

export const metadata = { title: "Admin · Data migration — Octopus LLM" };

export default function AdminMigrationRoute() {
  return (
    <AdminGuard>
      <MigrationPage />
    </AdminGuard>
  );
}
