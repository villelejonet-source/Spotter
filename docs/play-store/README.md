# Releasing Spotter to the Play Store internal test track

Everything in the repo is ready for an internal test release. The steps below need your
Google Play Console account and your upload key, so they're for you to run.

## 1. Create the upload key (once)

Run this yourself and keep the file and passwords somewhere safe (a password manager).
Losing the upload key can be recovered through Play Console support, but it's slow.

```bash
keytool -genkeypair -v -keystore ~/keys/spotter-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Save the password in the macOS Keychain (it prompts; nothing is echoed):

```bash
security add-generic-password -a "$USER" -s spotter-upload-key -T /usr/bin/security -w
```

Then create `keystore.properties` in the repo root (gitignored). No password in it: the build
reads it from the Keychain item above.

```properties
storeFile=/Users/<you>/keys/spotter-upload.jks
keyAlias=upload
```

(Putting `storePassword=` / `keyPassword=` in the file also works, e.g. on a machine without a Keychain.)

Use **Play App Signing** (the default for new apps): Google holds the app signing key, and
this upload key only proves uploads come from you.

## 2. Build the bundle

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest :core:engine:test :core:model:test verifyRoborazziDebug :app:lintRelease
./gradlew bundleRelease
```

The bundle is `app/build/outputs/bundle/release/app-release.aab`. `bundleRelease` refuses to
run without `keystore.properties`, so a debug-signed bundle can't be uploaded by mistake.
Bump `versionCode` in `app/build.gradle.kts` for every upload.

## 3. Play Console

1. **Create app**: name "Spotter", app, free.
2. **Internal testing → Create release**: upload the `.aab`, add release notes, and add
   testers (an email list).
3. **App content**, everything Play asks before the first release:
   - **Privacy policy**: host `privacy-policy.md` somewhere public (e.g. GitHub Pages) and paste the URL.
   - **Data safety**: see below.
   - **Data deletion**: point to the privacy policy (it describes in-app and email deletion).
   - **Foreground service permissions**: declare `FOREGROUND_SERVICE_SPECIAL_USE` with the text below.
   - **Health apps declaration**: Spotter is a fitness app. It doesn't use Health Connect or sensors.
   - **Content rating**: questionnaire, category "Health & fitness"; no user-generated content.
   - **Target audience**: 18+ (strength training guidance).
   - **Ads**: no ads.
4. **Store listing**: copy from `listing.md`; screenshots below.

### Foreground service declaration (special use)

> Spotter shows a workout stopwatch and a rest-period countdown in an ongoing notification
> while the user is logging a gym session. The countdown must keep running and alert the
> user (sound/vibration) when rest ends, including when the screen is off or another app is
> open. The service starts only when the user starts a workout and stops when the workout is
> finished or discarded. Notification actions let the user add 30 seconds or skip the rest.
> Exact alarms aren't suitable because the user adjusts the rest time continuously, and the
> elapsed workout time is shown live.

A short screen recording of starting a workout, ticking a set and the rest notification
counting down with the screen off is usually requested; record it on the emulator.

### Data safety answers

- **Does the app collect or share user data?** Yes, collected (only with the optional cloud
  backup). **Shared:** No.
- **Data types collected:** Personal info → *Email address*; Health and fitness → *Fitness
  info* (workouts, exercise data, body weight); for each: **optional** (backup is opt-in),
  purpose **App functionality** and **Account management**, not processed ephemerally.
- **Encrypted in transit:** Yes (HTTPS). **Users can request deletion:** Yes, in the app
  (Profile → Backup & sync → Delete account and backup) and by email.
- No analytics, no ads, no data sold or shared. Training data isn't in Google cloud backup
  (`data_extraction_rules.xml`).

### Account deletion (required because the app has accounts)

- **In the app:** Profile → Backup & sync → "Delete account and backup".
- **Web link** for the Play form: the privacy policy URL, section "Deleting your data".

## Screenshots

Phone screenshots (at least 2, 16:9 or 9:16, 1080×1920 or more) are needed. The Roborazzi
goldens under `feature/*/src/test/screenshots` show the screens, but take store screenshots
on a device or emulator with real-looking data: Today, an active workout with the keypad,
exercise history with a chart, the Progress tab with a suggestion, and the plan summary.
