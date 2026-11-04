---
description: Design review of the current change by a second agent that hasn't seen how it was built — "how could this be built better?" — then weigh each suggestion and apply only the real ones. Runs before every commit of a feature or fix; also on request.
argument-hint: "[branch, PR number or path; default: this branch's changes against main]"
---

# Design review

Scope: $ARGUMENTS (empty: everything on this branch against `main`, committed or not).

`/code-review` hunts for bugs. This asks a different question: **is this built the way it
should be?** The author has spent the whole session deciding how, so the review goes to a
fresh agent that hasn't. Run it after the checks pass and before committing, so its changes
go through the checks too.

## 1. Gather the change

`git diff main...HEAD` plus `git diff HEAD` (uncommitted work), or the target given above.
Note the files it touches; the reviewer gets the list, not your summary of it.

## 2. Ask a fresh agent

Spawn one `general-purpose` agent (not a fork: it must not inherit this conversation, so it
isn't anchored on how the change came about). Give it only the list of changed files, how to
get the diff, and this brief, word for word:

> You are reviewing a change to Spotter, an offline-first gym app (Kotlin Multiplatform for
> Android and iOS, a Next.js website, Supabase). Read `CLAUDE.md` first: it sets the
> architecture and the rules. Then read the diff, and for each changed file the code around
> it, its callers and the existing helpers nearby.
>
> **How could this be built better?** Look for:
> - redundant code: logic written twice, state that can be derived, a helper that already
>   exists in the codebase (name it);
> - dead code: anything added or left behind that nothing uses;
> - code that makes future additions difficult: logic in the wrong layer, a rule the
>   website repeats that isn't pinned by the fixture, a special case where a general
>   mechanism belongs, hard-coded values that should be data, coupling that means the next
>   similar feature has to touch many files.
>
> Do not suggest changes for the sake of it or to have something to say. Every suggestion
> must be a real, valid concern: name the file and line, what is wrong, the concrete cost if
> it stays (what breaks, what gets duplicated, which future change gets harder and how),
> and the better form. Leave out style preferences, renames, comment wording, and anything
> that is a matter of taste. If the change is already well built, say so and return nothing:
> an empty list is a good answer.
>
> Read only; change no files. Return at most 8 suggestions, most important first.

## 3. Weigh each suggestion yourself

The agent's suggestions are claims, not instructions. For each one, read the code it names
and decide:

- **Apply** it when the concern is real and the better form is clearly better here, not just
  different. Keep the change to what the suggestion is about.
- **Skip** it when it's wrong (the helper doesn't fit, the "dead" code is used), a matter of
  taste, or costs more than it saves. Note one line saying why.
- **Ask** the user when it would change behaviour, a rule or the scope of the PR.

Don't apply a suggestion to look thorough, and don't skip one because it means more work.

## 4. After changes

Re-run the checks the changes affect (CLAUDE.md, "Build and test"). In the PR, under
"What was checked", add a short "Design review" list: what was applied, and what was skipped
with its reason. If nothing came up, say so in one line.
