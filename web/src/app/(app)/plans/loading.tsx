/** The plans list's shape while the plans load: a card per plan with its days. */
export default function PlansLoading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-3 h-8 w-28 rounded-lg bg-surface-high" />
      <div className="mb-6 h-10 w-28 rounded-xl bg-surface-high" />
      <div className="grid gap-4 lg:grid-cols-2">
        {Array.from({ length: 3 }, (_, i) => (
          <div key={i} className="h-40 rounded-2xl bg-surface-high" />
        ))}
      </div>
    </div>
  );
}
