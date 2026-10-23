# Spotter platform: hosting, environments and versioning

_Written 2026-10-24. Covers the Android app, the website (`web/`) and the Supabase backend._

## How it fits together today

```
Android app (Room, offline-first) ──push_rows / select──▶ Supabase (Auth + Postgres: sync_rows)
                                                              ▲
Website (Next.js on Vercel) ── server components, RLS ────────┘
```

- **Single source of truth per user** is `sync_rows`: one JSON row per Room row, last-write-wins on
  the client's `updated_at`. The phone owns the schema; the website writes the same shapes.
- **No server of our own.** The website talks to Supabase over HTTPS (PostgREST), so there are no
  database connections to pool and nothing to keep running besides Vercel and Supabase.
- **Security boundary is the database**: row-level security on `sync_rows`, security-invoker
  views, revoked anon access. The website's checks (middleware, server actions) are extra layers.

## Environments

| | Local | Staging | Production |
|---|---|---|---|
| Supabase | `supabase start` (Docker) | separate project, EU region | `gaherwthntdonrsuowbh` |
| Website | `npm run dev` | Vercel preview deploy per pull request | Vercel production (`main`) |
| App | debug build → local or staging | debug / internal test track | Play production |
| Data | seed script | test accounts only | real users |

Deferred on 2026-10-24 (revisit before public launch or paid features). In the dashboards:
1. **Create a staging Supabase project** in the same region as production. Apply
   `supabase/migrations` to it, and point Vercel's *Preview* environment variables at it, so
   pull-request previews never touch real data.
2. **Point debug builds at staging**: add `supabase.staging.url` / `supabase.staging.publishableKey`
   to `local.properties` and pick them per build type in `app/build.gradle.kts`.
3. **Check the production region** (Supabase → Project Settings → General). For Swedish users and
   GDPR it should be in the EU (Stockholm `eu-north-1` or Frankfurt `eu-central-1`). Set the Vercel
   function region to the same place (`arn1` or `fra1`); every page makes several database calls,
   so a cross-continent hop multiplies.

## Hosting and what it costs as it grows

| Users (monthly active) | Supabase | Vercel | Notes |
|---|---|---|---|
| < 100 (now) | Free | Hobby | Free projects pause after a week idle and have **no backups**. Hobby is non-commercial only. |
| Launch / paid features (deferred for now) | **Pro, $25/mo** | **Pro, $20/mo** | Daily backups, no pausing, 8 GB database included. Required once money is involved. |
| ~10 000 | Pro + Point-in-time recovery add-on, small compute upgrade | Pro | ~5 000 synced rows per active user per year ≈ 50 M rows / ~15 GB a year. |
| ~100 000 | Larger compute, read replica for the web | Pro / Enterprise | Move heavy reads off `sync_rows` (see "Scaling the data model"). |

The phone works offline, so a backend outage only delays sync; the website is the part that
needs the backend to be up.

## Delivery pipeline

1. **Branch and pull request** for every change; `main` is protected and always deployable.
2. **CI on every pull request** (`.github/workflows/ci.yml`):
   - Website: lint, type check, unit tests (including the payload-vs-Room-schema test), build.
   - App: unit tests and a debug build.
3. **Preview**: Vercel builds every pull request against staging.
4. **Database migrations** live in `supabase/migrations/` (timestamped, additive). They're applied
   to staging when a pull request merges and to production with the Supabase CLI
   (`supabase db push`) as an explicit step before deploying code that needs them. Later this can
   run from CI with a protected "production" environment that needs a manual approval.
5. **Website release**: merging to `main` deploys production. Vercel keeps every deploy, so a bad
   release is rolled back in one click.
6. **App release**: as in `docs/play-store/README.md`, plus staged rollouts in Play (10 % → 50 % →
   100 %) once there are enough users for it to matter.

## Versioning: keeping app, website and database compatible

There are always several app versions in use, while the website and database run one version.
Rules that keep that safe:

1. **Database changes are expand-then-contract.** Add tables, columns, views and functions; never
   rename or drop something an app version still in use reads. Remove things only after the
   oldest supported app no longer uses them.
2. **Room migrations that add a synced column must give it a default** (`ALTER TABLE … ADD COLUMN
   … DEFAULT …`). Rows written by older apps or by the website won't have the field, and the
   phone inserts with `ContentValues`, so a NOT NULL column without a default would fail there.
3. **The phone ignores payload fields it doesn't know, and skips rows it can't hold**
   (`LocalSyncStore.upsert`), such as a plan exercise using an exercise from a newer catalog,
   so a newer app or the website can't block an older app's sync. Such rows simply don't
   appear on that phone until it's updated.
4. **The website writes the newest Room schema it knows.** `web/src/lib/sync-writer.test.ts` reads
   the newest exported schema (`core/data/schemas/…/N.json`), so a Room change the website hasn't
   followed fails CI.
5. **Minimum supported app version** (to build): a public `app_config` row with
   `min_version_code`. The app checks it on sync and asks for an update instead of syncing; this
   is the emergency brake for an incompatible change.
6. **Record what each phone runs** (to build): on sync the phone upserts a `device` row with its
   `versionCode` and Room schema version. The website can then warn ("your phone needs an update
   before this plan syncs") and support can see which versions are still in use before
   contracting the schema.

## Scaling the data model

`sync_rows` (one generic JSON table) is right for sync: the phone never needs a server
migration. It's not ideal for reading many users' data at once, which trainers and analytics will
need. The plan:

1. **Now:** expression indexes for the joins the views do, and `v_session_summary` aggregated with
   one grouped join instead of a subquery per session, and a set-history view the website
   filters on the server (`supabase/migrations/20261024000000_hardening_and_performance.sql`).
   Pages load a user's data in a handful of requests.
2. **When trainers arrive:** typed tables (`sessions`, `sets`, …) maintained by a trigger on
   `sync_rows`, with their own indexes and RLS (owner, or linked trainer). The website reads those;
   sync keeps writing `sync_rows`.
3. **Past ~50 M rows:** hash-partition `sync_rows` by `user_id`, and pre-aggregate weekly stats.

## Operations

- **Backups:** daily on Supabase Pro; add point-in-time recovery once paying users exist.
  Users can also export their data from the website (JSON/CSV).
- **Errors and logs:** Vercel function logs and Supabase logs to start with. The privacy policy
  promises no analytics; if error reporting (e.g. Sentry) is added, configure it without
  personal data and update the policy first.
- **Abuse limits:** Supabase Auth rate limits on sign-in emails (dashboard). `push_rows` is the
  only way to write: it accepts known table names only, at most 1000 rows per call and 16 KB
  per row, and always writes as the caller.
- **Secrets:** only the publishable key is used by the app and website. The secret/service key is
  never put in Vercel or the app; anything that needs it later (webhooks for payments) goes in a
  Supabase Edge Function.
- **GDPR:** EU hosting, data processing agreements with Supabase and Vercel (both offer them),
  in-app and on-web account deletion, data export.
