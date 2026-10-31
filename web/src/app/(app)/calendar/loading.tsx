/** The calendar's shape while the month loads: the month title, its arrows and the grid. */
export default function CalendarLoading() {
  return (
    <div className="animate-pulse" aria-busy="true" aria-label="Loading">
      <div className="mb-3 h-8 w-48 rounded-lg bg-surface-high" />
      <div className="mb-6 h-10 w-40 rounded-lg bg-surface-high" />
      <div className="grid grid-cols-7 gap-1 rounded-2xl bg-surface-high p-2">
        {Array.from({ length: 35 }, (_, i) => (
          <div key={i} className="h-16 rounded-lg bg-surface-low md:h-24" />
        ))}
      </div>
    </div>
  );
}
