/** The levels page's shape while it loads: the level and XP cards, then the badge grid. */
export default function LevelsLoading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-6 h-8 w-48 rounded-lg bg-surface-high" />
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="h-28 rounded-2xl bg-surface-high" />
        <div className="h-44 rounded-2xl bg-surface-high" />
      </div>
      <div className="mt-6 mb-3 h-4 w-32 rounded bg-surface-high" />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 9 }, (_, i) => (
          <div key={i} className="h-28 rounded-2xl bg-surface-high" />
        ))}
      </div>
    </div>
  );
}
