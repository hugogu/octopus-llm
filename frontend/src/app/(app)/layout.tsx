import { redirect } from "next/navigation";
import { cookies } from "next/headers";
import type { ReactNode } from "react";

export default async function AppLayout({ children }: { children: ReactNode }) {
  // Token is stored client-side; middleware handles server-level protection.
  // Here we check the auth_token cookie set by the login page.
  const cookieStore = await cookies();
  const token = cookieStore.get("auth_token")?.value;
  if (!token) {
    redirect("/login");
  }
  return <>{children}</>;
}
