/** The profile's shape while it loads: the training profile form and the cards below it. */
export default function ProfileLoading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-6 h-8 w-28 rounded-lg bg-surface-high" />
      <div className="space-y-3 rounded-2xl bg-surface-high p-5">
        {Array.from({ length: 6 }, (_, i) => (
          <div key={i} className="h-11 rounded-lg bg-surface-low" />
        ))}
      </div>
      <div className="mt-4 h-48 rounded-2xl bg-surface-high" />
    </div>
  );
}
