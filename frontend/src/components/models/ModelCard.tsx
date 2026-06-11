import type { ModelDefinition } from "@/lib/types/api";
import CapabilityBadge from "./CapabilityBadge";

interface ModelCardProps {
  model: ModelDefinition;
  status?: React.ReactNode;
  children?: React.ReactNode;
}

export default function ModelCard({ model, status, children }: ModelCardProps) {
  const caps = model.capabilityMatrix;

  return (
    <article className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
      <div className="flex items-start justify-between gap-3 px-4 py-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-base font-semibold text-gray-900">{model.displayName}</p>
            <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium uppercase tracking-[0.14em] text-gray-500">
              {model.providerId}
            </span>
          </div>
          <p className="mt-1 break-all text-xs text-gray-400">{model.id}</p>
        </div>
        {status}
      </div>

      <div className="space-y-3 px-4 pb-4">
        {caps.context_length_tokens && (
          <p className="text-sm text-gray-500">
            Context: {caps.context_length_tokens.toLocaleString()} tokens
          </p>
        )}

        <div className="flex flex-col gap-1.5">
          {caps.input_modalities.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {caps.input_modalities.map((m) => (
                <CapabilityBadge key={`in:${m}`} label={`in:${m}`} />
              ))}
            </div>
          )}
          {caps.output_modalities.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {caps.output_modalities.map((m) => (
                <CapabilityBadge key={`out:${m}`} label={`out:${m}`} />
              ))}
            </div>
          )}
          <div className="flex flex-wrap gap-1.5">
            <CapabilityBadge label="streaming" active={caps.supports_streaming} />
            <CapabilityBadge label="functions" active={caps.supports_function_calling} />
            <CapabilityBadge label="system-prompt" active={caps.supports_system_prompt} />
            {caps.supports_video_input && <CapabilityBadge label="video-in" active />}
          </div>
        </div>
      </div>

      {children ? (
        <div className="border-t border-gray-100 bg-gray-50/80 px-4 py-4">
          {children}
        </div>
      ) : null}
    </article>
  );
}
