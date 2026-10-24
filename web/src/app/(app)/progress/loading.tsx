/** The progress overview's shape while the lifts load: a chart card and the table of lifts. */
export default function ProgressLoading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-6 h-8 w-36 rounded-lg bg-surface-high" />
      <div className="mb-4 h-72 rounded-2xl bg-surface-high" />
      <div className="space-y-px rounded-2xl bg-surface-high p-2">
        {Array.from({ length: 8 }, (_, i) => (
          <div key={i} className="h-11 rounded-lg bg-surface-low" />
        ))}
      </div>
    </div>
  );
}
