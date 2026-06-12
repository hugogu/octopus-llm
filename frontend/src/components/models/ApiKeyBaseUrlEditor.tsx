'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Pencil, Check, X } from 'lucide-react';
import { patchApiKey } from '@/lib/api/userConfig';
import { getToken } from '@/lib/api/auth';

interface ApiKeyBaseUrlEditorProps {
  keyId: string;
  baseUrl: string | null;
  /** The provider's built-in endpoint, shown when no override is set. */
  defaultBaseUrl?: string;
}

/**
 * Inline editor for an API key's base URL override.
 * Saving an empty value clears the override (provider default applies).
 */
export default function ApiKeyBaseUrlEditor({ keyId, baseUrl, defaultBaseUrl }: ApiKeyBaseUrlEditorProps) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(baseUrl ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSave = async () => {
    setError(null);
    setSaving(true);
    try {
      const token = getToken();
      if (!token) throw new Error('Not authenticated');
      await patchApiKey(token, keyId, { baseUrl: value.trim() });
      setEditing(false);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  if (!editing) {
    const displayUrl = baseUrl ?? defaultBaseUrl ?? 'Default endpoint';
    return (
      <div className="mt-1 flex items-center gap-1.5 text-xs text-gray-500">
        <span className="truncate font-mono" title={displayUrl}>
          {displayUrl}
        </span>
        {!baseUrl && defaultBaseUrl && (
          <span className="shrink-0 rounded-full bg-gray-100 px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-gray-400">
            default
          </span>
        )}
        <button
          type="button"
          onClick={() => {
            setValue(baseUrl ?? '');
            setEditing(true);
          }}
          className="shrink-0 rounded p-0.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
          title="Edit base URL"
        >
          <Pencil className="h-3 w-3" />
        </button>
      </div>
    );
  }

  return (
    <div className="mt-1">
      <div className="flex items-center gap-1.5">
        <input
          type="url"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={defaultBaseUrl ? `${defaultBaseUrl} (default)` : 'https://api.example.com/v1 (empty = default)'}
          className="w-full rounded border border-gray-300 px-2 py-1 font-mono text-xs text-gray-800 focus:border-blue-500 focus:outline-none"
          disabled={saving}
        />
        <button
          type="button"
          onClick={handleSave}
          disabled={saving}
          className="shrink-0 rounded p-1 text-green-600 hover:bg-green-50"
          title="Save"
        >
          <Check className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          onClick={() => {
            setEditing(false);
            setError(null);
          }}
          disabled={saving}
          className="shrink-0 rounded p-1 text-gray-400 hover:bg-gray-100"
          title="Cancel"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}
