"use client";

import { useCallback, useEffect, useState } from "react";
import { getToken } from "@/lib/api/auth";
import {
  addBuiltinModel,
  allocateConnection,
  createBuiltinConnection,
  deleteBuiltinConnection,
  listBuiltinConnections,
  revokeConnection,
} from "@/lib/api/admin";
import type { BuiltinConnection } from "@/lib/types/api";

export default function AdminConnectionsPage() {
  const token = getToken() ?? "";
  const [connections, setConnections] = useState<BuiltinConnection[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [protocol, setProtocol] = useState("openai-compatible");
  const [baseUrl, setBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [label, setLabel] = useState("");

  const load = useCallback(async () => {
    try {
      const page = await listBuiltinConnections(token, 0, 100);
      setConnections(page.items);
    } catch {
      setError("Failed to load built-in connections.");
    }
  }, [token]);

  useEffect(() => {
    let active = true;
    listBuiltinConnections(token, 0, 100)
      .then((page) => {
        if (active) setConnections(page.items);
      })
      .catch(() => {
        if (active) setError("Failed to load built-in connections.");
      });
    return () => {
      active = false;
    };
  }, [token]);

  async function run(action: () => Promise<unknown>, success: string) {
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(success);
      await load();
    } catch (e) {
      const status = (e as { status?: number }).status;
      setError(status === 422 ? "User must be activated before allocation." : "Action failed.");
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    await run(
      () => createBuiltinConnection(token, { protocol, baseUrl, apiKey, label: label || undefined }),
      "Built-in connection created.",
    );
    setBaseUrl("");
    setApiKey("");
    setLabel("");
  }

  return (
    <div className="p-6 max-w-5xl mx-auto">
      <h1 className="text-2xl font-bold mb-4">Built-in connections</h1>

      {error && <p className="text-red-600 text-sm mb-2">{error}</p>}
      {notice && <p className="text-green-600 text-sm mb-2">{notice}</p>}

      <form onSubmit={handleCreate} className="grid grid-cols-2 gap-2 mb-6 max-w-2xl">
        <input value={protocol} onChange={(e) => setProtocol(e.target.value)} placeholder="Protocol" className="border rounded px-3 py-2" />
        <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Label (optional)" className="border rounded px-3 py-2" />
        <input value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} placeholder="https://api.example.com" className="border rounded px-3 py-2" required />
        <input value={apiKey} onChange={(e) => setApiKey(e.target.value)} placeholder="API key" type="password" className="border rounded px-3 py-2" required />
        <button type="submit" className="col-span-2 bg-blue-600 text-white rounded px-4 py-2">
          Create built-in connection
        </button>
      </form>

      <ul className="flex flex-col gap-4">
        {connections.map((c) => (
          <ConnectionCard key={c.id} connection={c} token={token} onChange={load} onError={setError} />
        ))}
      </ul>
    </div>
  );
}

function ConnectionCard({
  connection,
  token,
  onChange,
  onError,
}: {
  connection: BuiltinConnection;
  token: string;
  onChange: () => Promise<void>;
  onError: (msg: string) => void;
}) {
  const [modelId, setModelId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [userId, setUserId] = useState("");

  async function act(action: () => Promise<unknown>) {
    try {
      await action();
      await onChange();
    } catch (e) {
      const status = (e as { status?: number }).status;
      onError(status === 422 ? "User must be activated before allocation." : "Action failed.");
    }
  }

  return (
    <li className="border rounded p-4">
      <div className="flex justify-between items-center">
        <div>
          <strong>{connection.label ?? connection.protocol}</strong>{" "}
          <span className="text-gray-500 text-sm">
            {connection.baseUrl} · {connection.modelCount} models · {connection.allocatedUserCount} users
          </span>
        </div>
        <button onClick={() => void act(() => deleteBuiltinConnection(token, connection.id))} className="text-red-600 underline text-sm">
          Delete
        </button>
      </div>

      <div className="flex flex-wrap gap-2 mt-3 text-sm">
        <input value={modelId} onChange={(e) => setModelId(e.target.value)} placeholder="model id" className="border rounded px-2 py-1" />
        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="display name" className="border rounded px-2 py-1" />
        <button
          onClick={() =>
            void act(() => addBuiltinModel(token, connection.id, { modelId, displayName })).then(() => {
              setModelId("");
              setDisplayName("");
            })
          }
          className="border rounded px-3 py-1"
        >
          Add model
        </button>
      </div>

      <div className="flex flex-wrap gap-2 mt-2 text-sm">
        <input value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="user id to allocate" className="border rounded px-2 py-1 flex-1" />
        <button onClick={() => void act(() => allocateConnection(token, connection.id, userId))} className="text-blue-600 underline">
          Allocate
        </button>
        <button onClick={() => void act(() => revokeConnection(token, connection.id, userId))} className="text-red-600 underline">
          Revoke
        </button>
      </div>
    </li>
  );
}
