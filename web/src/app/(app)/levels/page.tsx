import { BadgeTile, formatXp, LevelProgress } from "@/components/levels";
import { Card, CardTitle, PageHeader } from "@/components/ui";
import { getPlayerProgress } from "@/lib/data";
import { RULES } from "@/lib/gamification";

export const metadata = { title: "Levels" };

/** The app's Levels and badges screen: the level, how XP is earned, and every badge. */
export default async function LevelsPage() {
  const progress = await getPlayerProgress();
  const unlocked = progress.badges.filter((b) => b.unlockedBy != null).length;
  return (
    <>
      <PageHeader title="Levels and badges" subtitle={`${formatXp(progress.totalXp)} XP in total`} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <LevelProgress level={progress.level} />
        </Card>
        <Card>
          <CardTitle>How to earn XP</CardTitle>
          <ul className="space-y-1 text-sm">
            <li>Finish a workout: {RULES.sessionXp} XP, a lighter one too</li>
            <li>
              Each working set: {RULES.setXp} XP, up to {RULES.maxXpSets} sets
            </li>
            <li>Each PR: {RULES.recordXp} XP</li>
            <li>Check in before or rate after: {RULES.checkInXp} XP</li>
            <li>Reach your days a week: {RULES.weeklyGoalXp} XP</li>
          </ul>
          <p className="mt-2 text-xs text-muted">
            Workouts under {RULES.minMinutes} minutes don&apos;t count, and only {RULES.xpSessionsPerDay} a day earn workout XP. Missing a day never costs anything.
          </p>
        </Card>
      </div>
      <h2 className="label mt-6 mb-3 text-muted">
        Badges · {unlocked} of {progress.badges.length}
      </h2>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {progress.badges.map((b) => (
          <BadgeTile key={b.badge} badge={b} />
        ))}
      </div>
    </>
  );
}
