"use client";

import { LOCALE } from "@/lib/tz";
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export interface Point {
  label: string;
  value: number | null;
}

const axis = { stroke: "var(--muted)", fontSize: 12, tickLine: false, axisLine: false } as const;
const tooltip = {
  contentStyle: { background: "var(--surface)", border: "1px solid var(--outline)", borderRadius: 12, color: "var(--foreground)" },
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
