import { useId } from "react";

/**
 * A small trend line along the bottom of a stat tile: [values] oldest first, scaled to fit, shown
 * once at least two of them have data.
 * "accent" is the orange line with a fading area under it; "muted" a dashed grey line, for a
 * trend that isn't good or bad in itself (body weight). Drawn on the server, no chart library.
 */
export function Sparkline({ values, tone = "accent" }: { values: number[]; tone?: "accent" | "muted" }) {
  const id = useId();
  // A trend needs at least two points with data; one week of training would draw a lone spike.
  if (values.filter((v) => v > 0).length < 2) return null;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const points = values.map((v, i) => [(i / (values.length - 1)) * 100, 36 - ((v - min) / span) * 28] as const);
  const line = points.map(([x, y], i) => `${i ? "L" : "M"}${x.toFixed(2)} ${y.toFixed(2)}`).join(" ");
  const accent = tone === "accent";
  return (
    <svg viewBox="0 0 100 40" preserveAspectRatio="none" className="h-full w-full" aria-hidden="true">
      {accent && (
        <>
          <defs>
            <linearGradient id={id} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stopColor="var(--primary)" stopOpacity="0.32" />
              <stop offset="1" stopColor="var(--primary)" stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={`${line} L100 40 L0 40 Z`} fill={`url(#${id})`} />
        </>
      )}
      <path
        d={line}
        fill="none"
        stroke={accent ? "var(--primary)" : "var(--muted)"}
        strokeWidth="1.6"
        strokeDasharray={accent ? undefined : "3 3"}
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
