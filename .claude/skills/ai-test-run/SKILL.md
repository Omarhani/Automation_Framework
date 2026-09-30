---
name: ai-test-run
description: Execute test cases manually in the browser as an AI tester and produce a run report - pass / fail / skipped counts with percentages, per-case execution time, step log with timestamps, a GIF per case, and flaky detection via retry. Use when the user says "run these test cases", "test this page", "do a regression pass", "execute the smoke suite by hand", or asks for a test report from an exploratory session. Not for running the Java suites - that is the automation-framework skill.
---

# AI test run

Claude plays the manual tester: drives the app in the browser, judges each case against an explicit expected result,
times it, retries suspected flakes, and hands back a report with rates.

Pair it with **app-testing** (the app map, login, known quirks, helper JS) for the app under test. This skill is the
*process*; that one is the *knowledge*.

## Before starting

1. **Get the case list.** In order of preference: cases the user gives -> cases derived from a suite XML
   (`suiteFiles/**/*.xml`, one `<test>` block = one case) -> a smoke list in **app-testing** -> cases you propose and
   the user confirms.
2. **Never invent a case list and run it silently.** Show the list first if you built it yourself, then run.
3. **Confirm scope for anything destructive.** Bulk actions, approvals, "send email to all", or acting on records the
   user didn't name - ask first, per the safety rules in **app-testing**.
4. **State the environment** you are testing (env name + URL, browser profile + role, UI language) and record it in the
   report header. Use the environments listed in **app-testing**; default to the first test env, never production
   unless the user explicitly says so. The environment picks the report folder, `report/ai-runs/<env>Env/` (see
   **The report**), so don't add an env suffix to the file name. Set the environment row in the generator
   (`ENVIRONMENT_LINE`) to what you actually used. If the app shows its login page, stop and ask the user to sign in -
   never type passwords.
5. **Number the cases** `TC-01`, `TC-02`, ... and keep those ids in every table.

## Executing one case

Each case needs three things written down *before* you act: **steps**, **expected**, **actual**.

```
TC-04  Delete a draft order
Steps    Insert "Order 1042" in Search field -> Click Delete row action -> Click OK button in confirmation dialog
Expected row is removed from the list; the order is no longer found by search
Actual   row gone; search shows the empty state; no toast appeared
Status   PASS (note: no success toast - known issue, see app-testing)
```

Rules:
- **Verify with a check, not with a vibe.** Read the DOM (`document.evaluate(...).snapshotLength`), the toast text, or the row state. "It looked right" is not a result.
- **One case at a time.** Do not mix cases; a half-finished case pollutes the next one's preconditions.
- **Reset state between cases** where the case changed something, or declare the dependency in the report.
- **Screenshot only on FAIL** or when the user asks. Screenshots are expensive; DOM checks are cheap.
- If a case cannot start because its precondition is missing, that is **SKIPPED**, not FAIL.
- **Set-up and clean-up go through the API, not through clicks** (the **automation-framework** rule; recipes in
  **api-catalog**). Before the first step, make the case's preconditions true with the API: the project's
  create-by-API utility for the user (it prints what it created), a scratch suite of set-up blocks for anything else.
  After the last case, remove it the same way, then check through a list call that nothing with the run's tag is
  left. The recorded browser steps are only the case's own steps; set-up and clean-up go into the report as
  `Setup - … -` / `Clean up - … -` items with the call that was made and its answer. A precondition that has no API yet
  is set by hand **only** after saying so in the report, and it is a gap to add to the catalog.

### Step log: status + timestamp (every step, every test case)

**Every logged step gets a real status and an exact timestamp**, so the HTML report never shows `—`.
- At the start of each case, paste this logger into the tab. It keeps the log in `sessionStorage`, so it survives page
  navigations in the same tab. Paste it again after every navigation, because a reload loses `window` functions.
```js
window.__log=(step,status='Info')=>{const k='ai_steplog';const a=JSON.parse(sessionStorage.getItem(k)||'[]');const d=new Date();a.push({t:d.toLocaleTimeString('en-US'),ms:d.getTime(),step,status});sessionStorage.setItem(k,JSON.stringify(a));return a.length;};
window.__logDump=()=>JSON.parse(sessionStorage.getItem('ai_steplog')||'[]').map(e=>e.t+'|'+e.ms+'|'+e.status+'|'+e.step).join('\n');
window.__logReset=()=>sessionStorage.removeItem('ai_steplog');
```
- **Write every step as a plain-English action a manual tester could follow** - never camelCase method names like
  `insertFirstName: AI` or `clickSave`. Start with the verb, put typed values and options in double quotes, and name
  the field by its visible label:

| Action | Format | Example |
|---|---|---|
| Type | `Insert "<value>" in <Label> field` | `Insert "AI" in First Name field` |
| Click | `Click <Label> button` (or `link` / `tab` / `row action` / `icon`) | `Click Save button` |
| Choose | `Select "<option>" from <Label> dropdown` | `Select "Sales" from Department dropdown` |
| Tick | `Check <Label> checkbox` / `Uncheck <Label> checkbox` | `Check Is Manager checkbox` |
| Search | `Insert "<value>" in Search field` then `Press Enter` | |
| Open page | `Navigate to <Page name> page` | `Navigate to Orders page` |
| Confirm | `Click <Label> button in confirmation dialog` | `Click OK button in confirmation dialog` |
| Verify | `Verify <what> actual: <x> / expected: <y>` | `Verify toast message actual: … / expected: …` |

  Use the label as it appears on screen (in the UI's language), and add the English meaning in brackets when the UI
  is in another language: `Click <label> (Save) button`. Setup / info lines stay plain sentences
  (`Precondition: orders count 18`).
- Call `__log('Click Save button')` **in the same JS call as the action**, right before it fires, so the time is the
  action's time and not the time you wrote it down.
- Every verification logs its own status: `__log('Verify total actual: 09:00 / expected: 09:00','Pass')` or `'Fail'`.
  Deviations log as `'Warning'`, and a step that couldn't run logs as `'Skip'`. Setup and navigation steps stay `'Info'`.
- The case starts at the first `__log` and ends at the last. Take the duration from their `ms` difference.
- If you're working in more than one tab or browser, keep one log per tab and merge them by `ms` when writing the report.
- `__logDump()` returns `time|ms|status|step` lines - exactly the format `build-report.example.js` parses. Copy them
  out at the end of each case, then `__logReset()` before the next one.

### Video (every test case)

Record **every test case from its first action to its verification**, like the `.gif` the Java framework attaches
per test.
- Before the first action: `gif_creator start_recording` on the case's tab, then a screenshot so the first frame shows the starting state.
- Do the whole case in that tab group, including setup pages, so every step is in the recording.
- After the verification: a screenshot for the last frame, then `stop_recording`, then `export` with `download: true`,
  `filename: <testName>.gif`, and options `showActionLabels`, `showClickIndicators`, `showProgressBar` on. Call
  `clear` before the next case so frames don't carry over.
- Downloading needs the user's OK. Ask once at the start of the run for all cases in it. Then move the file from the
  Downloads folder into the run's folder: `report/ai-runs/<env>Env/<Report-Name>/<testName>.gif` (see **The report**).
- **Only real `computer` actions (click, type, key, scroll) add frames.** JS clicks, `navigate` and screenshots add
  none, so do each visible step with a real click. The limit is **50 frames per recording**; a form with many fields
  can use all of them. Skip extra clicks, and split a long case into setup / cleanup recordings if needed.
- Start a recording **after** `navigate` and after the page has loaded, then click. The first click on a freshly
  loaded page can miss (a dropdown that doesn't open, a search that doesn't filter). Re-read the DOM before logging a verdict.
- Use `triple_click` + `type` to replace an input's value in framework-bound forms; `key "ctrl+a Delete"` can type a literal `a`.
- Output is a **GIF**. In the HTML report, add a last event row `<a href='<testName>.gif'> Download Video </a>`, the
  same as the framework's report. In the `.md`, link it in the results table.
- If the recording fails or is missing, say so in the report. Never re-use another run's recording.

### Timing

Start a wall-clock timer when the first action of the case fires, stop it when the verification returns. Record seconds, one decimal.

- Report per-case duration, plus **total**, **average**, and the **three slowest** cases.
- Note any case that took more than ~2x the average - slow usually means retries, animation waits, or a genuinely
  slow API, and it is worth calling out separately from a failure.
- Time spent waiting for the user's answer does not count toward a case's duration; say so if it distorts the total.

## Statuses

| Status | Meaning |
|---|---|
| **PASS** | Actual matched expected, verified by a check. |
| **FAIL** | Actual contradicted expected. A real defect in the app. |
| **FLAKY** | Failed, then passed on retry with identical steps. Counts as executed and passed, but is reported separately - it is a signal, not a clean pass. |
| **SKIPPED** | Not executed: precondition missing, depends on a failed case, out of the agreed scope, or needs a permission the user hasn't given. |
| **BLOCKED** | Started but could not be judged: app error page, environment down, element never rendered, session expired. Distinct from FAIL - you have no verdict, not a negative one. |

Never record a status you did not observe. A case you ran out of time on is SKIPPED with the reason, not PASS.

## Flaky handling

Flakiness is the main risk in AI-driven testing - most first-attempt failures are timing, not defects.

**Retry policy**
1. On a FAIL, retry the case **once**, from a clean state (reload the page, re-paste helpers, re-navigate).
2. Passed on retry -> **FLAKY**. Record both durations and what differed.
3. Failed twice the same way -> **FAIL**, and you can state it is reproducible.
4. Failed twice *differently* -> **BLOCKED**, and describe both symptoms; something is unstable underneath.
5. **Never auto-retry a destructive action** (delete, reject, approve, send-email, bulk). The first attempt may have
   partially succeeded - re-check the record's state and report it, then ask.

**Frozen tab = recover, don't stop.** If the tab times out on scripts or screenshots, open a new tab, go to the same
URL, paste the helpers and logger again, and carry on from the last confirmed step. `sessionStorage` does not carry
over to the new tab, so rebuild the step log from what you already returned. A frozen tab is an environment problem,
not a case result; note the switch in Observations. Ask the user only if the fresh tab fails too, or it needs a login.

**Before blaming the app, rule out the usual causes** (and the app-specific ones in **app-testing**):
- A dialog / drawer still animating, or its fields / edit data still loading after the container appeared.
- A toast, spinner overlay or floating widget covering the target.
- A closed modal still in the DOM in its hidden state.
- A search value set by JS instead of typed + Enter, so no filtering happened.
- Browser autofill overwriting fields after you set them.
- A locator matching 0 or >1 elements - check the count before concluding.

If the failure came from one of these, the case is **FLAKY** (environment), not a defect. Say which cause you found.
If you could not determine the cause, say that too rather than guessing.

## The report

**One folder per environment, then one folder per run**, the same layout as the Java framework's
`report/<env>Env/<suite>/<suite>.html`:

```
report/ai-runs/
  testEnv/
    Checkout-With-Saved-Card_2026-01-31/
      Checkout-With-Saved-Card_2026-01-31.html
      Checkout-With-Saved-Card_2026-01-31.md
      <testName>.gif  ...  <testName>.jpg
  stageEnv/
    <Report-Name>/<Report-Name>.html + .md + .gif + .jpg
```

`<Report-Name>` is `<Suite-Title>_<yyyy-mm-dd>`. The `.md`, the `.html`, every `.gif` and every `.jpg` of the run go
in its folder, so the relative links in both reports work. Pick the env folder from the environment you actually
tested. Offer to publish the report as an artifact if the user wants a shareable link.

**Naming: give every report a clear title, not just the date.** `<Suite-Title>` is a Title-Case, hyphenated
description of what the run tests, plus the key test record when there is one (e.g.
`Checkout-With-Saved-Card_2026-01-31`, `Add-User-And-Assign-Role_run2_2026-01-31`). Use the same title (with spaces)
in the `.md` H1 (`# AI Report — <Suite Title>`), the HTML `<title>` (`AI Report | <Suite Title> | <date>`), and the
green `suite-title` badge in the HTML header. If a run is paused, say so under the H1 and update the same files when
it resumes; don't create new ones.

**Also write an HTML copy in the ExtentReports Spark format** of the framework: same Spark CDN CSS / JS,
`body class="spa -report dark"`, test list + dashboard view, **but branded as an AI report** so it can't be mistaken
for an automation run:
- **Green theme:** header / body `#0b2e1f`, side nav and test list `#134e36`, content and dashboard `#1a5c40`, hover `#0f3d2a`, borders `#2f7a55`.
- **Text logo** in place of the Extent image: `<div class="logo ai-logo"><i class="fa fa-magic"></i>AI Report</div>`, with the `.nav-logo` width set to `auto`.

Both pieces live in this skill folder, so a wiped `report/` can't break them:
- **`ai-report-template-head.html`** - the green shell up to the test list (placeholders `SUITE` and `REPORT_DATE`).
- **`build-report.example.js`** - the generator. Copy it to the scratchpad, set `ENV`, `REPORT_NAME`, `REPORT_DIR`,
  `SUITE`, `RUN_DATE` and `ENVIRONMENT_LINE` at the top, paste the step logs (`time|ms|status|step` lines from
  `__logDump()`) into `LOGS` and the case list into `CASES`, then run it with `node`. It builds the Extent rows,
  durations, dashboard counts and timeline from the logs. Setup and cleanup go in as their own test items
  (`Setup - … -`, `Clean up - … -`), the same as the framework does. A mis-logged entry can be corrected in `relabel`
  (by its `ms`) and explained in Observations - never edit the raw log.

`report/` is git-ignored: check the reports still exist before saying they're done.

HTML contents:
- One `<li class="test-item" status="pass|fail|skip">` per case, with a camelCase test name, start time and `hh:mm:ss:ms` duration.
- Event rows the way the framework logs them: teal `Environment` and `Steps To Reproduce` banners -> one row per
  action, worded `Insert … / Click … / Select …` as in **Step log** -> brown `Actual Result` / `Expected Result`
  banner pairs -> teal `Ends of Steps` -> the result row. Deviations go in as `warning-bg` rows.
- Screenshot: save it next to the HTML as `<testName>.jpg` and reference it from the case's `media`.
- Dashboard cards and the `timeline` / `statusGroup` scripts must match the real counts (the generator computes them).
- Only exact timestamps, taken from the step log. If a time is missing because the logger wasn't used, that's a
  process miss: write `&mdash;`, say so in an Observations row, and never invent a time.

### 1. Header
Date, environment URL, browser, account / role, language, who ran it (AI), total wall-clock for the session.

### 2. Summary

```
Total cases      12
Executed         10   (PASS 7 + FAIL 3)
Skipped           2
Blocked           0

Pass rate        70.0 %   (7 / 10 executed)
Coverage         83.3 %   (10 / 12 total)
Flaky rate       20.0 %   (2 / 10 executed)

Execution time   14m 22s total · 86.2s average · slowest TC-07 (241.0s)
```

Definitions - use these exactly, and show the fraction next to every percentage:
- **Pass rate** = PASS ÷ executed. Skipped and blocked are **excluded** from the denominator.
- **Coverage** = executed ÷ total cases.
- **Flaky rate** = FLAKY ÷ executed. A flaky case is counted in PASS as well.
- Round to one decimal. If nothing executed, write "n/a", never 0 % or 100 %.

### 3. Results table

| ID | Case | Status | Time | Attempts | Note |
|---|---|---|---|---|---|
| TC-01 | Add user with valid data | PASS | 41.3s | 1 | toast "User added successfully" |
| TC-02 | Add user, duplicate username | FAIL | 28.7s | 2 | saved instead of rejecting |
| TC-03 | Edit user first name | FLAKY | 33.1s / 19.8s | 2 | 1st attempt: dialog not loaded |
| TC-04 | Bulk delete | SKIPPED | — | 0 | destructive, not approved |

### 4. Failures - one block each
Case id, exact steps to reproduce, expected vs. actual, evidence (screenshot path, toast text, DOM count, network
status), and whether it is reproducible. Link it to a known issue in **app-testing** if it matches one.

### 5. Flaky cases
What failed the first time, what changed on retry, and the suspected cause. Flag anything that flaked twice across
runs - that belongs in the automation as an extra wait, not as a retry.

### 6. Observations
Slow pages, UI oddities, typos, anything outside the case list. Keep it short and factual.

### 7. Automation follow-up (when relevant)
Which failures are worth encoding as Java tests, and which locators the run confirmed or broke. Hand the locator work
to **xpath-locators** and the test-writing to **automation-framework**; add new app knowledge to **app-testing**.

## Honesty rules

- Report what happened. A failing run is a successful test session.
- Never mark PASS to make the numbers look better, and never quietly drop a case from the total to raise the pass rate.
- If you had to deviate from a case's steps to make it work, that is a finding - record the deviation in the note column.
- If a whole area went untested, say so in the summary rather than leaving it implied by the count.
- Percentages always carry their fraction, so nobody reads "70 %" without seeing it is 7 of 10.
