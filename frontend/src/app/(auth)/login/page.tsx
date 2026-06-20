import { Suspense } from "react";
import AuthShell from "@/components/auth/AuthShell";
import LoginForm from "@/components/auth/LoginForm";

export const metadata = { title: "Sign In — Octopus LLM" };

export default function LoginPage() {
  return (
    <AuthShell
      eyebrow="Welcome back"
      title="Sign in"
      description="Access your conversations and configured models."
    >
      <Suspense>
        <LoginForm />
      </Suspense>
    </AuthShell>
  );
}
