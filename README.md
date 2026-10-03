# Spotter

Android-only, offline-first gym app (Kotlin, Jetpack Compose, Material 3). See [PLAN.md](PLAN.md).

## Build and run

There's no system JDK on this machine, so point Gradle at Android Studio's bundled one:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest :core:engine:test :core:model:test   # all unit tests
./gradlew verifyRoborazziDebug     # screenshot tests (recordRoborazziDebug updates the goldens)
./gradlew :app:lintRelease
```

Screenshot goldens (light, dark and large text) live in `*/src/test/screenshots`. Releasing
to the Play Store internal test track: see [docs/play-store/README.md](docs/play-store/README.md).

Or open the folder in Android Studio and run the `app` configuration.

## Layout

| Module | What's in it |
|---|---|
| `app` | Application, MainActivity, bottom-bar navigation shell |
| `core/model` | Pure Kotlin domain types and enums |
| `core/data` | Room schema (exported to `core/data/schemas`), DataStore preferences, exercise seeder |
| `core/engine` | Pure Kotlin training logic, no Android deps, unit-tested: plan generator, progression, swap ranking and weight conversion, PR detection, muscle balance, body-weight trend |
| `core/ui` | Theme (branded light/dark palette + dynamic color), shared components |
| `feature/*` | One module per tab (`today`, `history`, `progress`, `settings` = Profile), plus `session` (workout logger, rest timer service, summary), `library` (exercise picker), `onboarding` (questionnaire + plan summary) and `plan` (plan overview) |
| `build-logic` | Convention plugins (`spotter.android.feature`, `spotter.jvm.library`, …) |

The exercise library lives in `core/data/src/main/assets/exercises.json`. Bump its `version` after editing so installed apps re-seed.

## Supabase (optional backup + sync, milestone 8)

The app works fully offline; signing in (email + 6-digit code) adds cloud backup and sync
between phones. Each synced row lives in `public.sync_rows` (one row per record, payload as
JSON, row-level security per user); SQLite triggers on the phone stamp changes and record
deletions, and sync is last-write-wins per record.

One-time setup in the Supabase dashboard:

1. **SQL Editor:** run [supabase/001_sync.sql](supabase/001_sync.sql).
2. **Authentication → Emails → Templates:** in both **Confirm signup** and **Magic link**,
   add the code to the email body, e.g. `<p>Your Spotter code: <strong>{{ .Token }}</strong></p>`.

Build config: add the project's **publishable** key to `local.properties` (untracked). Never
use the secret/service_role key in the app.

```properties
supabase.url=https://<project-ref>.supabase.co
supabase.publishableKey=sb_publishable_...
```

Dashboard: https://supabase.com/dashboard/project/gaherwthntdonrsuowbh
