/** Shown the moment a menu link is clicked, while the page's data loads. */
export default function Loading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-6 h-8 w-48 rounded-lg bg-surface-high" />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="h-28 rounded-2xl bg-surface-high" />
        ))}
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <div className="h-72 rounded-2xl bg-surface-high lg:col-span-2" />
        <div className="h-72 rounded-2xl bg-surface-high" />
      </div>
    </div>
  );
}
