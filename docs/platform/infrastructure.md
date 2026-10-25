# Spotter platform: hosting, environments and versioning

_Written 2026-10-24; updated the same day with the audit (`audit-2026-10.md`) and on 2026-10-25
when the website went live. Covers the Android app, the website (`web/`) and the Supabase backend._

## How it fits together today

```
Android app (Room, offline-first) ──push_rows / select──▶ Supabase (Auth + Postgres: sync_rows)
                                                              ▲
Website (Next.js on Vercel) ── server components, RLS ────────┘
```

Production addresses: website **https://spotter.northstarsolutions.se** (Vercel, functions in
London `lhr1`; setup in `web/README.md`, "Deploy"), Supabase project `gaherwthntdonrsuowbh`
(London, `eu-west-2`). Auth emails are sent as `noreply@northstarsolutions.se` through Google
Workspace (`auth-emails.md`, "SMTP sender").

- **Single source of truth per user** is `sync_rows`: one JSON row per Room row, last-write-wins on
  the client's `updated_at`. The phone owns the schema; the website writes the same shapes.
- **No server of our own.** The website talks to Supabase over HTTPS (PostgREST), so there are no
  database connections to pool and nothing to keep running besides Vercel and Supabase.
- **Security boundary is the database**: row-level security on `sync_rows`, security-invoker
  views, revoked anon access. The website's checks (middleware, server actions) are extra layers.
- **Units have one source of truth**: the synced `user_profile.units` field (kg or lb). The app
  writes it when its unit setting changes (and mirrors it back into the setting on sync); the
  website reads the same field. Neither side keeps a unit preference of its own.

## Sign-in links: PKCE now, App Links later

Every auth email (confirm, sign-in, reset) carries a **link** and a **code**
(`auth-emails.md`). They are deliberately different in where they work:

- The **link works only on the device that asked for it.** The app and the website use the
  PKCE flow: the client that starts a flow keeps a random secret (the code verifier) and sends
  its hash to Supabase; the link that comes back carries a one-time `code`, which the client
  exchanges for a session together with the secret it kept. A link opened anywhere else has no
  verifier to pair with and is useless. The website has used this since the start
  (`@supabase/ssr`); the app switched to it in the 2026-10 audit. As implemented in the app:
  `SupabaseProvider` installs Auth with `flowType = FlowType.PKCE`; `SignInLinks.parse` accepts
  only `spotter://login?code=…` and ignores everything else, so a link carrying a session in the
  fragment (`#access_token=…`) is dropped; a password-reset link is recognised from the
  exchanged token's `amr` claim containing `recovery`, with the old `type=recovery` query
  parameter as a fallback. The session itself is stored in `EncryptedSharedPreferences` (file
  `spotter_auth`), with a one-time migration from the previous plain store. Before the audit the
  app used the implicit flow: the link itself carried the session in the URL fragment, and the
  app imported whatever session a link handed it, so a link crafted by someone else could sign a
  phone into their account. The audit fix also stops the first sync after a link sign-in from
  uploading the phone's data to an empty account without asking: the app asks first.
- The **code works anywhere.** It's the 6-digit `{{ .Token }}` for the same email, typed into
  the app or the website, and it is tied to the email address, not to a device.

**Follow-up, now unblocked (the website has had its domain since 2026-10-25): https App Links.** `spotter://login` is a
custom scheme, and Android lets any installed app register for a custom scheme, so another app
could in principle receive our links (with PKCE it can't use them, but it could swallow them).
The fix is to make the link an https URL on our domain, e.g. `https://spotter.northstarsolutions.se/auth/app`, with
`android:autoVerify="true"` on the intent filter and the website serving
`/.well-known/assetlinks.json` listing the app's package name and signing-certificate
fingerprint (the Play App Signing key, from Play Console → App integrity). Android verifies the
file at install time and routes the link to this app only. The page at that URL also gives
desktop users somewhere sensible to land. Was deferred until the domain existed (listed in
`audit-2026-10.md`).

## Environments

| | Local | Staging | Production |
|---|---|---|---|
| Supabase | `supabase start` (Docker) | separate project, EU region | `gaherwthntdonrsuowbh` |
| Website | `npm run dev` | Vercel preview deploy per pull request | Vercel production (`main`), spotter.northstarsolutions.se |
| App | debug build → local or staging | debug / internal test track | Play production |
| Data | seed script | test accounts only | real users |

Deferred on 2026-10-24 (revisit before public launch or paid features). In the dashboards:
1. **Create a staging Supabase project** in the same region as production. Apply
   `supabase/migrations` to it, and point Vercel's *Preview* environment variables at it, so
   pull-request previews never touch real data.
2. **Point debug builds at staging**: add `supabase.staging.url` / `supabase.staging.publishableKey`
   to `local.properties` and pick them per build type in `app/build.gradle.kts`.
3. **Production region: checked 2026-10-25, staying in London (`eu-west-2`).** Supabase can't
   move a project; moving means a new project, a new URL and key in a new app release (old
   installs keep syncing to the old project until they update), and copying accounts with their
   password hashes. London is close to Sweden (~30 ms), only the website waits on the database,
   and the UK has an EU adequacy decision, so GDPR allows it. If an EU region is ever wanted,
   move once, before public launch, together with creating the staging project. The Vercel
   function region is London (`lhr1`) to match; every page makes several database calls, so a
   cross-region hop would multiply.
4. **Send auth email through a send-only provider** (Resend, Postmark, Brevo or Amazon SES).
   Today Supabase logs in to Google Workspace with an app password on a Workspace user
   (`auth-emails.md`, "SMTP sender"); that password can also read the mailbox. A provider
   verified for `northstarsolutions.se` (DNS records at Inleed) gives Supabase a key that can
   only send, with higher limits and delivery logs. Switch before public launch, and update the
   privacy policy's "sent … through Google Workspace" line when it happens.

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
2. **CI on every pull request** (`.github/workflows/ci.yml`; read-only `permissions`, every
   action pinned to a commit SHA):
   - Website: `npm audit` on production dependencies (visible, non-blocking), lint, type check,
     unit tests (including the payload-vs-Room-schema test), build.
   - App: unit tests, Roborazzi screenshot tests against the committed goldens, `lintRelease`,
     a debug build and a debug-signed `assembleRelease` (proves the R8 shrink step works). On
     failure the screenshot diffs and lint reports are uploaded as a workflow artifact.
3. **Preview**: Vercel builds every pull request against staging.
4. **Database migrations**: see the next section. Applied to staging when a pull request merges
   and to production as an explicit step before deploying code that needs them. Later this can
   run from CI with a protected "production" environment that needs a manual approval.
5. **Website release**: merging to `main` deploys production. Vercel keeps every deploy, so a bad
   release is rolled back in one click.
6. **App release**: as in `docs/play-store/README.md`, plus staged rollouts in Play (10 % → 50 % →
   100 %) once there are enough users for it to matter.

## Database migrations

Migrations live in `supabase/migrations/<timestamp>_<name>.sql`, are additive and are written
so they can be re-run (`create or replace`, `if not exists`, `drop policy if exists` before
`create policy`). The timestamp prefix is the migration's **version**, and versions must be
unique: two files with the same prefix can't both be recorded.

Apply them in one of two ways, both of which record the version in the server's history table
(`supabase_migrations.schema_migrations`):

- **Supabase CLI**, linked once per machine: `supabase link --project-ref gaherwthntdonrsuowbh`,
  then `supabase db push` (applies every file not yet in the history, in order; `--dry-run`
  shows what it would do).
- **Supabase MCP** (`.mcp.json`): `apply_migration` with the file's name and SQL. Same effect,
  usable from a Claude Code session.

**Never paste a migration into the SQL editor.** The SQL runs, but nothing is recorded, so the
history no longer says what the database contains, and the next `db push` tries to apply the
file again. That was the state of production on 2026-10-24; since the audit the history matches. The files are:

| Version | File | On production |
|---|---|---|
| `20261004000000` | `sync.sql` (sync_rows, RLS, first `push_rows`) | applied via the editor, now recorded |
| `20261023000000` | `web_views.sql` (typed views for the website) | applied via the editor, now recorded |
| `20261024000000` | `hardening_and_performance.sql` (`push_rows` limits, indexes, `v_set_history`) | applied via the editor, now recorded |
| `20261025000000` | `scheduled_sessions.sql` (`scheduled_session`, `v_scheduled_session`) | applied via the editor, now recorded |
| `20261026000000` | `audit_hardening.sql` (policies with `(select auth.uid())`, `push_rows` checks and row cap, revoked grants, `delete_my_account` cleanup, `v_exercise_usage` which `/library` needs) | applied with the audit, recorded |
| `20261027000000` | `trainer_foundation.sql` (`profiles`, `trainer_links` and their functions, `can_read_user` in the `sync_rows` read policy, `delete_my_account` removes profile and links) | applied 2026-10-25 with `supabase db push --linked`, recorded |
| `20261028000000` | `devices_and_admin.sql` (`devices` and `check_in_device`, `admins` and `admin_stats` for `/admin`, `delete_my_account` removes devices) | applied 2026-10-25 with `supabase db push --linked`, recorded |
| `20261029000000` | `admin_tag.sql` (admin is the `app_metadata.admin` tag; `admins` rows become tags and the table is dropped; `admin_stats` checks the tag) | applied 2026-10-25 with `supabase db push --linked`, recorded |

On 2026-10-24 the history was empty (`list_migrations` returned `[]`); on 2026-10-25 it lists
all five versions (six since 2026-10-25). If it ever drifts again, repair it the same way: after linking, name the
versions that are already live, then push the rest:

```bash
supabase migration repair --status applied 20261004000000 20261023000000 20261024000000 20261025000000
supabase migration list   # remote should now show the four; 20261026000000 local only
supabase db push          # applies 20261026000000_audit_hardening.sql and records it
```

From then on apply new migrations only via `db push` or `apply_migration`. Check `supabase
migration list` before a push whenever in doubt.

**Test a migration locally before applying it.** There's no staging project, so new migrations
are checked on a local stack first (needs Docker; OrbStack on this Mac). `supabase/config.toml`
holds the local settings, not production's:

```bash
supabase start -x studio,imgproxy,mailpit,realtime,storage-api,edge-runtime,logflare,vector,supavisor,postgres-meta
supabase db reset   # rebuilds the local database from every file in supabase/migrations/
for f in supabase/tests/*.sql; do docker exec -i supabase_db_spotter psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -t < "$f"; done
supabase stop
```

Each test file prints "… all checks passed" or stops at the first failing check. For a policy
change, also `explain` the app's pull as `authenticated` and check it still uses
`sync_rows_pull_idx` (an unfiltered pull, as older apps send, included).

## Versioning: keeping app, website and database compatible

There are always several app versions in use, while the website and database run one version.
Rules that keep that safe:

1. **Database changes are expand-then-contract.** Add tables, columns, views and functions; never
   rename or drop something an app version still in use reads. Remove things only after the
   oldest supported app no longer uses them.
2. **Room migrations that add a synced column must give it a default** (`ALTER TABLE … ADD COLUMN
   … DEFAULT …`). Rows written by older apps or by the website won't have the field, and the
   phone inserts with `ContentValues`, so a NOT NULL column without a default would fail there.
   **Every Room migration must also re-create the sync triggers** through
   `SyncSchema.TriggerSpec` (the triggers that stamp `updatedAt` and write tombstones are created
   once, in the database callback, not on every open, so a migration that rebuilds or alters a
   synced table has to put them back). The schema is at version 8 (7 added scheduled sessions,
   8 the audit's indexes); the exported JSON lives in `core/data/schemas/`.
   **The seeded exercise catalog** (`core/data/src/main/assets/exercises.json`) is versioned by
   `EXERCISE_CATALOG_VERSION` in `ExerciseSeeder`; bump it whenever the JSON changes, or
   installed apps keep the old catalog.
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
6. **Record what each phone runs** (built, from versionCode 7): at the end of every successful
   sync the app calls `check_in_device(device_id, version_code, schema_version)`
   (`SyncEngine`, `DeviceIdentity`), which upserts the caller's row in `devices`: a random
   per-install UUID (kept in plain SharedPreferences, so it isn't copied to a new phone), the
   `versionCode`, the Room schema version and `last_synced_at`. Each account keeps its 20 most
   recently synced installs; `delete_my_account` removes them. A failed check-in never fails
   the sync. The versionCode spread is on the usage overview (`/admin`, see "Operations").
   An account that syncs but never checks in runs versionCode 6 or older (or uses only the
   website). Still to build on top of it: the website warning ("your phone needs an update
   before this plan syncs").

## Scaling the data model

`sync_rows` (one generic JSON table) is right for sync: the phone never needs a server
migration. It's not ideal for reading many users' data at once, which trainers and analytics will
need. The plan:

1. **Now:** expression indexes for the joins the views do, and `v_session_summary` aggregated with
   one grouped join instead of a subquery per session, and a set-history view the website
   filters on the server (`supabase/migrations/20261024000000_hardening_and_performance.sql`).
   Pages load a user's data in a handful of requests.
2. **Scheduled workouts** (Room v7, `scheduled_session`) follow the same pattern: one more synced
   table, one more view (`v_scheduled_session`), and `push_rows` allows the new table name. Older
   apps skip the rows until they update.
3. **When trainers arrive:** typed tables (`sessions`, `sets`, …) maintained by a trigger on
   `sync_rows`, with their own indexes and RLS (owner, or linked trainer). The website reads those;
   sync keeps writing `sync_rows`. The groundwork is in place, see "Trainer foundation" below.
4. **Past ~50 M rows:** hash-partition `sync_rows` by `user_id`, and pre-aggregate weekly stats.

### Trainer foundation

Built (`supabase/migrations/20261027000000_trainer_foundation.sql`, tests in
`supabase/tests/trainer_links.sql`). Trainers are **still switched off**: no account can read
another account's rows.

- **Owner filters everywhere.** The app's pull and `hasRows` filter `sync_rows` on
  `user_id = signed-in user` (`SupabaseSyncRemote`, from versionCode 7). Every website read goes
  through `ownedBy()` in `web/src/lib/queries.ts`, which adds `user_id = signed-in user`, and the
  query functions only accept that client. RLS stays as the second wall. Without these filters a
  trainer's phone would pull client rows into its own database and push them back as its own.
- **`profiles`**: user id, display name, timestamps; each user reads and writes only their own
  row. There is no fixed trainer or client role: any account can be either, or both.
- **`trainer_links`**: trainer, client, status (`pending` → `active` → `ended`), a 32-character
  random invite code, and when it was created, accepted and ended. Trainer ≠ client. A client
  has at most one trainer at a time; a trainer can have any number of clients. Each side reads its own links. There are no direct writes, only:
  - `create_trainer_invite(label)`: a pending link with the caller as trainer (at most 20 open
    invites). The optional label ("Anna", "Tuesday group") tells open invites apart and is
    cleared when the invite is accepted;
  - `accept_trainer_invite(code)`: the caller becomes the client of a pending invite less than 14
    days old; nobody can activate a link for anyone else;
  - `end_trainer_link(id)`: either side ends it at any time.
- **`can_read_user(owner_id)`**: the one access check in the `sync_rows` read policy. Today it is
  `owner_id = auth.uid()`. It's plain SQL without `SET search_path` so Postgres inlines it into
  the policy (the advisor's search_path WARN on it is expected).
- **`delete_my_account()`** also deletes the account's profile and every link it's part of.

Applied to production on 2026-10-25. Advisors afterwards, all expected: security WARNs for
the SECURITY DEFINER functions callable by signed-in users (`push_rows`, `delete_my_account`
and the three link functions) and for `can_read_user`'s search_path (needed for inlining),
plus the existing leaked-password-protection WARN; performance INFOs for unused indexes (the
two `trainer_links` indexes, on an empty table, and `sync_rows_set_entry_exercise_idx`).
`explain` on production confirms an unfiltered pull still uses `sync_rows_pull_idx`.

**Left to do before trainers can read a client's data, in order:**

1. **Release versionCode 7** (`/release`) with the owner-filtered pull. Apps on versionCode 6
   or older pull `sync_rows` unfiltered, so a trainer still on one of those would pull client
   rows. Before step 3, make sure no trainer runs an old app. Each install now reports its
   `versionCode` on sync ("Versioning", step 6), but an old app never reports anything, so
   the gate is the absence of old apps, not the presence of new ones: wait until `/admin`
   shows (close to) no active accounts without a check-in, and have `create_trainer_invite`
   refuse an account that synced rows in the last 30 days without a versionCode ≥ 7 check-in
   after them.
2. **Build the read path for trainers:** typed tables or views for what a trainer sees (step 3
   above), plus a `profiles` read policy so a linked trainer sees the client's display name.
3. **Switch reads on:** change only `can_read_user` to also allow an active link (the `exists`
   query in the function's comment), and add SQL tests that a trainer reads an active client,
   loses access the moment the link ends, and still never sees pending or ended clients' rows.
4. **UI:** invites, accepting a code, the client list and ending a link, on the web and in the
   app. Then plan suggestions and, if wanted, a paid trainer tier (PLAN.md step 4).

## Operations

- **Backups:** daily on Supabase Pro; add point-in-time recovery once paying users exist.
  Users can also export their data from the website (JSON/CSV).
- **Usage overview:** `/admin` on the website (an Admin item in the navigation for accounts
  tagged admin) shows accounts and
  sign-ups per week, last sign-in, last active (the newer of the account's newest synced row
  and its newest device check-in; the app stays signed in for months, so last sign-in says
  little), workouts and weekly active users per week, and the versionCode spread. It reads
  `admin_stats()`, a SECURITY DEFINER function that answers only for accounts tagged admin:
  `"admin": true` in the auth user's `app_metadata`, which only the SQL editor or the service
  key can set (unlike `user_metadata`, which users change themselves). Everyone else gets "not
  found". No allow-list or secret key on the website. Dates and counts only, never workout
  contents. The tag is in the session token, so the website shows the Admin menu without a
  query; it appears or disappears when the token next refreshes (sign out and in to see it at
  once), while `admin_stats` checks the tag in the database on every call. Tag or untag an
  account in the SQL editor (the repository is public, so never in a migration):
  `update auth.users set raw_app_meta_data = coalesce(raw_app_meta_data, '{}') || '{"admin": true}' where email = '…';`
  `update auth.users set raw_app_meta_data = raw_app_meta_data - 'admin' where email = '…';`
  Applied to production on 2026-10-25. Advisors afterwards, all expected: the security
  WARN for SECURITY DEFINER functions callable by signed-in users now also lists
  `check_in_device` and `admin_stats`; a new INFO says `admins` has RLS without policies (on
  purpose: no API role may read it; the table goes in `20261029000000`); and a performance INFO says `devices_last_synced_idx` is
  unused (the table is empty until versionCode 7). After `20261029000000` (also applied
  2026-10-25, which turned the one `admins` row into the tag) the `admins` INFO is gone with
  the table; everything else is unchanged.
- **Errors and logs:** Vercel function logs and Supabase logs to start with. The privacy policy
  promises no analytics or tracking services and describes exactly what the app reports on sync
  and what the usage overview shows; if error reporting (e.g. Sentry) is added, or the overview
  shows anything new, configure it without personal data and update the policy first.
- **Abuse limits:** Supabase Auth rate limits on sign-in emails (dashboard). `push_rows` is the
  only way to write: it accepts known table names only, at most 1000 rows per call and 16 KB
  per row, and always writes as the caller.
- **Secrets:** only the publishable key is used by the app and website (`web/.env.example` says
  so; `NEXT_PUBLIC_*` values are compiled into the browser bundle). The secret/service key is
  never put in Vercel or the app; anything that needs it later (webhooks for payments) goes in a
  Supabase Edge Function.
- **Tooling access:** `.mcp.json` enables a write-capable Supabase MCP server for this project.
  Anyone who authenticates with a Supabase account that can see the project can change the
  production database through it, so project membership is production access.
- **GDPR:** EU hosting, data processing agreements with Supabase and Vercel (both offer them),
  in-app and on-web account deletion, data export.
