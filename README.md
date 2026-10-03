# Spotter

Android-only, offline-first gym app (Kotlin, Jetpack Compose, Material 3). See [PLAN.md](PLAN.md).

## Build and run

There's no system JDK on this machine, so point Gradle at Android Studio's bundled one:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest :core:engine:test :core:model:test   # all unit tests
```

Or open the folder in Android Studio and run the `app` configuration.

## Layout

| Module | What's in it |
|---|---|
| `app` | Application, MainActivity, bottom-bar navigation shell |
| `core/model` | Pure Kotlin domain types and enums |
| `core/data` | Room schema (exported to `core/data/schemas`), DataStore preferences, exercise seeder |
| `core/engine` | Pure Kotlin plan generator: split choice, movement-pattern slots, goal parameters, starting weights (no Android deps, unit-tested) |
| `core/ui` | Theme (branded light/dark palette + dynamic color), shared components |
| `feature/*` | One module per tab (`today`, `history`, `progress`, `settings` = Profile), plus `session` (workout logger, rest timer service, summary), `library` (exercise picker), `onboarding` (questionnaire + plan summary) and `plan` (plan overview) |
| `build-logic` | Convention plugins (`spotter.android.feature`, `spotter.jvm.library`, …) |

The exercise library lives in `core/data/src/main/assets/exercises.json`. Bump its `version` after editing so installed apps re-seed.

## Supabase (optional backup + sync, milestone 8)

The app works fully offline; Supabase is only used for opt-in cloud backup. Add the project's
**publishable** key to `local.properties` (untracked). Never use the secret/service_role key in the app.

```properties
supabase.url=https://<project-ref>.supabase.co
supabase.publishableKey=sb_publishable_...
```

Dashboard: https://supabase.com/dashboard/project/gaherwthntdonrsuowbh
