---
name: automation-bug-report
description: After every automation run (local mvn or CI) that has red assertions, tell the user which failures look like product bugs, draft each bug in a structure developers can act on, and - only after the user confirms - file it in the tracker, assigned and linked to its user story. Use when a run report has failures, or when the user says "create a bug", "report this bug", "file it".
---

# Bugs from automation runs

The rule: **after checking any automation run, tell the user which product bugs it found; create a bug only when they
confirm it**, with the full structure below, assigned to the project's bug owner (`bugcycle/config.json` →
`tracker.assignTo`), **linked to the user story** (as its child) and to any related bug - then give the link.

## 1. After every run - triage (never skip, never create yet)

Right after the run report table (`node jenkins/run-report.js ...`, see **automation-framework**):

1. For each failed block decide: **product bug**, **test / data / env issue**, or **knock-on** of an earlier failure
   (skipped because a set-up block failed, "Nothing saved as ... in this run"). Only product bugs go on.
   - Test or env issue (a locator that no longer matches, a dropdown that did not open, a slow env, CI unreachable) →
     fix or re-run, don't file.
   - A red that is already known and tracked → say "known: #<id>", don't file again.
2. Find the **root cause layer** before calling it a bug: read the screenshot and the assertion row (#n name,
   expected, actual, hint), then compare what the UI shows with what the API returns (the report's Timeline tab lists
   the page's API calls; or call the endpoint with the project's API client). Front end (API right, UI wrong) vs back
   end (API wrong) goes in the bug. Say which only when you checked it; mark a guess as a hypothesis.
3. **Duplicate check** in the tracker: search open bugs by the feature name / endpoint / field; read the story's child
   bugs - a closed child bug may be the cause (a regression).
4. Tell the user in one short list: `<suite> block <n> - <one-line bug> (<layer>) - story #<id> - draft ready /
   duplicate of #<id>`. Then **stop and wait for "yes" per bug**.

## 2. The bug structure

**Title**: `[Feature] What is wrong: where / under which condition` - one line, searchable, never "not working".
e.g. `[Order details] Total shows "-": the details call returns an empty total although the order has items`

Body (the tracker's repro-steps field), sections in this order:
1. **Summary** - 1-2 lines: where, what the user sees, which layer is wrong.
2. **Preconditions** - role / permission, account, test data (the run's own user and values). **Never a password.**
3. **Steps to reproduce** - numbered, minimal, labels as on screen (in the UI's language).
4. **Expected** - with its source: the story and its acceptance criterion, not an opinion.
5. **Actual** - exactly what shows / returns.
6. **Evidence** - the API request + the relevant part of the response, the same data from another endpoint when it
   proves the layer, every env checked; the screenshot (the report's png or a recording frame, cropped to the area,
   the wrong value boxed).
7. **Analysis** - which layer, why; a suspected cause clearly marked "hypothesis".
8. **Impact** - who is affected, frequency (always / intermittent), envs.
9. **Suggested checks for the developer** - 1-3 concrete places to look.
10. **Found by automation** - suite, block, assertion `#n name`, expected / found / hint, run (local env + date, or
    CI job #build + report link).
11. **Retest after the fix** - the suite(s) to run + the negative case that must stay fixed.
12. Last line, for the retest: `AUTOMATION: suite=<suite>; block=<block name>; assertion=<assertion name without #n>` -
    the same values go into `bugcycle/bug-registry.json` (skill **suite-bug-cycle**).

System info: environments + URLs, date, frequency, layer.

Drafts are written to a file first (`bugs/<short-slug>.md` next to the story or the run), so the user can read one
before saying yes.

## 3. Tracker fields

Fill this table once per project from how the team's existing bugs look (open three recent ones and copy their
conventions), and keep `bugcycle/config.json` → `tracker` in step:

| Field | Value |
|---|---|
| Work item type / project | `<Bug>` in `<project>` |
| Assigned to | `tracker.assignTo`, unless the user names someone else |
| Area / component | `<...>` |
| Iteration / sprint | the current one |
| Environment found in | `tracker.environmentField` for the env |
| Severity / priority | `<the tracker's scale>` |
| Other required fields | `<...>` |
| Tags / labels | `tracker.tags` + the feature |
| Links | parent = the user story; related bugs; the screenshot as an attachment |

## 4. Access and safety

- Use the credential the machine already has for the tracker (for Azure DevOps: the one `git credential fill` returns;
  `bugcycle/ado.js`). Never print it, never ask for a token in chat. Fallback: the user's signed-in tracker session in
  the browser - they sign in; never type their password.
- **Creating a work item is a write to an external system**: it needs the user's explicit "yes" for that bug. If the
  write is refused, don't look for another route - hand over the finished draft (file + screenshot) and say what is
  missing.
- A create call that returns nothing readable may still have created the bug: **query for the title first** before
  any retry, never create twice.
- Never create, edit, close or comment on work items the user did not confirm. Never change existing bugs or stories.

## 5. Worked examples

Keep one or two of the project's own filed bugs here as the model (title, the text, its tracker id and link), and the
drafts in `bug-drafts/`.
