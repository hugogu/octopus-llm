import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import Link from "next/link";

export default async function Home() {
  const token = (await cookies()).get("auth_token")?.value;
  if (token) redirect("/chat");
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#faf9f5] p-6">
      <div className="max-w-lg rounded-3xl border border-stone-200 bg-white p-10 text-center shadow-sm">
        <h1 className="text-3xl font-semibold text-stone-900">Octopus LLM</h1>
        <p className="mt-3 text-sm text-stone-600">Compare configured models and inspect anonymous platform-level model analytics.</p>
        <div className="mt-6 flex justify-center gap-3">
          <Link href="/login" className="rounded-lg bg-[#c96442] px-4 py-2 text-sm font-medium text-white">Sign in</Link>
          <Link href="/analytics" className="rounded-lg border border-stone-300 px-4 py-2 text-sm font-medium text-stone-700">Public analytics</Link>
        </div>
      </div>
    </main>
  );
}
