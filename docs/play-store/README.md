# Releasing Spotter on Google Play

The Play Console steps below need your Google Play Console account and your upload key, so
they're for you to run.

The developer account is an **organization** account (Northstar solutions AB), so releases
can go straight to production. The 12-testers-for-14-days closed test rule only applies to
personal accounts.

## Versions

| versionCode | versionName | Status |
|---|---|---|
| 2 | 0.9.1 | Internal test |
| 3 | 0.10.0 | First production release, live 2026-10-25 (Sweden only), replaced by 0.11.0 the same day. Also on the closed test (alpha), which is paused. Lacks timed holds from 0.9.1. |
| 4 | 0.10.1 | Built from tag `v0.10.1`; never uploaded (superseded by 0.11.0). |
| 5 | 0.11.0 | Audit release (`docs/platform/audit-2026-10.md`): PKCE sign-in links, sync and database hardening, one design system on app and web. Built from tag `v0.11.0`; live in production 2026-10-25 (submission 6, full rollout, published 17:07), replaced by 1.0.0 the same day. Release notes: `release-notes-0.11.0.txt`. |
| 6 | 1.0.0 | First 1.x release, marking the production launch. Fixes typing in the exercise picker's search (letters dropped, cursor jumping). Built from tag `v1.0.0`; live in production since 2026-10-25 (full rollout). Release notes: `release-notes-1.0.0.txt`. |

Tag each uploaded build so it can be rebuilt exactly: `git tag android-v1.0.1 <commit>` from
1.0.1 on (room for `ios-v…` tags later); earlier builds use `vX.Y.Z`. The `/release` Claude
Code skill (`.claude/skills/release/`) walks through bumping, notes, checks and the bundle.

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
Bump `versionCode` in `app/build.gradle.kts` for every upload: Play rejects a code it has
seen before, on any track. Then add the build to the table above.

## 3. Play Console

1. **Create app**: name "Spotter", app, free.
2. **Release** (Testa och lansera): pick a track, then **Skapa ny version** (create new
   release), upload the `.aab` (or **Lägg till från bibliotek** for a build already
   uploaded to another track), add release notes, then **Nästa → Spara och publicera**.
   - **Produktion**: set the countries on **Länder/regioner** first (currently Sweden).
   - **Stängt test → alpha** and **Internt test**: testers come from an email list and must
     open the opt-in link before Play offers them the app.
   - Managed publishing is off, so an approved release goes live by itself. Withdrawing a
     release in review (**Ta bort ändringar**) puts the next one at the back of the queue.
3. **App content**, everything Play asks before the first release:
   - **Privacy policy**: https://spotter.northstarsolutions.se/privacy. The website renders
     `privacy-policy.md` at build time, so a change to the file goes live with the next deploy
     of `main`; edit only the file. Set in Play Console (App content → Privacy policy and Data
     safety → account deletion) on 2026-10-25, replacing the old Google Site
     (https://sites.google.com/view/spotter-app-gym/startsida, on the personal Gmail account),
     which can now be deleted.
   - **Data safety**: see below.
   - **Data deletion**: the same URL (section "Deleting your data" describes in-app and email deletion).
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
counting down with the screen off is usually requested; record it on the emulator. The
current recording is `docs/play-store/foreground-service-demo.mp4` on your machine only:
`docs/play-store/*.mp4` is gitignored (3.9 MB binaries don't belong in the repository) and
the file is uploaded to Play Console by hand under the foreground-service declaration.
Keep a copy with the upload key.

### Data safety answers

- **Does the app collect or share user data?** Yes, collected (only with the optional cloud
  backup). **Shared:** No.
- **Data types collected** (everything the backup uploads, see `SyncSchema.tables`):
  - Personal info → *Email address*, *User IDs* (the account id), *Other info* (sex and
    date of birth from the profile)
  - Health and fitness → *Health info* (injuries/limitations to work around, body weight)
    and *Fitness info* (plans, workouts, sets, records)
  - Device or other IDs → *Device or other IDs* (the random per-install id the app sends on
    sync, from versionCode 7; see `DeviceIdentity`)
  - For each: **optional** (backup is opt-in), purpose **App functionality** (plus
    **Account management** for email and user ID), not processed ephemerally. From
    versionCode 7 also **Analytics** for *User IDs*, *Fitness info* and *Device or other IDs*:
    the owner's usage overview (`/admin`) counts accounts, workouts per week and app versions.
- **Encrypted in transit:** Yes (HTTPS). **Users can request deletion:** Yes, in the app
  (Profile → Backup & sync → Delete account and backup) and by email.
- No analytics or tracking services, no ads, no data sold or shared (the usage overview reads
  the backup database directly). Training data isn't in Google cloud backup
  (`data_extraction_rules.xml`).

### Account deletion (required because the app has accounts)

- **In the app:** Profile → Backup & sync → "Delete account and backup".
- **Web link** for the Play form: https://spotter.northstarsolutions.se/privacy (section "Deleting your data").

## Screenshots

Phone screenshots (at least 2, 16:9 or 9:16, 1080×1920 or more) are needed. The Roborazzi
goldens under `feature/*/src/test/screenshots` show the screens, but take store screenshots
on a device or emulator with real-looking data: Today, an active workout with the keypad,
exercise history with a chart, the Progress tab with a suggestion, and the plan summary.
