/**
 * A sync id the way the app makes them: 32 lowercase hex chars, like SQLite's
 * lower(hex(randomblob(16))). Lives apart from sync-writer.ts so client components can
 * make ids without pulling zod into the browser bundle.
 */
export function newSyncId(): string {
  return crypto.randomUUID().replace(/-/g, "");
}
