import RegisterForm from "@/components/auth/RegisterForm";

export const metadata = { title: "Register — Octopus LLM" };

export default function RegisterPage() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center p-4">
      <h1 className="text-2xl font-bold mb-6">Create your account</h1>
      <RegisterForm />
    </main>
  );
}
