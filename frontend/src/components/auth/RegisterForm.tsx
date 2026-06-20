"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import Button from "@/components/ui/Button";
import { login, register } from "@/lib/api/auth";

const inputClass =
  "w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 shadow-sm placeholder:text-stone-400 focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";

export default function RegisterForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (password !== confirm) {
      setError("Passwords do not match.");
      return;
    }
    setLoading(true);
    try {
      await register({ email, password });
      await login({ email, password });
      const requested = searchParams.get("returnTo");
      router.replace(requested?.startsWith("/") && !requested.startsWith("//") ? requested : "/chat");
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Registration failed.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  const loginLink = searchParams.get("returnTo")
    ? `/login?returnTo=${encodeURIComponent(searchParams.get("returnTo")!)}`
    : "/login";

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
        placeholder="Password (min 8 chars)"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
        minLength={8}
        className={inputClass}
      />
      <input
        type="password"
        placeholder="Confirm password"
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
        required
        className={inputClass}
      />
      <Button type="submit" disabled={loading} fullWidth className="!bg-[#c96442] hover:!bg-[#b55538]">
        {loading ? "Registering…" : "Create account"}
      </Button>
      <p className="pt-1 text-center text-sm text-stone-600">
        Already have an account?{" "}
        <Link href={loginLink} className="font-medium text-[#b75536] hover:text-[#c96442]">
          Sign in
        </Link>
      </p>
    </form>
  );
}
