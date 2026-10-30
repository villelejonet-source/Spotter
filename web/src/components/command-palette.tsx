"use client";

import clsx from "clsx";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { navPages } from "./nav";

/** Things to do, besides going to a page. */
const ACTIONS = [
  { href: "/plans/new", label: "New plan", section: "Plans" },
  { href: "/calendar", label: "Schedule a workout", section: "History" },
  { href: "/library/new", label: "New custom exercise", section: "Progress" },
  { href: "/profile", label: "Log body weight", section: "Profile" },
];

/**
 * "Jump to…": ⌘K (Ctrl+K) or the sidebar button opens it; type to filter pages and actions,
 * arrows to move, Enter to go. On the native <dialog>, so focus, Escape and the backdrop behave
 * as in every other dialog here.
 */
export function CommandPalette({ admin = false }: { admin?: boolean }) {
  const router = useRouter();
  const dialog = useRef<HTMLDialogElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [index, setIndex] = useState(0);

  const items = useMemo(() => {
    const all = [...navPages(admin), ...ACTIONS];
    const q = query.trim().toLowerCase();
    return q ? all.filter((i) => `${i.label} ${i.section}`.toLowerCase().includes(q)) : all;
  }, [admin, query]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    const d = dialog.current;
    if (!d) return;
    if (open && !d.open) {
      setQuery("");
      setIndex(0);
      d.showModal();
      input.current?.focus();
    } else if (!open && d.open) d.close();
  }, [open]);

  function go(href: string) {
    setOpen(false);
    router.push(href);
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "ArrowDown") setIndex((i) => Math.min(i + 1, items.length - 1));
    else if (e.key === "ArrowUp") setIndex((i) => Math.max(i - 1, 0));
    else if (e.key === "Enter" && items[index]) go(items[index].href);
    else return;
    e.preventDefault();
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex w-full items-center justify-between rounded-lg border border-outline bg-surface-lowest px-3 py-2 text-sm text-faint transition hover:border-outline-strong hover:text-muted"
      >
        Jump to…
        <kbd className="rounded border border-outline-strong px-1.5 font-mono text-[0.6875rem] text-muted">⌘K</kbd>
      </button>
      <dialog
        ref={dialog}
        aria-label="Jump to"
        onClose={() => setOpen(false)}
        onClick={(e) => e.target === e.currentTarget && setOpen(false)}
        className="mx-auto mt-[12vh] w-full max-w-lg overflow-hidden rounded-2xl border border-outline-strong bg-raised p-0 text-foreground shadow-2xl backdrop:bg-scrim backdrop:backdrop-blur-sm"
      >
        <input
          ref={input}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setIndex(0);
          }}
          onKeyDown={onKeyDown}
          placeholder="Go to a page or do something…"
          aria-label="Search pages and actions"
          aria-controls="palette-results"
          aria-activedescendant={items[index] ? `palette-${index}` : undefined}
          className="w-full border-b border-outline bg-transparent px-4 py-3.5 text-base outline-none"
        />
        <ul id="palette-results" role="listbox" aria-label="Results" className="max-h-80 overflow-y-auto p-2">
          {items.map((item, i) => (
            <li
              key={`${item.href}-${item.label}`}
              id={`palette-${i}`}
              role="option"
              aria-selected={i === index}
              onMouseEnter={() => setIndex(i)}
              onClick={() => go(item.href)}
              className={clsx("flex cursor-pointer items-center justify-between rounded-lg px-3 py-2.5 text-sm", i === index ? "bg-primary-container text-foreground" : "text-muted")}
            >
              {item.label}
              <span className="label text-faint">{item.section}</span>
            </li>
          ))}
          {!items.length && <li className="px-3 py-6 text-center text-sm text-muted">Nothing matches “{query}”.</li>}
        </ul>
      </dialog>
    </>
  );
}
