# Spotter Web

The Spotter website: sign in with the same account as the app's cloud backup to see your
training (today, history, calendar, progress) and edit plans. Next.js App Router on
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

The seeded exercise catalog isn't synced; `scripts/copy-shared.mjs` copies
`core/data/src/main/assets/exercises.json` into `src/data/` before dev, build and test.

## Privacy policy

**https://spotter.northstarsolutions.se/privacy**, public (the middleware lets it through
signed out) and linked from the sign-in pages and Profile. The text lives only in
`docs/play-store/privacy-policy.md`: `scripts/copy-shared.mjs` copies it into `src/data/` and
the page renders the markdown (`src/components/privacy-policy.tsx`), so edit the file, not the
page. It's the Play listing's privacy-policy and account-deletion URL; Play Console has to be
pointed at it by hand (`docs/play-store/README.md`, "App content").

## Setup

1. Apply every file in `supabase/migrations/` in order (currently `20261004000000_sync`,
   `20261023000000_web_views`, `20261024000000_hardening_and_performance`,
   `20261025000000_scheduled_sessions`, `20261026000000_audit_hardening`; the last adds
   `v_exercise_usage`, which `/library` needs) with `supabase db push` (linked CLI) or
   the Supabase MCP's `apply_migration`, never by pasting into the SQL editor: pasting runs the
   SQL but leaves no entry in the migration history, so the next `db push` would try to run it
   again. They're all safe to re-run. See "Database migrations" in
   `docs/platform/infrastructure.md`, including how to repair the history on the current project.
2. In Supabase → Authentication → URL Configuration, add `http://localhost:3000/auth/callback`
   and the deployed URL's `/auth/callback` to the redirect URLs (production has
   `https://spotter.northstarsolutions.se/auth/callback`, and it's also the Site URL).
3. Set up the sign-in emails and auth settings in `docs/platform/auth-emails.md` (email
   templates with both a link and a code, password rules, redirect URLs).
4. `cp .env.example .env.local` and fill in the publishable key (same as `supabase.publishableKey`
   in the app's `local.properties`). Only publishable values go in `.env*` files and
   `NEXT_PUBLIC_*` variables; they end up in the browser bundle.

```bash
npm install
npm run dev
```

## Checks

```bash
npm run lint && npm run typecheck && npm test
npm audit --omit=dev --audit-level=high   # CI runs this too, visibly but without failing the job
```

Units (kg or lb) come from the synced profile (`user_profile.units`); the app writes it when the
setting changes and both app and web read it from there, so the two can't disagree.

## Wording, formats and locale

The app is the source of truth for wording. `src/lib/labels.ts` mirrors the app's string
resources (goal names, "Lighter week", equipment and movement labels, progression reasons, set
and record formats): when wording changes, update the app's `strings.xml`/`labels.xml` **and**
`labels.ts` together. Dates and numbers use one locale constant in `src/lib/tz.ts` (`en-GB`)
next to the Europe/Stockholm time zone.

## Deploy (Vercel)

Live at **https://spotter.northstarsolutions.se** (Vercel project "Spotter", Hobby plan,
deployed 2026-10-25). Every push to `main` deploys production, and every pull request gets a
preview deployment, unless nothing the site is built from changed: `vercel.json`'s
`ignoreCommand` skips the build when neither `web/` nor the files it copies (the exercise
catalog `core/data/src/main/assets/exercises.json` and `docs/play-store/privacy-policy.md`)
differs from the last deployed commit. If that
commit isn't in Vercel's shallow clone, `git diff` fails and the build runs anyway.

How it's set up:

- **Project:** imported from `villelejonet-source/Spotter`, **Root Directory** `web`,
  framework Next.js, default build settings. The build copies the exercise catalog from the
  Android module and the privacy policy from `docs/`, so the whole repository must be checked out (Vercel's default).
- **Environment variables:** `NEXT_PUBLIC_SUPABASE_URL` and
  `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`, the same values as `.env.local`. Never a secret key.
- **Function region:** London (`lhr1`), Settings → Functions. The Supabase project is in
  London (`eu-west-2`) and every page makes several database calls, so the functions sit
  next to it. A region change needs a redeploy; Vercel's `x-vercel-id` response header shows
  it (`…::lhr1::…` on a dynamic route such as `/auth/callback`).
- **Domain:** `spotter.northstarsolutions.se` added under Settings → Domains; DNS for
  `northstarsolutions.se` is at Inleed, with a CNAME `spotter` →
  `102e40b717f6c9d8.vercel-dns-017.com`. Vercel issues the HTTPS certificate itself.
- **Supabase:** Site URL and redirect URLs as in `docs/platform/auth-emails.md`.

Checked on the live site on 2026-10-25: sign-up confirmed by link and by code, forgot password
by code with a new password saved, auth emails from `noreply@northstarsolutions.se` with SPF and
DKIM passing at an outside Gmail. Not re-checked live: sign-in with a password, reset by link,
"Email me a sign-in code".

Vercel Hobby is for non-commercial use only; move to Pro before Spotter earns money
(`docs/platform/infrastructure.md`, "Hosting").

Days and weeks are counted in Europe/Stockholm (`src/lib/tz.ts`) until profiles carry a
time zone.
