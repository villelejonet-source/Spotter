# iOS port

Spotter goes to iOS with **Kotlin Multiplatform and Compose Multiplatform**: the same Kotlin
code runs on both phones, including the screens. iOS gets a thin Swift app around it plus the
few things that have to be native (rest-timer alerts, background sync, Keychain, links).

Why not SwiftUI: the app is ~15k lines of Kotlin, and the training engine, database, sync and
all nine screens would otherwise exist twice and drift. Compose Multiplatform keeps one UI;
the cost is a Material look on iOS rather than a native one, which suits a branded app.

The port lands as a series of pull requests; Android keeps working and shipping after each.

## Phases

| # | What | State |
|---|---|---|
| 1 | `core/model` and `core/engine` multiplatform (JVM + iOS). `java.time` → `kotlinx.datetime` / `kotlin.time` everywhere; tests on `kotlin.test`, run on JVM and the iOS simulator in CI | Done |
| 2 | `core/data` multiplatform: Room KMP (connection API; `AndroidSQLiteDriver` on Android so the existing database file is untouched, `BundledSQLiteDriver` on iOS), DataStore KMP, supabase-kt with the Darwin engine, one plain-Kotlin object graph that Hilt wraps on Android | Done |
| 3 | `iosApp/` Xcode project around a shared framework; builds and runs in the simulator; CI builds it | Done: seeds the catalog, opens `spotter://` links, syncs while open |
| 4 | `core/ui` and `feature/*` on Compose Multiplatform: `R.string`/`plurals` → Compose resources, Hilt → Koin (or the phase 2 graph), Navigation Compose and lifecycle ViewModels (both multiplatform), charts (Vico has a multiplatform build) | 4a done: `core/ui`; 4b done: Koin; 4c: today, history, progress |
| 5 | iOS-only pieces, below | |
| 6 | App Store release, below | |

### Phase 2 details

- **Database.** DAOs, entities, `SyncSchema` and migrations move to `commonMain`.
  `SupportSQLiteDatabase` code (`SyncSchema`, `LocalSyncStore`'s cursors and `ContentValues`)
  is rewritten on `SQLiteConnection` / `SQLiteStatement`; `withTransaction` becomes
  `useWriterConnection { immediateTransaction { … } }`. `MigrationTest`, `SyncTriggersTest`
  and `HostilePayloadTest` must pass unchanged in behaviour; they stay Robolectric tests on
  the Android target.
- **Exercise catalog.** `core/data/catalog/exercises.json`, read through `CatalogSource`: an
  Android asset (`core/data-android` adds the folder), an iOS bundle resource. One file; the
  website copies it too.
- **Auth session.** Android keeps `EncryptedSharedPreferences` (`AuthStorage`); iOS uses the
  Keychain (`KeychainSettings` from multiplatform-settings). Both are `Settings` behind `SessionStore`, which is what supabase-kt's session manager
  takes. iOS items are `AfterFirstUnlockThisDeviceOnly`.
- **Device id** stays per install: SharedPreferences on Android, `NSUserDefaults` on iOS.
  Decide whether an iCloud-restored iPhone should keep the old id (Android deliberately
  doesn't, see `data_extraction_rules.xml`).
- **Sign-in links.** `SignInLinks.parse` takes a URL string; Android passes `intent.data`, iOS
  the URL from `onOpenURL`. Same `spotter://login?code=…` PKCE flow.
- **Background sync.** Android keeps WorkManager. iOS syncs on launch and on foreground first;
  `BGAppRefreshTask` comes in phase 5.

### Phase 4 details

- **Modules.** `spotter.kmp.compose` makes a Compose Multiplatform module (Android + iOS):
  code in `commonMain`, strings and plurals in `src/commonMain/composeResources`, read with
  `org.jetbrains.compose.resources.stringResource`; the public `Res` class is in
  `<namespace>.resources`. On Android the Compose Multiplatform artifacts resolve to the androidx
  ones from the Compose BOM, so keep `composeMultiplatform` on the BOM's Compose release.
- **Locale formatting** goes through `core/ui`'s `PlatformFormat`: Android keeps
  `String.format`, `java.time` with `DateFormat.getBestDateTimePattern`, `NumberFormat` and ICU's
  `ListFormatter`; iOS uses `NSDateFormatter.dateFormatFromTemplate`, `NSNumberFormatter` and
  `NSListFormatter`. Shared code uses `localFormat` instead of `String.format`.
- **Compose resources differ from Android's:** `\'` isn't unescaped (write `'`), and there's no
  `formatted="false"`. Positional `%1$s` / `%1$d` work as before.
- **Screenshot tests** of a multiplatform module are Android host tests
  (`src/androidHostTest`); the goldens stay in `src/test/screenshots`, and the module also answers
  to `testDebugUnitTest` / `verifyRoborazziDebug` / `recordRoborazziDebug`.
- **Dependency injection: Koin** (Hilt is gone). `core/data`'s `dataModule(graph)` hands the
  platform's `DataGraph` objects to Koin; Android starts Koin in `SpotterApplication` with
  `appModules` (data, the app shell's view models, one module per feature), iOS in `SpotterIos`.
  View models are `viewModelOf(::X)` (SavedStateHandle comes from Koin) and screens take
  `koinViewModel()`; the WorkManager worker is `workerOf(::SyncWorker)` with Koin's worker factory.
  `AppModulesTest` (app) fails when a view model or worker asks for something nobody provides.
- **Lifecycle and navigation** are the JetBrains multiplatform artifacts in feature modules; on
  Android they resolve to the same androidx versions as before.
- **Feature modules** move to `spotter.kmp.feature` (shared Compose, Koin, navigation, lifecycle,
  wired to `core:data` rather than `core:data-android`): code in `commonMain`, strings in
  `composeResources`, screenshot tests as Android host tests. Resource ids passed around as `Int`
  become `StringResource`. Locale calendar facts (first day of the week, narrow weekday names)
  come from `core/ui`'s `PlatformFormat`. `:shared` links each ported feature, so the iOS build
  proves it links before 4d shows it.
- **Charts.** Vico 3.3.1's `compose` and `compose-m3` artifacts are multiplatform (iOS arm64 and
  simulator), and `TrendChart` uses only their common API, so it moved as is.

### Phase 5: iOS-only pieces

| Android today | iOS |
|---|---|
| Rest-timer foreground service + notification (`WorkoutService`) | Local notification scheduled for the rest's end; a Live Activity (lock screen / Dynamic Island) for the running workout |
| Vibration on rest end | `UINotificationFeedbackGenerator` / notification sound |
| WorkManager periodic sync | `BGAppRefreshTask` |
| `spotter://login` intent filter, App Links later | URL type `spotter`; Universal Links via `/.well-known/apple-app-site-association` on the website |
| Dynamic color (Material You) | Off; branded palette only |
| Per-app language, `data_extraction_rules.xml` | `Localizable.xcstrings` for the app name; Keychain items are `ThisDeviceOnly` |

### Phase 6: App Store

- Apple Developer Program membership (paid, yearly), a bundle ID (`com.viktorolsson.spotter`,
  as on Android), signing in Xcode, an App Store Connect record.
- App Privacy answers (email for the optional account; training data synced to Supabase),
  matching the website's `/privacy` page and the Play data-safety form.
- Supabase **Redirect URLs**: no change for `spotter://login`; add the Universal Link URL
  when that lands.
- TestFlight first; the reviewer account from `.private/play-review-account.md` can be reused.
- Version numbers: `CFBundleShortVersionString` follows the Android `versionName`; the build
  number is iOS's own counter. Record both in a table like the one in
  `docs/play-store/README.md`.

## Rules for shared code

- No `java.*` / `android.*` in `commonMain`. Dates are `kotlinx.datetime.LocalDate`, instants
  and durations `kotlin.time`. Android UI that still formats with `java.time` converts at the
  edge (`toJavaLocalDate()`, `toJavaInstant()`).
- Tests in `commonTest` use `kotlin.test` and must pass on both targets:
  `./gradlew :core:model:allTests :core:engine:allTests`. Kotlin/Native rejects
  `, . : ; / < > [ ]` in test names.

## Known follow-ups

- The data layer's "today" (deload week, scheduled sessions, onboarding age) is computed in
  **UTC**, as it was with `Clock.systemUTC()`, while training dates use the phone's time zone.
  Phase 1 keeps that exactly; between midnight and 01:00/02:00 in Sweden "today" is still
  yesterday. Worth fixing on its own, with tests around midnight.
