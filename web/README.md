# Spotter Web

The Spotter website: sign in with the same account as the app's cloud backup to see your
training (dashboard, history, calendar, progress) and edit plans. Next.js App Router on
Vercel, Supabase for auth and data.

## How it gets data

The app syncs every Room row into one table, `sync_rows`, as JSON (`supabase/migrations/20261004000000_sync.sql`).
The web reads it through typed views (`supabase/migrations/20261023000000_web_views.sql`), which run with the
signed-in user's rights, so row-level security still limits everything to their own rows.

Plan edits are written back with the same `push_rows` function the app uses
(`src/lib/sync-writer.ts`): full Room-shaped payloads, parent sync ids for foreign keys,
last-write-wins timestamps, and explicit tombstones for removed days and exercises. The app
pulls them the next time it opens. `src/lib/sync-writer.test.ts` checks the payloads against
the app's exported Room schema, so a Room change that the web doesn't follow fails the tests.

The seeded exercise catalog isn't synced; `scripts/copy-catalog.mjs` copies
`core/data/src/main/assets/exercises.json` into `src/data/` before dev, build and test.

## Setup

1. Apply every file in `supabase/migrations/` in order: `supabase db push` with the Supabase CLI,
   or paste each into the SQL editor. They're all safe to re-run.
2. In Supabase → Authentication → URL Configuration, add `http://localhost:3000/auth/callback`
   and the deployed URL's `/auth/callback` to the redirect URLs.
3. `cp .env.example .env.local` and fill in the publishable key (same as `supabase.publishableKey`
   in the app's `local.properties`).

```bash
npm install
npm run dev
```

## Checks

```bash
npm run lint && npm run typecheck && npm test
```

## Deploy (Vercel)

Import the repository, set **Root Directory** to `web`, and add the two
`NEXT_PUBLIC_SUPABASE_*` variables. The build copies the exercise catalog from the Android
module, so the whole repository must be checked out (Vercel's default).

Days and weeks are counted in Europe/Stockholm (`src/lib/tz.ts`) until profiles carry a
time zone.
