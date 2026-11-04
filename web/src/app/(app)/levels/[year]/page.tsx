import { notFound } from "next/navigation";
import { BadgeTile } from "@/components/levels";
import { BackLink, Card, CardTitle, EmptyState, PageHeader, Stat } from "@/components/ui";
import { exerciseName } from "@/lib/catalog";
import { getCatalog, getPlayerProgress, getShowLevels, getUnit, getYearInReview, preload } from "@/lib/data";
import { LOCALE } from "@/lib/tz";
import { formatVolume } from "@/lib/model";

const monthName = new Intl.DateTimeFormat(LOCALE, { month: "long", timeZone: "UTC" });

export async function generateMetadata({ params }: { params: Promise<{ year: string }> }) {
  return { title: `Your ${(await params).year}` };
}

/**
 * The year in review (the app's YearInReviewScreen): workouts, time, volume, records, the
 * strongest month, the favourite exercise, levels gained and badges unlocked. Today and
 * Levels link to it from December 1 to January 15; any year with workouts can be opened.
 */
export default async function YearInReviewPage({ params }: { params: Promise<{ year: string }> }) {
  const year = Number((await params).year);
  if (!Number.isInteger(year) || year < 2000 || year > 2100) notFound();
  preload(getYearInReview(year), getPlayerProgress(), getCatalog(), getUnit());
  if (!(await getShowLevels())) notFound();
  const [review, progress, catalog, unit] = await Promise.all([getYearInReview(year), getPlayerProgress(), getCatalog(), getUnit()]);

  if (!review) {
    return (
      <>
        <BackLink href="/levels">Levels</BackLink>
        <PageHeader title={`Your ${year}`} />
        <EmptyState title={`No workouts in ${year}`}>Finish a workout of 10 minutes or more and it counts here.</EmptyState>
      </>
    );
  }

  const hours = Math.round(review.minutes / 60);
  const badges = progress.badges.filter((b) => review.badges.includes(b.badge));
  return (
    <>
      <BackLink href="/levels">Levels</BackLink>
      <PageHeader title={`Your ${year} in training`} subtitle={`Level ${review.levelStart} to level ${review.levelEnd}`} />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Workouts" value={review.workouts} />
        <Stat label="Time" value={`${hours} h`} />
        <Stat label="Volume" value={formatVolume(review.volumeKg, unit)} />
        <Stat label="PRs" value={review.records} />
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardTitle>Highlights</CardTitle>
          <dl className="space-y-2 text-sm">
            {review.strongestMonth != null && (
              <div className="flex justify-between gap-3">
                <dt className="text-muted">Strongest month</dt>
                <dd>{monthName.format(Date.UTC(year, review.strongestMonth - 1, 1))}</dd>
              </div>
            )}
            {review.favouriteExercise != null && (
              <div className="flex justify-between gap-3">
                <dt className="text-muted">Favourite exercise</dt>
                <dd className="text-right">{exerciseName(catalog, review.favouriteExercise)}</dd>
              </div>
            )}
            <div className="flex justify-between gap-3">
              <dt className="text-muted">XP earned</dt>
              <dd className="font-mono tabular-nums">{review.xp.toLocaleString(LOCALE)}</dd>
            </div>
          </dl>
        </Card>
        <Card>
          <CardTitle>Badges unlocked</CardTitle>
          <p className="text-sm">{badges.length === 0 ? "None this year, there's always next year." : `${badges.length} this year.`}</p>
        </Card>
      </div>
      {badges.length > 0 && (
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {badges.map((b) => (
            <BadgeTile key={b.badge} badge={b} />
          ))}
        </div>
      )}
    </>
  );
}
