"use client";

import { LOCALE } from "@/lib/tz";
import { useId } from "react";
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export interface Point {
  label: string;
  value: number | null;
}

const axis = { stroke: "var(--faint)", fontSize: 11, fontFamily: "var(--font-geist-mono)", tickLine: false, axisLine: false } as const;
const tooltip = {
  contentStyle: { background: "var(--raised)", border: "1px solid var(--outline-strong)", borderRadius: 12, color: "var(--foreground)", fontFamily: "var(--font-geist-mono)", fontSize: 12 },
  labelStyle: { color: "var(--muted)" },
  cursor: { fill: "var(--surface-high)" },
} as const;

export function BarChartView({ data, unit, height = 220 }: { data: Point[]; unit?: string; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="var(--outline)" strokeDasharray="3 3" />
        <XAxis dataKey="label" {...axis} />
        <YAxis {...axis} width={48} />
        <Tooltip {...tooltip} formatter={(v) => [`${Number(v).toLocaleString(LOCALE)}${unit ? ` ${unit}` : ""}`, ""]} />
        <Bar dataKey="value" fill="var(--primary)" radius={[6, 6, 0, 0]} maxBarSize={36} />
      </BarChart>
    </ResponsiveContainer>
  );
}

export function LineChartView({ data, unit, height = 240 }: { data: Point[]; unit?: string; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="var(--outline)" strokeDasharray="3 3" />
        <XAxis dataKey="label" {...axis} minTickGap={24} />
        <YAxis {...axis} width={48} domain={["auto", "auto"]} />
        <Tooltip
          {...tooltip}
          cursor={{ stroke: "var(--outline)" }}
          formatter={(v) => [`${Number(v).toLocaleString(LOCALE, { maximumFractionDigits: 1 })}${unit ? ` ${unit}` : ""}`, ""]}
        />
        <Line
          type="monotone"
          dataKey="value"
          stroke="var(--primary)"
          strokeWidth={2}
          dot={{ r: 3, fill: "var(--primary)" }}
          connectNulls
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

/**
 * A trend as a glowing line over a fading area (the dashboard's weekly volume). The latest point
 * is marked; the grid is only faint horizontal lines.
 */
export function AreaChartView({ data, unit, height = 240 }: { data: Point[]; unit?: string; height?: number }) {
  const id = useId().replace(/:/g, "");
  const last = data.length - 1;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 12, right: 12, left: -8, bottom: 0 }}>
        <defs>
          <linearGradient id={`fill-${id}`} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0" stopColor="var(--primary)" stopOpacity={0.42} />
            <stop offset="1" stopColor="var(--primary)" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid vertical={false} stroke="var(--outline)" />
        <XAxis dataKey="label" {...axis} minTickGap={28} />
        <YAxis {...axis} width={52} />
        <Tooltip
          {...tooltip}
          cursor={{ stroke: "var(--outline-strong)" }}
          formatter={(v) => [`${Number(v).toLocaleString(LOCALE)}${unit ? ` ${unit}` : ""}`, ""]}
        />
        <Area
          type="monotone"
          dataKey="value"
          stroke="var(--primary)"
          strokeWidth={2.4}
          fill={`url(#fill-${id})`}
          style={{ filter: "drop-shadow(0 0 6px var(--glow))" }}
          dot={(p: { cx?: number; cy?: number; index?: number }) =>
            p.index === last && p.cx != null && p.cy != null ? (
              <circle key="last" cx={p.cx} cy={p.cy} r={4.5} fill="var(--foreground)" stroke="var(--primary)" strokeWidth={3} />
            ) : (
              <g key={p.index} />
            )
          }
          activeDot={{ r: 5, fill: "var(--primary)", stroke: "var(--background)", strokeWidth: 2 }}
          connectNulls
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
