"use client";

interface TrendPoint {
  label: string;
  value: number;
}

interface TrendChartProps {
  title: string;
  points: TrendPoint[];
  format: (value: number) => string;
  /** Accent colour for the line/area; defaults to the brand accent. */
  color?: string;
}

/**
 * Lightweight dependency-free SVG line chart for usage trends. Renders a single series over evenly
 * spaced buckets with a soft area fill, matching the warm stone/accent design system. Responsive via
 * a fixed viewBox scaled to the container width.
 */
export default function TrendChart({ title, points, format, color = "#c96442" }: TrendChartProps) {
  const width = 600;
  const height = 180;
  const pad = { top: 16, right: 16, bottom: 24, left: 48 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;

  const values = points.map((p) => p.value);
  const max = values.length ? Math.max(...values) : 0;
  const min = values.length ? Math.min(...values) : 0;
  const span = max - min || 1;

  const x = (index: number) => pad.left + (points.length <= 1 ? innerW / 2 : (index / (points.length - 1)) * innerW);
  const y = (value: number) => pad.top + innerH - ((value - min) / span) * innerH;

  const line = points.map((p, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(" ");
  const area = points.length
    ? `${line} L${x(points.length - 1).toFixed(1)},${(pad.top + innerH).toFixed(1)} L${x(0).toFixed(1)},${(pad.top + innerH).toFixed(1)} Z`
    : "";
  const latest = points.length ? points[points.length - 1]!.value : 0;
  const gradientId = `grad-${title.replace(/[^a-z0-9]/gi, "")}`;

  return (
    <section className="rounded-2xl border border-stone-200 bg-white/80 p-4 shadow-sm">
      <div className="mb-2 flex items-baseline justify-between">
        <h3 className="text-sm font-semibold text-stone-900">{title}</h3>
        <span className="text-sm font-medium text-stone-500">{format(latest)}</span>
      </div>
      {points.length === 0 ? (
        <div className="flex h-[140px] items-center justify-center text-sm text-stone-400">No data in range</div>
      ) : (
        <svg viewBox={`0 0 ${width} ${height}`} className="h-44 w-full" role="img" aria-label={title}>
          <defs>
            <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity="0.18" />
              <stop offset="100%" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          {[max, (max + min) / 2, min].map((tick, i) => {
            const ty = y(tick);
            return (
              <g key={i}>
                <line x1={pad.left} x2={width - pad.right} y1={ty} y2={ty} stroke="#e7e5e4" strokeWidth="1" />
                <text x={pad.left - 6} y={ty + 3} textAnchor="end" className="fill-stone-400 text-[10px]">
                  {format(tick)}
                </text>
              </g>
            );
          })}
          {area ? <path d={area} fill={`url(#${gradientId})`} /> : null}
          <path d={line} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
          {points.map((p, i) => (
            <circle key={i} cx={x(i)} cy={y(p.value)} r="2.5" fill={color} />
          ))}
          <text x={pad.left} y={height - 6} textAnchor="start" className="fill-stone-400 text-[10px]">
            {points[0]!.label}
          </text>
          {points.length > 1 ? (
            <text x={width - pad.right} y={height - 6} textAnchor="end" className="fill-stone-400 text-[10px]">
              {points[points.length - 1]!.label}
            </text>
          ) : null}
        </svg>
      )}
    </section>
  );
}
