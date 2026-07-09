import AdminGuard from "@/components/admin/AdminGuard";
import SiteSettingsPage from "@/components/admin/SiteSettingsPage";

export const metadata = { title: "Admin · Site Info — Octopus LLM" };

export default function AdminSiteRoute() {
  return (
    <AdminGuard>
      <SiteSettingsPage />
    </AdminGuard>
  );
}
