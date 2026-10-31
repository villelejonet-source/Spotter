/** The exercise list's shape while it loads: the filters card and the list. */
export default function LibraryLoading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-3 h-8 w-36 rounded-lg bg-surface-high" />
      <div className="mb-6 h-10 w-36 rounded-xl bg-surface-high" />
      <div className="mb-4 h-56 rounded-2xl bg-surface-high" />
      <div className="space-y-px rounded-2xl bg-surface-high p-2">
        {Array.from({ length: 8 }, (_, i) => (
          <div key={i} className="h-12 rounded-lg bg-surface-low" />
        ))}
      </div>
    </div>
  );
}
