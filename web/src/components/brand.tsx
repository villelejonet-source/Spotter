import Link from "next/link";

/** The logo mark (a dumbbell on the glowing accent) and the name, linking to [href]. */
export function Brand({ href = "/" }: { href?: string }) {
  return (
    <Link href={href} className="tap inline-flex items-center gap-2.5 rounded-md text-[1.0625rem] font-semibold tracking-[-0.02em]">
      <span aria-hidden="true" className="btn-primary grid h-7 w-7 place-items-center rounded-lg">
        <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
          <path d="M6 12h12M4 8v8M8 6v12M16 6v12M20 8v8" />
        </svg>
      </span>
      Spotter
    </Link>
  );
}
