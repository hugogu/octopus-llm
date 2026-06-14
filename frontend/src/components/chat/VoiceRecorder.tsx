"use client";

import { useRef, useState } from "react";
import { Mic, Square } from "lucide-react";

/**
 * In-chat voice capture (feature 007, US4) using the browser-native MediaRecorder. On stop it hands a
 * single audio File to the caller, which adds it to the attachment tray and sends it like any media.
 * No extra dependency.
 */
export default function VoiceRecorder({
  onRecorded,
  disabled = false,
}: {
  onRecorded: (file: File) => void;
  disabled?: boolean;
}) {
  const [recording, setRecording] = useState(false);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);

  async function start() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => {
        const type = recorder.mimeType || "audio/webm";
        const blob = new Blob(chunksRef.current, { type });
        const ext = type.includes("mp4") || type.includes("mpeg") ? "m4a" : type.includes("ogg") ? "ogg" : "webm";
        stream.getTracks().forEach((t) => t.stop());
        onRecorded(new File([blob], `voice-${Date.now()}.${ext}`, { type: blob.type }));
      };
      recorder.start();
      recorderRef.current = recorder;
      setRecording(true);
    } catch {
      setRecording(false);
    }
  }

  function stop() {
    recorderRef.current?.stop();
    recorderRef.current = null;
    setRecording(false);
  }

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => (recording ? stop() : void start())}
      className={`inline-flex h-8 w-8 items-center justify-center rounded-lg transition disabled:opacity-50 ${
        recording
          ? "animate-pulse bg-red-500 text-white hover:bg-red-600"
          : "text-stone-500 hover:bg-stone-100 hover:text-stone-700"
      }`}
      title={recording ? "Stop recording" : "Record voice"}
      aria-label={recording ? "Stop recording" : "Record voice"}
    >
      {recording ? <Square className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
    </button>
  );
}
