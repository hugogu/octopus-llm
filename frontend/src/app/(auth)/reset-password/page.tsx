import AuthShell from "@/components/auth/AuthShell";
import ResetPasswordForm from "@/components/auth/ResetPasswordForm";

export const metadata = { title: "Reset Password — Octopus LLM" };

export default function ResetPasswordPage() {
  return (
    <AuthShell
      eyebrow="New password"
      title="Set a new password"
      description="Choose a password of at least 8 characters."
    >
      <ResetPasswordForm />
    </AuthShell>
  );
}
