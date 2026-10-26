# Spotter for iOS

The iOS app: a thin Swift shell around the shared app (`:shared` builds the `Shared` framework
from `:shell`, the features and `core/*`). Every screen is the shared Compose UI. The plan and what's still to come: `docs/platform/ios-port.md`.

## Run it

Needs Xcode and a JDK for Gradle (Android Studio's is used when `JAVA_HOME` isn't set).

```bash
open iosApp/Spotter.xcodeproj
```

Pick an iPhone simulator and run. The **Compile Kotlin framework** build phase runs
`./gradlew :shared:embedAndSignAppleFrameworkForXcode` first, so the first build takes a while.
From the command line:

```bash
xcodebuild -project iosApp/Spotter.xcodeproj -scheme Spotter -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO build
```

## Cloud sync

Without configuration the app builds and works offline. For sign-in and sync, copy
`Config/Secrets.xcconfig.example` to `Config/Secrets.xcconfig` (git-ignored) and fill in the
project URL and **publishable** key, as `local.properties` does for Android.

## Layout

| Path | What |
|---|---|
| `Spotter/SpotterApp.swift` | App entry; starts `SpotterIos` (in `shared/`), shows its Compose UI full-window, passes opened URLs and foreground events |
| `Spotter/Info.plist` | The `spotter://` URL scheme for sign-in links, Supabase keys from the xcconfig |
| `Config/App.xcconfig` | Build settings; includes the optional `Secrets.xcconfig` |
| `../core/data/catalog/exercises.json` | Copied into the app bundle; seeded on first launch |

Signing for a device or TestFlight needs an Apple Developer account (phase 6 of the plan).
