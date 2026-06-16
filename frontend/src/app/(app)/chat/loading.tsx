import { Plus } from 'lucide-react';

/**
 * Route-level fallback shown on a full page load / refresh, while the chat page (a client component
 * that reads useSearchParams and therefore bails out of server rendering) hydrates. It mirrors the
 * real chat shell — sidebar chrome, header, content + composer — so the refresh path looks identical
 * to the page's own `sessionLoading || modelsLoading` skeleton, instead of flashing a different
 * layout. Static chrome (New Chat, nav) renders solid; only the data regions pulse.
 */
export default function ChatLoading() {
  return (
    <div className="flex h-screen max-h-screen bg-[#faf9f5]">
      {/* Sidebar — matches SessionSidebar's expanded shell */}
      <div className="hidden h-full w-64 flex-col border-r border-stone-200 bg-[#f5f4ee] md:flex">
        <div className="flex items-center gap-2 p-3">
          <div className="flex h-9 flex-1 items-center justify-center gap-2 rounded-lg bg-[#c96442] text-sm font-medium text-white">
            <Plus className="h-4 w-4" /> New Chat
          </div>
          <div className="h-9 w-9 shrink-0 rounded-lg bg-white/50" />
        </div>
        <div className="flex-1 space-y-3 p-4">
          {[1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-12 animate-pulse rounded-lg bg-stone-200/80" />
          ))}
        </div>
        <div className="space-y-1 border-t border-stone-200 p-2">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-9 rounded-lg bg-white/40" />
          ))}
        </div>
      </div>

      {/* Main column */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-b border-stone-200 px-6 py-3">
          <div className="flex items-center justify-between gap-4">
            <div className="min-w-0 space-y-1.5">
              <div className="h-4 w-48 animate-pulse rounded bg-stone-200" />
              <div className="h-3 w-64 rounded bg-stone-200/60" />
            </div>
            <div className="h-8 w-44 shrink-0 rounded-lg border border-stone-200 bg-white" />
          </div>
        </header>

        <div className="flex-1 overflow-y-auto px-6">
          <div className="flex w-full flex-col gap-4 py-6">
            <div className="ml-auto h-24 w-full max-w-3xl animate-pulse rounded-2xl bg-stone-200" />
            <div className="h-52 w-full animate-pulse rounded-2xl bg-stone-200" />
          </div>
        </div>

        <div className="border-t border-stone-200 bg-[#faf9f5] px-6 py-3">
          <div className="mx-auto h-12 w-full max-w-3xl animate-pulse rounded-xl bg-stone-200" />
        </div>
      </div>
    </div>
  );
}
