import AdminGuard from "@/components/admin/AdminGuard";
import AdminModelAccessPage from "@/components/admin/AdminModelAccessPage";

export const metadata = { title: "Admin · Model access — Octopus LLM" };

export default function AdminModelsRoute() {
  return <AdminGuard><AdminModelAccessPage /></AdminGuard>;
}
