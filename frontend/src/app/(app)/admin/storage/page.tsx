import AdminGuard from "@/components/admin/AdminGuard";
import StorageSettingsPage from "@/components/admin/StorageSettingsPage";

export const metadata = { title: "Admin · Media Storage — Octopus LLM" };

export default function AdminStorageRoute() {
  return (
    <AdminGuard>
      <StorageSettingsPage />
    </AdminGuard>
  );
}
