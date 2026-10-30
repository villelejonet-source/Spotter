"use client";

import dynamic from "next/dynamic";

export type { Point } from "./charts-impl";

// Charts load in their own chunk after the page shows, keeping recharts out of the
// first load; the placeholder holds the chart's space so nothing jumps.
const placeholder = (height: number) => <div className="animate-pulse rounded-xl bg-surface-high" style={{ height }} />;

export const BarChartView = dynamic(() => import("./charts-impl").then((m) => m.BarChartView), {
  ssr: false,
  loading: () => placeholder(220),
});

export const LineChartView = dynamic(() => import("./charts-impl").then((m) => m.LineChartView), {
  ssr: false,
  loading: () => placeholder(240),
});

export const AreaChartView = dynamic(() => import("./charts-impl").then((m) => m.AreaChartView), {
  ssr: false,
  loading: () => placeholder(240),
});
