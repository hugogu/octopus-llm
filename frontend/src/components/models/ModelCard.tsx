import type { ModelDefinition } from "@/lib/types/api";
import CapabilityBadge from "./CapabilityBadge";

interface ModelCardProps {
  model: ModelDefinition;
  children?: React.ReactNode;
}

export default function ModelCard({ model, children }: ModelCardProps) {
  const caps = model.capabilityMatrix;

  return (
    <div className="border rounded-lg p-4 flex flex-col gap-2 bg-white shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-semibold text-sm">{model.displayName}</p>
          <p className="text-xs text-gray-500">{model.providerId}</p>
          <p className="text-[11px] text-gray-400">{model.id}</p>
        </div>
        {children}
      </div>

      {caps.context_length_tokens && (
        <p className="text-xs text-gray-400">
          Context: {caps.context_length_tokens.toLocaleString()} tokens
        </p>
      )}

      <div className="flex flex-col gap-1">
        {caps.input_modalities.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {caps.input_modalities.map((m) => (
              <CapabilityBadge key={`in:${m}`} label={`in:${m}`} />
            ))}
          </div>
        )}
        {caps.output_modalities.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {caps.output_modalities.map((m) => (
              <CapabilityBadge key={`out:${m}`} label={`out:${m}`} />
            ))}
          </div>
        )}
        <div className="flex flex-wrap gap-1">
          <CapabilityBadge label="streaming" active={caps.supports_streaming} />
          <CapabilityBadge label="functions" active={caps.supports_function_calling} />
          <CapabilityBadge label="system-prompt" active={caps.supports_system_prompt} />
          {caps.supports_video_input && <CapabilityBadge label="video-in" active />}
        </div>
      </div>
    </div>
  );
}
