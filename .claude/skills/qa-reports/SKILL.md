---
name: qa-reports
description: Every report Claude gives the tester, their manager or the developers - the two tables after every action (done, blocks), the run report after every run, the session report at the end of every session (three tables), a summary page for a manager, and the evidence card for a developer. Use whenever the tester asks for a report, a summary, "what is the result", "send it to the developers", "update the report", or when any report is built, changed or refreshed. Read before building one so the format is reused.
---

# QA reports - what exists and how it is built

Every new kind of report goes into this file the same day: what it is, who reads it, how it is built, where its tools
are.

## House rules for every report

- **Tables, with borders**, never walls of text. Plain words, short lines, no jargon without a half-line explanation.
- **Facts only**: every number comes from a run, a query or a file you saw. Outward facts (pull request, bug, CI build)
  are re-read from the system after the action, not remembered. A skipped step is "not done", never "done"; a refused
  step is "refused" with what was refused. Never round a failure into a success.
- **No secrets**: no password, token, key, one-time code, or hint where one lives; users by tag or role only.
  Personal data in screenshots (faces, names of real people) is blurred or cropped.
- Links are clickable: files as `path:line`, tickets and pull requests as links.
- A page for a manager: **half and half** - the top half executive (number tiles, a chart or two, progress, almost no
  text), then a "Details" band and the detail tables. Every page shows a **"Last update"** label at the top.
- Status chips (New, Closed, Tested) have a border in their own colour; every bug screenshot has a red box around the
  element.
- A report is **dynamic when it can be**: it reads the data it shows (a json the tools write) instead of being retyped.
- Everything in the repo (report builders, saved reports) goes through a branch → pull request, never straight on main.

## 1. After every action - two tables

After **any** action or step - not only at the end - tell the tester, in tables, what was done and what blocks:

| What was done | Result | Evidence / link |
|---|---|---|
| the action in plain words | pass / fail / done / refused, with numbers | screenshot, ticket, file |

| Block or issue | Impact | Needed to go on | Photo | Action | Status |
|---|---|---|---|---|---|
| what stopped or failed (a refused permission, a missing setting, a sign-in, a tool that failed) | what cannot be done because of it | who or what clears it | a link to the screenshot or file that shows it, or - | one line: who does what next | Open / Blocked / Closed |

Rules: never skip the block table when something was refused, blocked, failed or skipped (say "none" when there is
none); one line per row; keep going with what is not blocked and list the blocked ones.

## 2. After every run - the run report

`node jenkins/run-report.js "<env> (local)"` (or `"<env> - CI #<build>" <testng-results.xml>`) right after any run;
write the table into the reply (pass / fail / skipped with %, time, one row per block with why it failed), then one
root-cause line per red. Details: **automation-framework**, *Run report*.

## 3. At the end of every session - the session report (three tables)

Default closing of every conversation or task that read, tested, changed, filed, ran or decided something - even when
it stopped half-way. Three small tables, in this order:

| What I did | Result |
|---|---|
| each action, one line | done / not done / partly (with the number or the link) |

| Issue | Impact | Status / who |
|---|---|---|
| each problem, blocker, refusal, leftover data, shared-checkout clash | what it blocks | open / closed / who is needed |

| Shippable? | State |
|---|---|
| each deliverable (pull request, bug draft, suite, skill, file) | **Shippable** (merged / ready to merge / safe to use) · **Not shippable** (why) · **Needs the tester** (what) |

End with one line: the next step and who owns it. Then the clickable `path:line` list of every changed file. Keep it
to one screen. A **full report** (headline + status, what was done, results in numbers, bugs found, issues and
blockers, questions with the default used, not done / risks, next steps with owners, files and changes) only when the
tester asks for "the report" or a manager summary. After a subagent hands back: fold its report in, check its key
claims yourself, and say which numbers are the agent's and which you verified.

## 4. Manager summary page (when asked)

The session report or a story's results as a private Artifact page (the tester decides who gets it): number tiles
(stories, tested, bugs filed, bugs open, blocks, decisions), progress and bugs-by-state charts, then a "Details" band:
stories (Tested, Partly, N/A, Blocked last), bugs by severity (most severe first), blocks table with Photo + Action,
decisions needed, next steps. Republish to the same link when something changes (a bug filed, closed, a story tested),
right after the change.

## 5. Developer evidence card (an API problem the developers must see)

One image or page: title, a table Env | Call | Account | Time | Answer (red box on the bad answer), the request fields
(secrets masked), the repro command, the impact. Built as HTML and rendered by Chrome headless at 2x
(`--headless=new --force-device-scale-factor=2 --window-size=1300,<h> --screenshot=<out.png> file:///<page.html>`).
The tester forwards it; Claude has no channel to the developers.

## 6. Bug evidence

Layouts, screenshots with a red box, GIF / video, request and answer json: skill **automation-bug-report**.

## Tool notes

- Each Bash call is a new shell - export and use variables in the same call.
- A toast lasts about a second: take frames every 0.5 s around the moment and look at all of them.
