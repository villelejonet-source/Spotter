# Spotter

Offline-first gym app (Kotlin, Jetpack Compose, Material 3) for Android, being ported to iOS with
Kotlin Multiplatform; see [docs/platform/ios-port.md](docs/platform/ios-port.md). See [PLAN.md](PLAN.md).

## Build and run

There's no system JDK on this machine, so point Gradle at Android Studio's bundled one:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest :core:data:testAndroidHostTest :core:engine:jvmTest :core:model:jvmTest   # all unit tests
./gradlew verifyRoborazziDebug     # screenshot tests (recordRoborazziDebug updates the goldens)
./gradlew :app:lintRelease
./gradlew :app:assembleRelease     # debug-signed R8 build; CI runs it to prove shrinking works
./gradlew :core:model:iosSimulatorArm64Test :core:engine:iosSimulatorArm64Test :core:data:iosSimulatorArm64Test :core:ui:iosSimulatorArm64Test   # shared code on iOS (needs Xcode)
```

CI (`.github/workflows/ci.yml`) runs all of the above plus the website's lint, type check,
tests and build on every pull request. Actions are pinned to commit SHAs; the workflow has
read-only repository permissions.

Screenshot goldens (light, dark and large text) live in `*/src/test/screenshots`. Releasing
to Google Play: see [docs/play-store/README.md](docs/play-store/README.md).

Or open the folder in Android Studio and run the `app` configuration.

## Layout

| Module | What's in it |
|---|---|
| `app` | Application, MainActivity, bottom-bar navigation shell |
| `core/model` | Domain types and enums; Kotlin Multiplatform (JVM + iOS), dates are `kotlinx.datetime` / `kotlin.time` |
| `core/data` | Kotlin Multiplatform (Android + iOS): Room schema (exported to `core/data/schemas`), DAOs, repositories, DataStore preferences, exercise seeder, cloud sync and accounts, and `DataGraph`, which builds them |
| `core/data-android` | Android wiring for `core/data`: the Hilt module (exposes `DataGraph`), WorkManager sync, the encrypted session store (`AuthStorage`) |
| `core/engine` | Training logic, Kotlin Multiplatform (JVM + iOS), unit-tested on both: plan generator, progression, swap ranking and weight conversion, PR detection, muscle balance, body-weight trend |
| `core/ui` | Compose Multiplatform (Android + iOS): theme (branded light/dark palette + dynamic color on Android), shared components, labels and formatting; strings are Compose resources in `src/commonMain/composeResources` |
| `feature/*` | One module per tab (`today`, `history`, `progress`, `settings` = Profile), plus `session` (workout logger, rest timer service, summary), `library` (exercise picker), `onboarding` (questionnaire + plan summary) and `plan` (plan overview) |
| `shared` | The iOS framework (`Shared`): the shared modules plus `SpotterIos`, what the Swift app calls |
| `iosApp` | The iOS app (Xcode project, Swift shell); see [iosApp/README.md](iosApp/README.md) |
| `build-logic` | Convention plugins (`spotter.android.feature`, `spotter.kmp.library`, …) |

The exercise library lives in `core/data/catalog/exercises.json` (an asset on Android, a bundle resource on iOS). Bump `EXERCISE_CATALOG_VERSION` in `ExerciseSeeder` whenever it changes so installed apps re-seed. Room is at schema version 8; every Room migration must re-create the sync triggers via `SyncSchema.TriggerSpec`.

## Supabase (optional backup + sync, milestone 8)

The app works fully offline; signing in (email + password, or an emailed link / 6-digit code) adds cloud backup and sync
between phones. Each synced row lives in `public.sync_rows` (one row per record, payload as
JSON, row-level security per user); SQLite triggers on the phone stamp changes and record
deletions, and sync is last-write-wins per record.

**Sign-in links use PKCE** (the 2026-10 audit, `docs/platform/audit-2026-10.md`). Every email
carries both a link and a code: the **link** only works on the phone that asked for it, because
the app keeps a secret (the PKCE verifier) that the link's one-time `code` is exchanged against;
the **code** works anywhere you can type it. `SupabaseProvider` sets `flowType = FlowType.PKCE`
and `SignInLinks.parse` accepts only `spotter://login?code=…`; a password-reset link is detected
from the token's `amr` claim (`recovery`), falling back to the old `type=recovery` query. The
session is kept in `EncryptedSharedPreferences` (`spotter_auth`). After a link sign-in into an
account with no data, the first sync asks before uploading the phone's data. Before this, the
link carried the session itself in the URL fragment and any app that opened a
`spotter://login#…` link imported it, so a crafted link could sign a phone into someone else's
account. Now that the website has a public domain, the next step is to make the link an https
App Link (`android:autoVerify` plus `/.well-known/assetlinks.json` on the site) so only this app
can claim it; see `docs/platform/infrastructure.md`.

**Units** (kg or lb) have one source of truth: the synced `user_profile.units` field. Changing
the setting in the app writes it there; the website reads the same field.

One-time setup in the Supabase dashboard:

1. **Database:** apply `supabase/migrations/` with `supabase db push` (linked CLI) or the Supabase
   MCP's `apply_migration`, never by pasting SQL into the editor, so the migration history on the
   server matches the repository. How to repair the history when it doesn't:
   `docs/platform/infrastructure.md`, "Database migrations".
2. **Authentication → URL Configuration:** Site URL `https://spotter.northstarsolutions.se`;
   Redirect URLs `spotter://login` (the emailed link opens the app there),
   `https://spotter.northstarsolutions.se/auth/callback` and `http://localhost:3000/auth/callback`.
3. **Authentication → Emails → SMTP Settings:** custom SMTP through Google Workspace, sending as
   `noreply@northstarsolutions.se` (an alias on the Workspace user). Supabase's built-in sender
   only reaches the organization's team members and sends 2 emails/hour. Details:
   `docs/platform/auth-emails.md`, "SMTP sender".
4. **Authentication → Emails → Templates:** every email carries a link and a code; the
   templates are in `docs/platform/auth-emails.md`.

Build config: add the project's **publishable** key to `local.properties` (untracked). Never
use the secret/service_role key in the app.

```properties
supabase.url=https://<project-ref>.supabase.co
supabase.publishableKey=sb_publishable_...
```

Dashboard: https://supabase.com/dashboard/project/gaherwthntdonrsuowbh

`.mcp.json` registers the Supabase MCP server for this project (schema, migrations, logs,
advisors, SQL). It is **write-capable**: anyone who opens this repository in Claude Code and
authenticates with a Supabase account that has access to the project can change the production
database through it. It stays in the repo because the audit and release steps use it; treat
project access like production access.

## Website

`web/` is the Next.js site that shows the same account's training and edits plans, live at
https://spotter.northstarsolutions.se; see
[web/README.md](web/README.md). Platform setup, environments and versioning rules:
[docs/platform/infrastructure.md](docs/platform/infrastructure.md). The 2026-10 audit and its
dashboard checklist: [docs/platform/audit-2026-10.md](docs/platform/audit-2026-10.md).
