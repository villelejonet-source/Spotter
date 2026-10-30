# Changelog

What changed for people using Spotter, newest first. Every pull request that changes something a
user or the website would notice adds a line under **Unreleased**; `/release` turns that section
into the new version's entry and writes the store notes from it. Versions are the Android
`versionName` (`versionCode` in brackets); the full build record is the version table in
`docs/play-store/README.md`.

## Unreleased

### iOS (not released yet)
- The whole app runs on iOS from the same shared code as Android (#13–#20).
- Fixed a crash on iOS whenever the app logged a warning, such as a failed sync (#27).
- Builds without cloud backup no longer show account buttons that couldn't work (#21).

## 1.1.0 (8) — 2026-10-30

### Fixed
- A set could lose a value or an edit: a tick made right after typing could be undone by the
  typed value or undo it, typing a weight could clear reps the screen hadn't caught up with, and
  "copy previous" could be overwritten by a value still waiting to save (#22).
- Typing a lone "." as a weight no longer turns the set into a bodyweight set without saying so;
  reps and effort reject "8,5" instead of clearing the field (#23).
- Exercise history no longer shows volume or a 1RM for weighted holds such as a plank with a
  plate, matching the website and the workout summary (#23).
- Starting weights from onboarding treat a single rep as its own 1RM, as everywhere else (#23).
- The keypad's +2.5 then Done no longer reverts the weight (#19).
- Between midnight and 01:00 (02:00 in summer) the app treated "today" as yesterday: a finished
  deload week still lightened workouts, yesterday's scheduled workout still showed as upcoming,
  and a deload applied then started a day early. "Today" is now the phone's date everywhere (#29).

### Changed
- A save or action that fails now says so ("Couldn't save. Try again.") instead of the app
  closing or nothing happening. A sign-in link that doesn't work says so too (#27).
- Decimal commas work everywhere a number is typed, including the website's body-weight log
  ("82,5") in every browser (#23).

### Also in this release (from 1.0.1, which was never uploaded)
- Backup and sync only ever download your own account's data, ready for coaching features.
- When it syncs, the app reports its version number (see the updated privacy policy).

## 1.0.1 (7)

Never uploaded; its changes shipped in 1.1.0.
- Backup and sync only ever download your own account's data, ready for coaching features.
- When it syncs, the app reports its version number (see the updated privacy policy).

## 1.0.0 (6) — 2026-10-25

- Fixed typing in the exercise search: letters no longer go missing or jump around.

## 0.11.0 (5) — 2026-10-25

- Time planks and other holds in seconds.
- Sign in to backup with a password as well as an emailed code.
- Safer sign-in: links only work on the phone that asked.
- Undo when you delete a set.
- Faster start-up and smoother logging.
- Clearer screens, loading states and messages throughout.

## 0.10.0 (3) — 2026-10-25

First production release (Sweden only).
