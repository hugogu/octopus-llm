"use client";

import { Image as ImageIcon, Video, Mic } from "lucide-react";
import type { ModalityToggles } from "./formUtils";

const OPTIONS = [
  { key: "image", label: "Image", Icon: ImageIcon },
  { key: "video", label: "Video", Icon: Video },
  { key: "audio", label: "Audio", Icon: Mic },
] as const;

/**
 * One-click media-capability toggles (feature 007). Sets which media types a model accepts besides
 * text, driving its `input_modalities` — far friendlier than hand-writing capability JSON.
 */
export default function CapabilityToggles({
  value,
  onChange,
}: {
  value: ModalityToggles;
  onChange: (next: ModalityToggles) => void;
}) {
  return (
    <div>
      <p className="text-sm font-medium text-gray-700">Media input</p>
      <p className="mb-2 text-xs text-stone-500">
        What can this model accept besides text? Toggle on the types it supports.
      </p>
      <div className="flex flex-wrap gap-2">
        {OPTIONS.map(({ key, label, Icon }) => {
          const active = value[key];
          return (
            <button
              key={key}
              type="button"
              aria-pressed={active}
              onClick={() => onChange({ ...value, [key]: !active })}
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm transition ${
                active
                  ? "border-[#c96442] bg-[#c96442]/10 text-[#b75536]"
                  : "border-stone-200 bg-white text-stone-600 hover:bg-stone-50"
              }`}
            >
              <Icon className="h-4 w-4" /> {label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
