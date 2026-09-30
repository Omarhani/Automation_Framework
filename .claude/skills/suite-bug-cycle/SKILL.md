---
name: suite-bug-cycle
description: The suite → bug → retest cycle. Use when the user says "run the <group> group / suites", "find bugs", "retest the bugs", "retest <id>", "check the fixed bugs". Runs a suite group through the suite-bug-runner agent (up to 3 retries per failing suite to separate real bugs from flaky runs), reports, files the confirmed bugs in the tracker only after the user's approval, and later retests the fixed bugs and moves the passing ones to the next tracker state - on request, or automatically through a scheduled watcher. Also for "is the watcher running", "what did the watcher do".
---

# Suite → bug → retest cycle

Purpose: separate real bugs from flaky runs before bothering developers, and close the loop after a fix without
manual retesting.

| Part | Where |
|---|---|
| Groups | `bugcycle/suite-groups.json` - smoke, api, web, mobile, hybrid, all (`@name` pulls a group in) |
| Settings | `bugcycle/config.json` - retries, envs, failure patterns, the tracker (org, project, assignee, tag), retest rules |
| Runner (detached) | `bugcycle/run-group.js`, `bugcycle/retest.js`, `bugcycle/wait.js` → `bugcycle/runs/<time>-<group>/` (gitignored) |
| Tracker | `bugcycle/ado.js` (Azure DevOps with the credential git has; query / read / patch), `bugcycle/watch.js` (which bugs wait for a retest), `bugcycle/move.js` (move the passing ones + comment; `--validate` = check only) |
| Filed bugs → what to retest | `bugcycle/bug-registry.json` (commit it with the next change after filing) |
| Agent | `.claude/agents/suite-bug-runner.md` - runs, retries, triages, drafts; never touches the tracker |
| Bug structure | skill **automation-bug-report** |

Before first use, fill `bugcycle/config.json`: the tracker's `org` / `project` / `assignTo` / `tags`, and the `retest`
rules - which tracker **states** mean "the fix is deployed on <env>" and which state a passing bug moves to. With
another tracker, re-implement the six functions `bugcycle/ado.js` exports.

## Step 1 - run (on request)

"Run the hybrid group" / "run smoke on Test": start the **suite-bug-runner** agent in the background with the group
and the envs (default: `config.defaultEnvs`). Tell the user it started and roughly how long it takes (each retry
repeats one suite). The agent returns the report.

Verdicts the runner gives each failure:

| Verdict | Means |
|---|---|
| **confirmed** | failed in every attempt (first run + retries) - a real, repeatable failure: bug candidate |
| **flaky** | passed in at least one retry - not a bug; worth a look at the test |
| **unconfirmed** | never reproduced but never passed either (set-up got in the way) |
| **env** | the machine or the run's set-up, not the product (`config.envIssuePattern`) - not retried |
| **knock-on** | an earlier block failed, so this one had nothing to work on (`config.knockOnPattern`) - not retried |

## Step 2 - report

Post the agent's report as it is: the suite × env table, then the confirmed failures (product bug with its layer, or
test problem), flaky / env / knock-on in one line each, leftovers. For each **product bug** show the draft title and
ask **per bug**: file it? (and which user story it belongs to, when the draft says "story: ask"). Test problems: offer
to fix the suite (normal delivery workflow). Nothing is filed without a yes for that bug.

## Step 3 - file (after "yes" for that bug)

With the tracker credential git already has (never printed; never ask the user to paste a token):

1. **Duplicate check first**: search open bugs by the title's key words; read the story's child bugs too.
2. The current iteration / sprint, when the tracker has one.
3. The screenshot, cropped to the area and small, as an attachment.
4. Create the bug with the fields and the text structure of **automation-bug-report** (assigned to
   `config.tracker.assignTo`, tagged `config.tracker.tags`, linked to its story as a child, related bugs linked).
5. If the answer to the create call is unclear, **query by title before any retry** - never file twice.
6. Add the bug to `bugcycle/bug-registry.json` (id, title, story, suite, block, assertion, foundOn, filed) and give the
   user the link.

## Step 4 - retest after the fix (automatic, or "retest the bugs")

**How it knows the fix is there:** the bug's tracker **state**. `config.retest` lists the states that mean "deployed
on Test" / "deployed on Stage"; any other state is not retested yet.

**On request** ("retest the bugs" / "retest 1201"):
1. `node bugcycle/watch.js` → `PLAN: 1201:Test,...` - bugs with the automation tag in a retest state that are in
   `bug-registry.json`. A bug already retested at the same revision (not fixed / inconclusive) is skipped until the
   developers change it again - no loops. Memory: `bugcycle/runs/watch-state.json`.
2. `node bugcycle/retest.js --plan <PLAN> --detach`, then `node bugcycle/wait.js <folder> 540` until it prints
   `state: done`. Verdicts: **fixed** (its block passed), **not fixed** (the same assertion failed in every attempt),
   **inconclusive** (the block never got that far).
3. `node bugcycle/move.js --run <folder>` (step 5), then a result table for the user.

Or the **suite-bug-runner** agent in mode 2 with `id:env,...` when its triage of a not-fixed bug is wanted.

**Automatic - a watcher:** a scheduled task (every hour in working hours is a good start) that runs the same four
commands, stops quietly when the plan is empty, waits for the next turn when a run is already going or the phone is
busy, and sends one notification with the result table.

## Step 5 - move the passing bugs

`node bugcycle/move.js --run <retest folder>`: every **fixed** bug still in its retest state gets the `to` state of
its rule with a comment (env, suite, block, assertion, attempts, run folder) - guarded by the bug's revision, so a bug
someone moved meanwhile is left alone - and is read back. Output also in `<folder>/moves.md`.

- **Not fixed**: never moved by itself. Say what still fails (same symptom or a new one) and propose
  `config.failedRetestProposal` with the evidence; move it only on the user's yes.
- **Inconclusive**: no move; say why (env issue, set-up failed) and offer a re-run.
- If a tracker write is refused, stop and say which step is left - don't look for another route.

## Rules

- One bugcycle run at a time (one phone, one `target/` folder); the runner refuses a second one.
- A run at an hour when the test env is known to be unreliable: say so next to its failures.
- Runs create and delete their own data only; leftovers of a cut-off run are listed in the summary
  (`config.leftovers`) - delete exactly those, by API.
- CI is the other way to run a suite; the same retries can be done there by re-queuing the job and reading each
  build's `testng-results.xml` with `jenkins/run-report.js`.
- The retest finds a bug's assertion by **block name + assertion name**: renaming either means updating
  `bug-registry.json` in the same change.
