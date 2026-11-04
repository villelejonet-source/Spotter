import { BadgeTile, formatXp, LevelProgress, MasteryList, QuestList } from "@/components/levels";
import { Card, CardTitle, EmptyState, LinkButton, PageHeader } from "@/components/ui";
import { getMastery, getPlayerProgress, getShowLevels, getUnit, getYearInReview, preload } from "@/lib/data";
import { RULES, reviewYear } from "@/lib/gamification";
import { dayKey } from "@/lib/stats";

export const metadata = { title: "Levels" };

/**
 * The app's Levels and badges screen: the level, this week's quests, how XP is earned, lift
 * mastery and every badge. Turned off in the profile, it says where to turn it back on.
 */
export default async function LevelsPage() {
  const year = reviewYear(dayKey(Date.now()));
  preload(getPlayerProgress(), getMastery(), getUnit(), ...(year == null ? [] : [getYearInReview(year)]));
  if (!(await getShowLevels())) {
    return (
      <>
        <PageHeader title="Levels and badges" />
        <EmptyState title="Levels and badges are off" action={<LinkButton href="/profile">Turn them on in Profile</LinkButton>}>
          You turned them off, here or in the app. Your workouts still count, so everything is there when you turn them back on.
        </EmptyState>
      </>
    );
  }
  const [progress, mastery, unit, review] = await Promise.all([getPlayerProgress(), getMastery(), getUnit(), year == null ? null : getYearInReview(year)]);
  const unlocked = progress.badges.filter((b) => b.unlockedBy != null).length;
  return (
    <>
      <PageHeader
        title="Levels and badges"
        subtitle={`${formatXp(progress.totalXp)} XP in total`}
        actions={review && <LinkButton href={`/levels/${review.year}`}>Your {review.year}</LinkButton>}
      />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <LevelProgress level={progress.level} />
        </Card>
        {progress.thisWeek && (
          <Card>
            <CardTitle>This week&apos;s quests</CardTitle>
            <QuestList week={progress.thisWeek} />
            <p className="mt-3 text-xs text-muted">New quests every Monday, {RULES.questXp} XP each.</p>
          </Card>
        )}
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
            <li>Finish a weekly quest: {RULES.questXp} XP</li>
          </ul>
          <p className="mt-2 text-xs text-muted">
            Workouts under {RULES.minMinutes} minutes don&apos;t count, and only {RULES.xpSessionsPerDay} a day earn workout XP. Missing a day never costs anything.
          </p>
        </Card>
        <Card>
          <CardTitle>Lift mastery</CardTitle>
          {mastery ? (
            <>
              <MasteryList lifts={mastery} unit={unit} />
              <p className="mt-3 text-xs text-muted">Your best estimated 1RM against your body weight. Gives no XP.</p>
            </>
          ) : (
            <p className="text-sm text-muted">Add your body weight in Profile to see bronze, silver and gold for your main lifts.</p>
          )}
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
