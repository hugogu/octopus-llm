import LoginForm from "@/components/auth/LoginForm";

export const metadata = { title: "Sign In — Octopus LLM" };

export default function LoginPage() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center p-4">
      <h1 className="text-2xl font-bold mb-6">Sign in</h1>
      <LoginForm />
    </main>
  );
}
