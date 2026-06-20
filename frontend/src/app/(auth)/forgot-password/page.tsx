import AuthShell from "@/components/auth/AuthShell";
import ForgotPasswordForm from "@/components/auth/ForgotPasswordForm";

export const metadata = { title: "Reset Password — Octopus LLM" };

export default function ForgotPasswordPage() {
  return (
    <AuthShell
      eyebrow="Account access"
      title="Reset your password"
      description="Enter your email and we'll send a reset link if the account exists."
    >
      <ForgotPasswordForm />
    </AuthShell>
  );
}
