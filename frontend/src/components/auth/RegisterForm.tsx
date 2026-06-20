"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { login, register } from "@/lib/api/auth";

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

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 w-full max-w-sm">
      {error && <p className="text-red-600 text-sm">{error}</p>}
      <input
        type="email"
        placeholder="Email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        required
        className="border rounded px-3 py-2"
      />
      <input
        type="password"
        placeholder="Password (min 8 chars)"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
        minLength={8}
        className="border rounded px-3 py-2"
      />
      <input
        type="password"
        placeholder="Confirm password"
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
        required
        className="border rounded px-3 py-2"
      />
      <button
        type="submit"
        disabled={loading}
        className="bg-blue-600 text-white rounded px-4 py-2 disabled:opacity-50"
      >
        {loading ? "Registering…" : "Create account"}
      </button>
      <p className="text-sm text-center">
        Already have an account?{" "}
        <a
          href={searchParams.get("returnTo") ? `/login?returnTo=${encodeURIComponent(searchParams.get("returnTo")!)}` : "/login"}
          className="text-blue-600 underline"
        >
          Sign in
        </a>
      </p>
    </form>
  );
}
