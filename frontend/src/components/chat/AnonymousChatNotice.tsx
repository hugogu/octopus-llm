"use client";

export default function AnonymousChatNotice({ storageWarning }: { storageWarning?: string | null }) {
  return (
    <div className="space-y-1 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
      <p>Anonymous mode: conversations stay in this browser and cannot be shared.</p>
      {storageWarning ? <p>{storageWarning}</p> : null}
    </div>
  );
}
