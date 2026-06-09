interface CapabilityBadgeProps {
  label: string;
  active?: boolean;
}

export default function CapabilityBadge({ label, active = true }: CapabilityBadgeProps) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
        active
          ? "bg-blue-100 text-blue-800"
          : "bg-gray-100 text-gray-400 line-through"
      }`}
    >
      {label}
    </span>
  );
}
