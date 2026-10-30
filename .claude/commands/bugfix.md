---
description: Fix a Spotter bug test-first — reproduce it with a failing test, show it fails for the right reason, then fix, check, changelog and PR.
argument-hint: "<what goes wrong, and how to make it happen>"
---

# Bug fix

The bug: $ARGUMENTS

Follow CLAUDE.md. A bug fix **always starts with a failing test**. In order:

1. **Understand it.** Read the code involved and find the cause before changing anything. If
   the report is unclear (which screen, which platform, what was typed), ask.
2. **Branch:** `git switch -c fix/<short-name>` from an up-to-date `main`. Only start once any
   other open PR of yours is merged.
3. **Write the failing test**, at the lowest layer that shows the bug:
   - a `commonTest` unit test for logic;
   - a repository test (in-memory Room) for data;
   - `SessionViewModelTest`-style for a view model;
   - an iOS test (`iosTest`) if it's iOS-only.

   For timing bugs, make the timing deterministic (test dispatchers, a gate, a fixed clock and
   time zone), so the test fails every time, not sometimes.
4. **Run it and show it fails for the reason in the report**: the assertion that captures the
   bug, not a setup error. Keep the failure output for the PR.
5. **Fix the cause**, not the symptom, in shared code unless the bug is truly platform-specific.
   Look for the same mistake elsewhere (`git grep`) and fix those too, each with a test.
6. **Run the test again:** it passes. Then every check in CLAUDE.md, "Build and test".
7. **CHANGELOG.md:** a line under "Unreleased" → "Fixed", saying what the user saw.
8. **PR:**
   - the bug;
   - the cause;
   - the fix;
   - the test and that it failed first (with the message);
   - what was checked;
   - how to check it by hand.

   Don't merge without the user's go-ahead.
