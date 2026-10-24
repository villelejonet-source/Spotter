/** The insights page's shape while it loads: four tiles, the balance card, the heatmap and two charts. */
export default function InsightsLoading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-6 h-8 w-32 rounded-lg bg-surface-high" />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="h-28 rounded-2xl bg-surface-high" />
        ))}
      </div>
      <div className="mt-4 h-96 rounded-2xl bg-surface-high" />
      <div className="mt-4 h-40 rounded-2xl bg-surface-high" />
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <div className="h-64 rounded-2xl bg-surface-high" />
        <div className="h-64 rounded-2xl bg-surface-high" />
      </div>
    </div>
  );
}
