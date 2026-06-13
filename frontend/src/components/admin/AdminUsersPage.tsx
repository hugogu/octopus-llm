"use client";

import { useCallback, useEffect, useState } from "react";
import { getToken } from "@/lib/api/auth";
import {
  activateUser,
  disableUser,
  enableUser,
  listUsers,
  resetUserPassword,
} from "@/lib/api/admin";
import type { AdminUser } from "@/lib/types/api";

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const token = getToken() ?? "";

  const load = useCallback(async () => {
    try {
      const page = await listUsers(token, query, 0, 50);
      setUsers(page.items);
    } catch {
      setError("Failed to load users.");
    }
  }, [token, query]);

  useEffect(() => {
    let active = true;
    listUsers(token, query, 0, 50)
      .then((page) => {
        if (active) setUsers(page.items);
      })
      .catch(() => {
        if (active) setError("Failed to load users.");
      });
    return () => {
      active = false;
    };
  }, [token, query]);

  async function run(action: () => Promise<unknown>, success: string) {
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(success);
      await load();
    } catch (e) {
      const status = (e as { status?: number }).status;
      setError(status === 409 ? "Refused: cannot lock out the last administrator." : "Action failed.");
    }
  }

  return (
    <div className="p-6 max-w-5xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">User management</h1>

      <div className="flex gap-2 mb-4">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by email"
          className="border rounded px-3 py-2 flex-1"
        />
        <button onClick={() => void load()} className="border rounded px-4 py-2">
          Search
        </button>
      </div>

      {error && <p className="text-red-600 text-sm mb-2">{error}</p>}
      {notice && <p className="text-green-600 text-sm mb-2">{notice}</p>}

      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="text-left border-b">
            <th className="py-2">Email</th>
            <th>Status</th>
            <th>Role</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id} className="border-b align-top">
              <td className="py-2">{u.email}</td>
              <td>
                <span className={u.isDisabled ? "text-red-600" : "text-green-700"}>
                  {u.isDisabled ? "Disabled" : "Enabled"}
                </span>
                {", "}
                {u.isActive ? "Activated" : "Not activated"}
              </td>
              <td>{u.isAdmin ? "Admin" : "User"}</td>
              <td className="flex flex-wrap gap-2 py-2">
                {!u.isActive && (
                  <button
                    onClick={() => void run(() => activateUser(token, u.id), "User activated.")}
                    className="text-blue-600 underline"
                  >
                    Activate
                  </button>
                )}
                {u.isDisabled ? (
                  <button
                    onClick={() => void run(() => enableUser(token, u.id), "User enabled.")}
                    className="text-blue-600 underline"
                  >
                    Enable
                  </button>
                ) : (
                  <button
                    onClick={() => void run(() => disableUser(token, u.id), "User disabled.")}
                    className="text-red-600 underline"
                  >
                    Disable
                  </button>
                )}
                <button
                  onClick={() => void run(() => resetUserPassword(token, u.id), "Reset email sent.")}
                  className="text-gray-700 underline"
                >
                  Reset password
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
