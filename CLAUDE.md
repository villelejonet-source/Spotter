# Spotter

Offline-first gym app. One repository holds every part:

| Path | What | Ships how |
|---|---|---|
| `app/`, `core/`, `feature/`, `build-logic/` | Android app (Kotlin, Compose), Gradle root | Signed bundle uploaded to Google Play by hand (`/release`) |
| `web/` | Website and dashboard (Next.js) | Vercel deploys production on every push to `main` that touches it |
| `supabase/` | Database migrations and SQL tests, shared by app and web | Applied to the one (production) Supabase project by hand |
| `iosApp/`, `shared/` | iOS app (Swift shell, Xcode) and the Kotlin framework it links | Not shipped yet (`docs/platform/ios-port.md`) |
| `docs/` | Play Store, platform and audit notes | — |

`README.md` has the module layout; `web/README.md` the website and Vercel setup;
`docs/play-store/README.md` the Play Console steps and the version table;
`docs/platform/infrastructure.md` hosting and accounts.

## Build and test

`JAVA_HOME` is set to Android Studio's bundled JDK in `.claude/settings.json` (there's no
system JDK). If Gradle says "Unable to locate a Java Runtime", export it:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

```bash
./gradlew testDebugUnitTest :core:data:testAndroidHostTest :core:engine:jvmTest :core:model:jvmTest   # unit tests
./gradlew :core:model:iosSimulatorArm64Test :core:engine:iosSimulatorArm64Test :core:data:iosSimulatorArm64Test   # same tests on iOS
./gradlew verifyRoborazziDebug     # screenshot tests; recordRoborazziDebug updates goldens
./gradlew :app:lintRelease
./gradlew :feature:library:testDebugUnitTest   # one module, for quick iteration
npm --prefix web run lint && npm --prefix web run typecheck && npm --prefix web test
```

The web dev server is the `web` entry in `.claude/launch.json` (port 3000). CI
(`.github/workflows/ci.yml`) runs all of the above on every pull request.

## Workflow

- **Never push to `main`.** Work on a branch and open a pull request; merge once CI is
  green. `main` is the website's production and the base for app releases.
- One task per branch/worktree. Commits end with the attribution line the harness gives.
- The repository is **public**: no secrets, keys, passwords or personal data in commits.
  Local-only material lives in `.private/` and `keystore.properties` (both git-ignored).

## Conventions that bite

- **Strings:** wording shared with the website changes in the app's `strings.xml`/`labels.xml`
  **and** `web/src/lib/labels.ts` together.
- **Exercise catalog** (`core/data/catalog/exercises.json`, also copied into the web
  build): bump `EXERCISE_CATALOG_VERSION` (`core/data/.../seed/SeedExercise.kt`) whenever
  it changes so installed apps re-seed.
- **Room:** schema is exported to `core/data/schemas`; every migration must re-create the
  sync triggers via `SyncSchema.TriggerSpec`. Add a `MigrationTest` case. Room runs on its
  driver API (`SQLiteConnection`, not `SupportSQLiteDatabase`) so the same code runs on iOS;
  use `db.withTransaction` from `core.data.db`, not `androidx.room`'s.
- **Supabase:** schema changes only as new files in `supabase/migrations/`, never by editing
  an applied one. There's no staging project, so applying a migration touches production:
  ask first.
- **Screenshot goldens** (light, dark, large text) live in `*/src/test/screenshots`. When a
  UI change is intended, re-record them and say so in the PR.
- **Shared code** (`core/model`, `core/engine`, `core/data`) is Kotlin Multiplatform and also compiles for iOS:
  no `java.*` there. Dates are `kotlinx.datetime.LocalDate`, times `kotlin.time.Instant`/`Duration`;
  tests use `kotlin.test` and their names can't contain `, . : ; / < > [ ]`. iOS plan:
  `docs/platform/ios-port.md`.
- **Compose text fields** take their value from synchronous state (`mutableStateOf` in the
  ViewModel), never from a flow that passes through async work: that drops keystrokes.
- **CI actions** are pinned to commit SHAs with the tag in a comment.

## Releases

Run `/release` (`.claude/skills/release/SKILL.md`). Play rejects any `versionCode` it has
seen, so it always goes up; the version table in `docs/play-store/README.md` is the record.
