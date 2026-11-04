/** The year in review's shape while it loads: four stats, then the highlight and badge cards. */
export default function YearInReviewLoading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-3 h-4 w-16 rounded bg-surface-high" />
      <div className="mb-6 h-8 w-64 rounded-lg bg-surface-high" />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="h-24 rounded-2xl bg-surface-high" />
        ))}
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <div className="h-36 rounded-2xl bg-surface-high" />
        <div className="h-36 rounded-2xl bg-surface-high" />
      </div>
    </div>
  );
}
