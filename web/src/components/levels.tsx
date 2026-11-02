// Levels and badges (lib/gamification.ts), as the app draws them (core/ui Levels.kt): the
// level heading and bar, and a badge tile. Server components: nothing here is interactive.
import clsx from "clsx";
import { formatDateValue } from "@/lib/dates";
import type { BadgeProgress, Level } from "@/lib/gamification";
import { BADGE_LABELS, LEVEL_TITLE_LABELS } from "@/lib/labels";
import { LOCALE } from "@/lib/tz";

const integer = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 0 });
export const formatXp = (xp: number) => integer.format(xp);

/** "Level 8 · Plate Collector" */
export const levelHeading = (level: Level) => `Level ${level.number} · ${LEVEL_TITLE_LABELS[level.title]}`;

/** The level heading, its bar and the XP still to go. */
export function LevelProgress({ level }: { level: Level }) {
  const fraction = level.xpForNext > 0 ? Math.min(level.xpIntoLevel / level.xpForNext, 1) : 0;
  return (
    <div>
      <div className="text-lg font-semibold tracking-tight">{levelHeading(level)}</div>
      <div
        role="progressbar"
        aria-label={`${formatXp(level.xpIntoLevel)} of ${formatXp(level.xpForNext)} XP to level ${level.number + 1}`}
        aria-valuemin={0}
        aria-valuemax={level.xpForNext}
        aria-valuenow={level.xpIntoLevel}
        className="mt-2 h-2 overflow-hidden rounded-full bg-surface-high"
      >
        <div className="h-full origin-left rounded-full bg-primary" style={{ transform: `scaleX(${fraction})` }} />
      </div>
      <div className="mt-1.5 text-xs text-muted">
        {formatXp(level.xpForNext - level.xpIntoLevel)} XP to level {level.number + 1}
      </div>
    </div>
  );
}

/** A badge's name and how to earn it, with the unlock date or how far along it is. */
export function BadgeTile({ badge }: { badge: BadgeProgress }) {
  const unlocked = badge.unlockedOn != null;
  const label = BADGE_LABELS[badge.badge];
  return (
    <div
      className={clsx(
        "rounded-2xl border p-4",
        unlocked ? "border-tertiary/25 bg-tertiary-container text-on-tertiary-container" : "border-outline bg-surface-lowest text-muted",
      )}
    >
      <div className="flex items-center gap-2 font-semibold">
        {!unlocked && <LockIcon />}
        <span className={unlocked ? undefined : "text-foreground/80"}>{label.name}</span>
        {!unlocked && <span className="sr-only">(locked)</span>}
      </div>
      <p className="mt-1 text-sm">{label.how}</p>
      <p className="mt-2 font-mono text-xs">
        {badge.unlockedOn != null
          ? `Unlocked ${formatDateValue(Date.parse(`${badge.unlockedOn}T00:00:00Z`))}`
          : `${Math.round((badge.current / badge.target) * 100)}% there`}
      </p>
    </div>
  );
}

function LockIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-4 shrink-0" fill="currentColor" aria-hidden="true">
      <path d="M17 9h-1V7a4 4 0 1 0-8 0v2H7a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2Zm-7-2a2 2 0 1 1 4 0v2h-4V7Zm2 11a2 2 0 1 1 0-4 2 2 0 0 1 0 4Z" />
    </svg>
  );
}
