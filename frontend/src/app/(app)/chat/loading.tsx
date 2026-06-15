export default function ChatLoading() {
  return (
    <div className="flex h-screen bg-[#faf9f5]">
      <div className="hidden w-64 shrink-0 border-r border-stone-200 bg-white p-4 md:block">
        <div className="h-8 animate-pulse rounded-lg bg-stone-200" />
        <div className="mt-4 space-y-2">
          <div className="h-12 animate-pulse rounded-lg bg-stone-200" />
          <div className="h-12 animate-pulse rounded-lg bg-stone-200" />
        </div>
      </div>
      <div className="flex flex-1 flex-col">
        <div className="border-b border-stone-200 px-6 py-3">
          <div className="h-5 w-48 animate-pulse rounded bg-stone-200" />
        </div>
        <div className="flex-1 px-6 py-6">
          <div className="ml-auto h-24 max-w-3xl animate-pulse rounded-2xl bg-stone-200" />
          <div className="mt-4 h-52 animate-pulse rounded-2xl bg-stone-200" />
        </div>
        <div className="border-t border-stone-200 px-6 py-3">
          <div className="h-10 max-w-3xl animate-pulse rounded-xl bg-stone-200" />
        </div>
      </div>
    </div>
  );
}
