import { cookies } from "next/headers";
import { getSession } from "@/lib/api/chat";
import type { ChatTurn } from "@/lib/types/api";

interface PageProps {
  params: Promise<{ sessionId: string }>;
}

export default async function SessionPage({ params }: PageProps) {
  const { sessionId } = await params;
  const cookieStore = await cookies();
  const token = cookieStore.get("auth_token")?.value ?? "";

  const session = await getSession(sessionId, token);

  return (
    <div className="max-w-3xl mx-auto px-4 py-8 flex flex-col gap-6">
      <div className="flex items-center gap-3">
        <a href="/chat" className="text-blue-600 hover:underline text-sm">← New Chat</a>
        <h1 className="text-xl font-bold">{session.title ?? "Chat Session"}</h1>
      </div>

      <div className="flex flex-col gap-4">
        {session.turns.map((turn: ChatTurn) => (
          <div key={turn.id} className="flex flex-col gap-3">
            <div className="bg-blue-50 border border-blue-200 rounded-lg px-4 py-3">
              <p className="text-xs text-blue-600 mb-1">You</p>
              <p className="text-sm whitespace-pre-wrap">{turn.promptText}</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {turn.responses.map((resp) => (
                <div
                  key={resp.modelId}
                  className="border rounded-lg overflow-hidden bg-white shadow-sm"
                >
                  <div className="flex items-center justify-between px-3 py-2 border-b bg-gray-50">
                    <span className="font-medium text-sm">{resp.modelId}</span>
                    <span
                      className={`text-xs px-1.5 py-0.5 rounded ${
                        resp.status === "complete"
                          ? "bg-green-100 text-green-700"
                          : "bg-red-100 text-red-700"
                      }`}
                    >
                      {resp.status}
                    </span>
                  </div>
                  <div className="px-3 py-2 text-sm whitespace-pre-wrap">
                    {resp.status === "error" ? (
                      <span className="text-red-600">{resp.errorMessage}</span>
                    ) : (
                      resp.responseText
                    )}
                  </div>
                  {resp.status === "complete" && (
                    <div className="px-3 py-1 border-t text-xs text-gray-400 flex gap-3">
                      <span>{(resp.latencyMs / 1000).toFixed(2)}s</span>
                      {resp.inputTokens !== null && <span>in:{resp.inputTokens}</span>}
                      {resp.outputTokens !== null && <span>out:{resp.outputTokens}</span>}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
