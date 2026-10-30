---
name: release
description: Prepare a Spotter Android release for Google Play — pick the version, bump versionCode/versionName, write release notes, run every check, build and verify the signed bundle, record it in the Play README, and open the release PR. Also records an upload or go-live afterwards. Run only when the user asks for a release.
disable-model-invocation: true
argument-hint: "[patch|minor|major|x.y.z] or 'live' after the upload went through"
---

# Android release

The Play Console upload itself is the user's job (their account, their upload key). Everything
up to a verified bundle and after the upload is ours. `docs/play-store/README.md` holds the
Play Console steps and the **version table, which is the record of every build**.

## 1. Decide the version

1. `git fetch && git switch main && git pull`; the release is cut from an up-to-date `main`
   with a clean tree.
2. Read `versionCode` / `versionName` in `app/build.gradle.kts` and the version table.
3. List what changed for users since the last uploaded tag:
   `git log --oneline <last tag>..main -- app core feature` (tags: `android-vX.Y.Z` from
   1.0.1 on, `vX.Y.Z` before). Changes outside those paths (web, docs, supabase) don't ship
   in the app.
4. Version name (semantic): fixes only → patch (1.0.0 → 1.0.1); new features → minor;
   big changes or breaking data → major. Use the argument if the user gave one; otherwise
   propose and confirm.
5. `versionCode` = highest code in the table **+ 1**, even if a build was never uploaded.
   Play rejects any code it has ever seen, on any track.

## 2. Bump and write notes

On a branch `release/android-X.Y.Z`:

- `app/build.gradle.kts`: new `versionCode` and `versionName`.
- `CHANGELOG.md`: rename "Unreleased" to `## X.Y.Z (N) — <date>` (keep only what ships in the
  Android app; leave web-only or iOS-only lines under a fresh, empty "Unreleased") and write the
  release notes below from it.
- `docs/play-store/release-notes-X.Y.Z.txt`, in the existing format:
  ```
  <en-US>
  • One line per user-visible change, plain words, no internals
  </en-US>
  ```
  At most 500 characters inside the tags. Show the notes to the user before building.
- Add a row to the version table: code, name, one-line summary, "Not uploaded yet; tag
  `android-vX.Y.Z` when it is", the notes file.

## 3. Check and build

```bash
./gradlew testDebugUnitTest :core:data:testAndroidHostTest :core:engine:jvmTest :core:model:jvmTest verifyRoborazziDebug :app:lintRelease
./gradlew bundleRelease
```

(`JAVA_HOME` comes from `.claude/settings.json`.) Run them in the background if they may
take over two minutes. Report any failure with its output and stop. `bundleRelease` needs
`keystore.properties` and the Keychain password; if it's missing, stop and point the user
at section 1 of the Play README rather than working around it.

Verify the bundle at `app/build/outputs/bundle/release/app-release.aab`:

```bash
"$JAVA_HOME/bin/keytool" -printcert -jarfile app/build/outputs/bundle/release/app-release.aab | grep -m1 Owner
unzip -p app/build/outputs/bundle/release/app-release.aab base/manifest/AndroidManifest.xml | strings | grep -E '^[0-9]+\.[0-9]+\.[0-9]+'
```

The owner must be the Northstar upload certificate (not "Android Debug") and the version
name must be the new one.

## 4. Commit and hand over

- Commit `Release X.Y.Z (versionCode N)` with a short body (what changed, notes file),
  push the branch and open a PR. Merge once CI is green (the user's call).
- Tell the user the bundle path, the release notes to paste, and the Play steps: Testa och
  lansera → Produktion → Skapa ny version → upload → notes → Nästa → 100 % → Spara och
  publicera. The closed test (alpha) is paused; only production unless they say otherwise.

## 5. After the upload (`/release live`)

- Tag the release commit on `main`: `git tag android-vX.Y.Z <commit>`, push the tag (ask
  first).
- Update the version table: "Built from tag `android-vX.Y.Z`; live in production since
  <date>" once Google approves (or "in review since <date>" until then), and mark the
  previous production row as replaced. Commit on a branch, PR.
