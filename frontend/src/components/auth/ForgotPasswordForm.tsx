"use client";

import { useState } from "react";
import Link from "next/link";
import { requestPasswordReset } from "@/lib/api/auth";

export default function ForgotPasswordForm() {
  const [email, setEmail] = useState("");
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      await requestPasswordReset(email);
      setDone(true);
    } finally {
      setLoading(false);
    }
  }

  if (done) {
    return <div className="max-w-sm space-y-3 text-sm text-stone-600"><p>If an eligible account exists, a reset email has been sent.</p><Link href="/login" className="font-medium text-blue-600 underline">Back to sign in</Link></div>;
  }
  return (
    <form onSubmit={submit} className="flex w-full max-w-sm flex-col gap-4">
      <p className="text-sm text-stone-600">Enter your email. The response is the same whether or not an account exists.</p>
      <input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="Email" className="rounded border px-3 py-2" />
      <button disabled={loading} className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50">{loading ? "Submitting..." : "Request reset"}</button>
      <Link href="/login" className="text-center text-sm text-blue-600 underline">Back to sign in</Link>
    </form>
  );
}
