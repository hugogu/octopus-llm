import AdminGuard from "@/components/admin/AdminGuard";
import ToolSettingsPage from "@/components/admin/ToolSettingsPage";

export const metadata = { title: "Admin · Tools — Octopus LLM" };

export default function AdminToolsRoute() {
  return (
    <AdminGuard>
      <ToolSettingsPage />
    </AdminGuard>
  );
}
