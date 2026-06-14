"use client";

/**
 * Renders a single media attachment (feature 007). Used for pending uploads (object-URL src) in the
 * attachment tray and for stored media (public URL) in history and the share view. Images render
 * inline; video and audio get native playback controls.
 */
interface MediaItemProps {
  src: string;
  type: "image" | "video" | "audio";
  alt?: string;
  className?: string;
}

export default function MediaItem({ src, type, alt, className }: MediaItemProps) {
  if (type === "video") {
    return (
      <video
        src={src}
        controls
        preload="metadata"
        className={className ?? "max-h-72 w-full rounded-xl border border-stone-200 bg-black object-contain"}
      />
    );
  }
  if (type === "audio") {
    return <audio src={src} controls preload="metadata" className={className ?? "w-full"} />;
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt={alt ?? "attachment"}
      className={className ?? "max-h-72 rounded-xl border border-stone-200 object-contain"}
    />
  );
}
