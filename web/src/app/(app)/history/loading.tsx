/** The history list's shape while its workouts load. */
export default function HistoryLoading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-6 h-8 w-32 rounded-lg bg-surface-high" />
      {Array.from({ length: 2 }, (_, m) => (
        <div key={m} className="mb-6">
          <div className="mb-2 h-4 w-28 rounded bg-surface-high" />
          <div className="space-y-px rounded-2xl bg-surface-high p-2">
            {Array.from({ length: 4 }, (_, i) => (
              <div key={i} className="h-16 rounded-xl bg-surface-low" />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
