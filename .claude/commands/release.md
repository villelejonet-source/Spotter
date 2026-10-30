---
description: Prepare a Spotter release — pick the version, bump Android and iOS, turn the changelog into notes, run every check, build and verify the signed Android bundle, open the release PR, and hand over the production upload steps and a smoke-test checklist. Also records an upload or go-live afterwards. Run only when the user asks for a release.
disable-model-invocation: true
argument-hint: "[patch|minor|major|x.y.z] or 'live' after the upload went through"
---

# Release

Uploading is the user's job: their Play account, their upload key, and later their Apple
account. Everything up to a verified bundle, and the bookkeeping after the upload, is ours.
`docs/play-store/README.md` holds the Play Console steps and the **version table, which is
the record of every build**. `CHANGELOG.md` is where the notes come from.

Arguments: $ARGUMENTS

## 1. Decide the version

1. `git fetch && git switch main && git pull`: the release is cut from an up-to-date `main`,
   with a clean tree and green CI on its last commit (`gh run list --branch main --limit 1`).
2. Read `versionCode` / `versionName` in `app/build.gradle.kts`, the version table, and
   `CHANGELOG.md`'s "Unreleased" section.
3. Check what changed for users since the last uploaded tag:
   `git log --oneline <last tag>..main -- app core feature shell` (tags: `android-vX.Y.Z` from
   1.0.1 on, `vX.Y.Z` before). Changes outside those paths (web, docs, supabase) don't ship
   in the app. Anything user-visible missing from "Unreleased" gets added now.
4. Version name (semantic): fixes only → patch; new features → minor; big changes or breaking
   data → major. Use the argument if the user gave one; otherwise propose and confirm.
5. `versionCode` = highest code in the table **+ 1**, even if a build was never uploaded.
   Play rejects any code it has ever seen, on any track.
6. **The server must be ready first.** Every migration the app relies on must already be in
   production. Check read-only with `supabase migration list --project-ref gaherwthntdonrsuowbh`
   (local and remote columns must match). A missing one stops the release. Applying it is a
   separate step that needs the user's OK.

## 2. Bump and write notes

On a branch `release/X.Y.Z`:

- `app/build.gradle.kts`: the new `versionCode` and `versionName`.
- `iosApp/Config/App.xcconfig`: `MARKETING_VERSION` = the new `versionName`. Bump
  `CURRENT_PROJECT_VERSION` (iOS's own build counter) only when an iOS build is uploaded.
- `CHANGELOG.md`:
  - Rename "Unreleased" to `## X.Y.Z (N) — <date>`, keeping only lines that ship in the
    Android app.
  - Lines for iOS or the website alone go under a fresh "Unreleased".
  - Start an empty "Unreleased" at the top.
- `docs/play-store/release-notes-X.Y.Z.txt`, from that changelog entry, in the existing format:
  ```
  <en-US>
  • One line per user-visible change, plain words, no internals
  </en-US>
  ```
  At most 500 characters inside the tags. Show the notes to the user before building.
- Add a row to the version table: code, name, one-line summary, "Not uploaded yet; tag
  `android-vX.Y.Z` when it is", the notes file.

## 3. Check and build

`JAVA_HOME` comes from `.claude/settings.json`. Run everything in the background; it takes
several minutes.

```bash
./gradlew testDebugUnitTest :core:data:testAndroidHostTest :core:engine:jvmTest :core:model:jvmTest verifyRoborazziDebug :app:lintRelease
./gradlew :core:model:iosSimulatorArm64Test :core:engine:iosSimulatorArm64Test :core:data:iosSimulatorArm64Test :core:ui:iosSimulatorArm64Test
npm --prefix web run lint && npm --prefix web run typecheck && npm --prefix web test
./gradlew bundleRelease
```

Report any failure with its output and stop.

Two files must be in place first; neither is in git, and **never read `keystore.properties`**:
- `local.properties` needs `supabase.url` and `supabase.publishableKey`. Without them the
  release has cloud sync off. Check with `grep -c '^supabase\.' local.properties` (expect 2).
- `bundleRelease` needs `keystore.properties` and the Keychain password. If it says either
  is missing, stop and point the user at section 1 of the Play README rather than working
  around it.

Verify the bundle at `app/build/outputs/bundle/release/app-release.aab`:

```bash
"$JAVA_HOME/bin/keytool" -printcert -jarfile app/build/outputs/bundle/release/app-release.aab | grep -m1 Owner
unzip -p app/build/outputs/bundle/release/app-release.aab base/manifest/AndroidManifest.xml | strings | grep -E '^[0-9]+\.[0-9]+\.[0-9]+'
```

The owner must be the Northstar upload certificate (not "Android Debug"), and the version
name must be the new one.

## 4. Commit and hand over

- Commit `Release X.Y.Z (versionCode N)` with a short body (what changed, the notes file),
  push the branch and open a PR. It merges once CI is green and the user says so.
- Tell the user:
  - **Bundle:** the path above.
  - **Release notes:** to paste.
  - **Upload straight to production** (the user doesn't use the testing tracks): Testa och
    lansera → Produktion → Skapa ny version → upload → notes → Nästa → 100 % → Spara och
    publicera.
- Give the **smoke-test checklist**, to run on a real phone as soon as Play updates it. A
  failure means a patch release (`/release patch`) right away:
  1. **Log a workout:** start the next planned workout, fill in and tick three sets, check
     the rest timer runs (with the screen off too) and alerts, then finish. The summary and
     History show it.
  2. **Edit reps and weight:** type a weight with a comma ("62,5"), change reps, then tick
     straight away. Both values stick, and the next set is filled in. Edit a set in a
     finished workout.
  3. **Bodyweight sets:** log pull-ups with no weight, then with added weight. History shows
     reps for the first and the weight for the second, and no 1RM for bodyweight only.
  4. **Offline, then online:** with flight mode on, log a set and change a setting; no error
     and nothing lost. Turn the network back on and open the app: Profile → Backup shows a
     fresh sync, and the website shows the workout.
  5. **Anything this release changed:** one line per changelog entry.
- **iOS:** there's no Apple Developer account or signing yet, so no TestFlight. When there
  is, this step builds an archive with `xcodebuild archive` and uploads it to TestFlight;
  until then the iOS version is only bumped to stay in step.

## 5. After the upload (`/release live`)

- Tag the release commit on `main`: `git tag android-vX.Y.Z <commit>`, then push the tag
  (ask first).
- Update the version table: "in review since <date>", then "live in production since <date>"
  once Google approves. Mark the previous
  production row as replaced. Commit on a branch, PR.

## No over-the-air updates

Spotter is native code (Kotlin, compiled), not JavaScript, so there's nothing like EAS Update:
every change, even a one-line fix, ships as a store release. The website is the exception: it
deploys on every merge to `main`. A fix needed fast goes out as a patch release straight to
production; Play usually reviews within hours.
