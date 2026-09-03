"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import Button from "@/components/ui/Button";
import { login } from "@/lib/api/auth";

const inputClass =
  "w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 shadow-sm placeholder:text-stone-400 focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";

export default function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login({ email, password });
      const requested = searchParams.get("returnTo");
      router.push(requested?.startsWith("/") && !requested.startsWith("//") ? requested : "/chat");
    } catch {
      setError("Invalid email or password.");
    } finally {
      setLoading(false);
    }
  }

  const registerLink = searchParams.get("returnTo")
    ? `/register?returnTo=${encodeURIComponent(searchParams.get("returnTo")!)}`
    : "/register";

  return (
    <form onSubmit={handleSubmit} className="flex w-full flex-col gap-4">
      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      )}
      <input
        type="email"
        placeholder="Email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        required
        className={inputClass}
      />
      <input
        type="password"
        placeholder="Password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
        className={inputClass}
      />
      <Button type="submit" disabled={loading} fullWidth className="!bg-[#c96442] hover:!bg-[#b55538]">
        {loading ? "Signing in…" : "Sign in"}
      </Button>
      <div className="flex flex-col items-center gap-1 pt-1 text-sm text-stone-600">
        <p>
          No account?{" "}
          <Link href={registerLink} className="font-medium text-[#b75536] hover:text-[#c96442]">
            Register
          </Link>
        </p>
        <p>
          Want to try it first?{" "}
          <Link href="/chat" className="font-medium text-[#b75536] hover:text-[#c96442]">
            Continue as guest
          </Link>
        </p>
        <Link href="/forgot-password" className="font-medium text-[#b75536] hover:text-[#c96442]">
          Forgot password?
        </Link>
      </div>
    </form>
  );
}
