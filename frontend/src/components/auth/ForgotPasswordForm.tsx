"use client";

import { useState } from "react";
import Link from "next/link";
import Button from "@/components/ui/Button";
import { requestPasswordReset } from "@/lib/api/auth";

const inputClass =
  "w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-800 shadow-sm placeholder:text-stone-400 focus:border-[#c96442] focus:outline-none focus:ring-1 focus:ring-[#c96442]";

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
    return (
      <div className="space-y-4">
        <div className="rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
          If an eligible account exists, a reset email has been sent.
        </div>
        <Link
          href="/login"
          className="block text-center text-sm font-medium text-[#b75536] hover:text-[#c96442]"
        >
          Back to sign in
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="flex w-full flex-col gap-4">
      <input
        type="email"
        required
        value={email}
        onChange={(event) => setEmail(event.target.value)}
        placeholder="Email"
        className={inputClass}
      />
      <Button type="submit" disabled={loading} fullWidth className="!bg-[#c96442] hover:!bg-[#b55538]">
        {loading ? "Submitting…" : "Request reset"}
      </Button>
      <Link
        href="/login"
        className="pt-1 text-center text-sm font-medium text-[#b75536] hover:text-[#c96442]"
      >
        Back to sign in
      </Link>
    </form>
  );
}
