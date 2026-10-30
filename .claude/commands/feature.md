---
description: Build a new feature the Spotter way — read, plan and wait for OK, then branch, build it once in shared code for both phones, test, check, changelog, PR with a hand-test list.
argument-hint: "<what the feature should do>"
---

# Feature

The feature: $ARGUMENTS

Follow CLAUDE.md. In order:

1. **Read first.** Find where this belongs (CLAUDE.md, "Architecture") and read that code: the
   screen in `feature/*`, its view model, the repository in `core/data`, any rules in
   `core/model`/`core/engine`. If the website shows the same thing, read `web/src` too.
2. **Propose a plan and wait for the user's OK.** Cover:
   - what changes, layer by layer;
   - whether the database schema changes (Room migration, Supabase migration, synced
     columns: see "Database" and `docs/platform/infrastructure.md`, "Versioning");
   - whether the website needs the same change;
   - what you'll test;
   - anything platform-specific, with the reason it can't be shared.

   Ask about anything the user has to decide (a rule, a wording, a default) instead of guessing.
3. **Branch:** `git switch -c feature/<short-name>` from an up-to-date `main`. Only start once
   any other open PR of yours is merged.
4. **Build it once, in shared code**, for Android and iOS together:
   - Logic goes in `core/model`/`core/engine` with `commonTest` tests.
   - Data goes in `core/data`; a schema change means a new Room migration (with
     `MigrationTest`) and, if synced, a new `supabase/migrations/` file and regenerated web types.
   - UI goes in `feature/*` `commonMain`; actions use `launchReporting(errors)`.
   - Strings go in Compose resources, matched in `web/src/lib/labels.ts` if the website says
     the same thing.
   - If a rule the website repeats changes, update `core/model/fixtures/training-math.json` first.
5. **Test:** unit tests for the logic, a view model or repository test for the behaviour, and
   new or re-recorded screenshot goldens for UI changes (say so in the PR).
6. **Check:** every command in CLAUDE.md, "Build and test", plus the database checks if the
   schema changed. Warnings are errors. Run the app on Android and, for UI changes, iOS.
7. **CHANGELOG.md:** a line under "Unreleased", in words a user would use.
8. **PR:**
   - what changed and why;
   - what was checked;
   - **what to test by hand** on a phone (a short numbered list);
   - anything not done.

   Don't merge without the user's go-ahead.
