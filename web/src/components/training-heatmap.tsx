import clsx from "clsx";
import { formatDateValue } from "@/lib/dates";
import { DAY_MS, dateValueKey, localDate, WEEK_MS, weekStart } from "@/lib/stats";

/** The Monday that starts a [weeks]-week heatmap ending this week. */
export function heatmapStart(weeks: number, now = Date.now()): number {
  return weekStart(now) - (weeks - 1) * WEEK_MS;
}

/**
 * Training days as a grid, one column per week and one row per weekday, oldest on the left:
 * rest, one workout, two or more (with the accent's glow). [perDay] is workoutsPerDay's map.
 */
export function TrainingHeatmap({ weeks, perDay, now = Date.now(), legend = true }: { weeks: number; perDay: Map<string, number>; now?: number; legend?: boolean }) {
  const first = heatmapStart(weeks, now);
  const today = localDate(now);
  return (
    <>
      <div className="overflow-x-auto">
        <div className="inline-grid grid-flow-col grid-rows-7 gap-[3px]" role="img" aria-label={`Training days over the last ${weeks} weeks`}>
          {Array.from({ length: weeks * 7 }, (_, i) => {
            const date = first + Math.floor(i / 7) * WEEK_MS + (i % 7) * DAY_MS;
            const count = perDay.get(dateValueKey(date)) ?? 0;
            const text = `${formatDateValue(date)}: ${count ? `${count} workout${count > 1 ? "s" : ""}` : "rest"}`;
            return (
              <div
                key={date}
                title={date > today ? undefined : text}
                className={clsx(
                  "h-3 w-3 rounded-[3px]",
                  date > today ? "opacity-0" : count >= 2 ? "bg-on-primary-container shadow-[0_0_8px_var(--glow)]" : count === 1 ? "bg-primary" : "bg-surface-high",
                )}
              />
            );
          })}
        </div>
      </div>
      {legend && (
        <div className="mt-3 flex items-center gap-2 text-xs text-muted">
          <span className="h-3 w-3 rounded-[3px] bg-surface-high" /> Rest
          <span className="ml-2 h-3 w-3 rounded-[3px] bg-primary" /> Workout
          <span className="ml-2 h-3 w-3 rounded-[3px] bg-on-primary-container" /> Two or more
        </div>
      )}
    </>
  );
}
