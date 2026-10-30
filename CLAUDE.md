# Spotter

Offline-first gym app for Android and iOS, plus a website. Claude Code writes the code; these
rules are how it keeps the repository safe. One repository holds every part:

| Path | What | Ships how |
|---|---|---|
| `app/`, `core/`, `feature/`, `shell/`, `build-logic/` | Android app (Kotlin, Compose) and the shared Kotlin, Gradle root | Signed bundle uploaded to Google Play by hand (`/release`) |
| `iosApp/`, `shared/` | iOS app (Swift shell, Xcode) and the Kotlin framework it links | Not shipped yet (`docs/platform/ios-port.md`) |
| `web/` | Website and dashboard (Next.js) | Vercel deploys production on every push to `main` that touches it |
| `supabase/` | Database migrations and SQL tests, shared by app and web | Applied to the one (production) Supabase project by hand, after asking |
| `docs/` | Play Store, platform and audit notes | — |

`README.md` has the module layout; `web/README.md` the website and Vercel setup;
`docs/play-store/README.md` the Play Console steps and the version table;
`docs/platform/infrastructure.md` hosting, accounts and the database workflow.

## Architecture: where things live

Every feature is built **once, in shared Kotlin, for both phones**. The layers, top down:

| Layer | Where | What goes there |
|---|---|---|
| Screens | `feature/*`, `shell` (`commonMain`) | Compose UI and view models, shared by Android and iOS. UI components are shared by default; reusable ones go in `core/ui`. |
| Data | `core/data` (`commonMain`) | Room schema and DAOs, repositories, preferences, Supabase sync and accounts, `DataGraph` (builds them all), `AppErrors` |
| Domain | `core/model`, `core/engine` | Types and rules with no I/O: `NumberInput` (every typed number), `Lifting.kt` (1RM, volume, best set), plan generation, progression, PRs |
| Platform | `androidMain` / `iosMain` source sets; `app/`, `iosApp/`, `shared/` | Only what truly differs per platform |

**Platform rules:**
- No platform checks in shared code: no `isIos`, no `Platform.OS`-style branching. When
  behaviour must differ, put it behind `expect`/`actual` or an interface the platform provides
  through `DataGraph`/Koin (`SyncScheduler`, `SessionStore`, `RestTimerRepository.runOut`'s
  callbacks), with the platform part in `androidMain`/`iosMain`.
- Platform code needs a stated reason in a comment: what the platform does differently. Today
  that's the rest-timer service and notifications, WorkManager, the encrypted session store /
  Keychain, keep-screen-on, dynamic color, locale formatting and opening the email app.
- Both apps start the same way: `spotterModules()` for Koin and `DataGraph.onAppStart()`.
  Android's `MainActivity` and iOS both host `SpotterRoot`.
- Rules the website repeats (1RM, volume, best set, typed decimals, timed exercises) are pinned
  by `core/model/fixtures/training-math.json`, which the Kotlin and web tests both check.
  Change the fixture first, then make both sides pass.
- No `java.*` or `android.*` in `commonMain`. Dates are `kotlinx.datetime.LocalDate`, times
  `kotlin.time.Instant`/`Duration`; tests use `kotlin.test`, and their names can't contain
  `, . : ; / < > [ ]`.

## Workflow for every task

1. **Read** the relevant code (and this file's rules for that area) before proposing anything.
2. **Propose a plan** and wait for the user's OK when the change is more than a small, obvious fix.
3. **Implement on a branch**, never on `main`: `git switch -c <type>/<short-name>`.
4. **Add or update tests** for the behaviour that changed.
5. **Run the checks** (next section). All must pass; warnings count as failures.
6. **Update `CHANGELOG.md`** under "Unreleased" for anything a user or the website would notice.
7. **Open a pull request** and **summarise**: what changed, what was checked, and a short list
   of what the user should test by hand on a phone.
8. **One pull request at a time.** Merge only when CI is green and the user has said so; don't
   start the next one before the current one is merged. A stacked branch is the exception, and
   needs saying.

**Bug fixes start with a failing test** that reproduces the bug, run before the fix to show it
fails for the right reason, then the fix that makes it pass. Say in the PR that it failed first.

**Never, without asking first:**
- change the database schema except through a new migration file;
- apply a migration to production (there's no staging project);
- delete or overwrite user data (on the server, in a migration, or with a destructive sync change);
- push to `main`, force-push, or bypass branch protection.

## Build and test

`JAVA_HOME` is set to Android Studio's bundled JDK in `.claude/settings.json` (there's no
system JDK). If Gradle says "Unable to locate a Java Runtime", export it:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

```bash
./gradlew testDebugUnitTest :core:data:testAndroidHostTest :core:engine:jvmTest :core:model:jvmTest   # unit tests
./gradlew verifyRoborazziDebug     # screenshot tests; recordRoborazziDebug updates goldens
./gradlew :app:lintRelease         # Android Lint for the app and every module
./gradlew :core:model:iosSimulatorArm64Test :core:engine:iosSimulatorArm64Test :core:data:iosSimulatorArm64Test :core:ui:iosSimulatorArm64Test   # shared tests on iOS
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # the iOS framework links
./gradlew :feature:library:testDebugUnitTest   # one module, for quick iteration
npm --prefix web run lint && npm --prefix web run typecheck && npm --prefix web test
```

Database checks need the local Supabase stack (Docker, OrbStack on this Mac); it never touches
production:

```bash
supabase db start   # a fresh database from every migration
for f in supabase/tests/*.sql; do docker exec -i supabase_db_spotter psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -t < "$f"; done
npm --prefix web run db:types   # after a schema change: regenerate the website's types, commit them
supabase stop
```

CI (`.github/workflows/ci.yml`) runs all of this on every pull request: Website, Android checks,
Shared code on iOS (tests plus the Xcode build) and Database. The required `App` check passes
only when every job did. The web dev server is the `web` entry in `.claude/launch.json` (port 3000).

**Strictness:**
- Kotlin warnings are errors in every module (root `build.gradle.kts`).
- Android Lint warnings are errors.
- ESLint runs with `--max-warnings=0`, and TypeScript is `strict`.

Fix the cause. If that's impossible, suppress the single spot with a comment saying why
(example: `BackHandler` in `OnboardingScreen`).

## Errors: nothing fails silently

- Every view model action runs through `launchReporting(errors)` (`core/data` `AppErrors.kt`),
  not a bare `viewModelScope.launch`. A failure is logged and shown as a snackbar ("Couldn't
  save. Try again.") instead of crashing the app or vanishing. Pick `Failure.SAVE` for changes,
  `Failure.ACTION` for the rest.
- Screens that need a specific message (account forms, sign-out) handle their own errors and
  must show something. Background work goes through `DataGraph.scope`, which reports anything
  uncaught.
- Log class names and table names only, never payloads, tokens or email addresses.
- Crash reporting (Sentry) is on hold; don't add it unasked.

## Database (Supabase)

- Schema changes only as new files in `supabase/migrations/`, never by editing an applied one
  and never in the dashboard. Expand, then contract: older app versions keep reading what they
  read (`docs/platform/infrastructure.md`, "Versioning").
- Every table has row-level security, every view is `security_invoker`, every `SECURITY
  DEFINER` function pins `search_path`, and `anon` gets nothing. `supabase/tests/rls.sql`
  checks all of it in CI.
- After a schema change, regenerate `web/src/lib/supabase/database.types.ts`. CI fails when
  it's stale, and `schema-check.ts` fails the type check when a column the website reads changed.

## Conventions that bite

- **Strings:** wording shared with the website changes in the app's strings and
  `web/src/lib/labels.ts` together. Shared modules' strings are Compose resources
  (`src/commonMain/composeResources/values/`): write `'` unescaped there, since `\'` shows the
  backslash.
- **Typed numbers** go through `NumberInput` (`core/model`): a decimal comma or point ("62,5"),
  blank clears the field, invalid text leaves the stored value alone.
- **Set edits** write one field (`setSetWeight`/`Reps`/`Rir`), never values copied from the
  screen's state, which can be behind the database.
- **Exercise catalog** (`core/data/catalog/exercises.json`, also copied into the web build):
  bump `EXERCISE_CATALOG_VERSION` (`core/data/.../seed/SeedExercise.kt`) whenever it changes,
  so installed apps re-seed.
- **Room:**
  - The schema is exported to `core/data/schemas`.
  - Every migration must re-create the sync triggers via `SyncSchema.TriggerSpec`, and gets a
    `MigrationTest` case.
  - Room runs on its driver API (`SQLiteConnection`) so the same code runs on iOS. Use
    `db.withTransaction` from `core.data.db`, and do read-then-write in one transaction.
- **Screenshot goldens** (light, dark, large text) live in `*/src/test/screenshots`. When a UI
  change is intended, re-record them and say so in the PR.
- **Compose text fields** take their value from synchronous state (`mutableStateOf` in the
  view model), never from a flow that passes through async work: that drops keystrokes.
- **CI actions** are pinned to commit SHAs with the tag in a comment.
- The repository is **public**: no secrets, keys, passwords or personal data in commits.
  Local-only material lives in `.private/`, `local.properties` and `keystore.properties` (all
  git-ignored).

## Releases

Run `/release` (`.claude/commands/release.md`); new work starts with `/feature` or `/bugfix`. Play rejects any `versionCode` it has
seen, so it always goes up; the version table in `docs/play-store/README.md` is the record.
`CHANGELOG.md`'s "Unreleased" section becomes the new version's entry and the source of its
store notes. iOS versions live in `iosApp/Config/App.xcconfig` (`MARKETING_VERSION` follows
Android's `versionName`).
