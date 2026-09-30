---
name: testing-activity
description: The Testing Activity for one user story, end to end - read the story and every ticket around it in the tracker, learn the business, walk it on the test environment, write the test cases (set-up by API → steps → expected → clean-up by API), test it by hand, then plan, design, implement and run the automation (added to an existing suite when it fits, else a new suite on its own data), push, open and merge the pull request, and run it in CI; the knowledge skills are updated after every phase. Use when the user gives a story number - "testing activity for 1234", "test story X", "automate story X". The agent testing-activity runs the same procedure.
---

# Testing Activity - one user story, end to end

Input: a story id (and optionally keywords, or limits such as "web only" / "manual only" / "no CI"). Env: the **test
env**; any other env only when the user asks; never production.

Skills this procedure leans on - read them at the phase that needs them, not all up front:

| Skill | For |
|---|---|
| **business-knowledge** | business rules per module - read in phase 1, update after phases 1, 2, 3 and 7 |
| **app-testing** | app map, mapped pages, locators, quirks - update after phases 2 and 3 |
| **api-catalog** | every call a set-up / clean-up step can use, and whether it is verified (phases 2-6); update it whenever a walk or a run shows a new call |
| **ai-test-run** | how to run and report the manual test cases (phase 3) |
| **automation-framework** | code, suite XML, running, delivery, run report (phases 4-7); the rule *a test case is set-up → test → clean-up, set-up and clean-up by API* (phases 3-6) |
| **xpath-locators** | every new locator |
| **automation-bug-report** | a red that is a product bug (phases 3 and 6) - the user decides, nothing is filed without a yes |

Every phase leaves a file in `testingActivity/stories/<id>/` (committed with the pull request; `attachments/` is
gitignored). Tell the user in one line when each phase starts and ends (and the branch).

**Test cases are created the `create-test-case` way** (agent `.claude/agents/create-test-case.md`): each case =
**Set-up (API) → Steps → Expected → Clean-up (API)**, with a data plan that names the call behind every precondition.
Phases 3-6 follow that agent's steps; in the main session do them yourself, or hand one scenario's cases to the agent
(it returns the cases, the data plan, the calls it verified and a green run) and carry on with phase 7.

## Phase 1 - Read the story and everything around it

1. `node testingActivity/story.js <id>` → `testingActivity/stories/<id>/story.md`: the story with its description,
   acceptance criteria and discussion (comments often **change** the criteria), attachments downloaded (designs, API
   collections - open them), the parent and its text, children (bugs in full), related links, siblings under the same
   parent, recent stories and bugs of the area, and keyword matches. Add `--keywords "a,b"` when the title words are
   too generic.
2. Open the old tickets that matter (`node testingActivity/story.js <otherId> --no-attachments`): every child bug,
   siblings and keyword hits on the same screen or rule. Old bugs are the best source of what breaks.
3. Read the module(s) of **business-knowledge** it touches.
4. Write `analysis.md` in the story folder:
   - **Business** in plain words: actor, goal, where (web page / app screen), what changes for whom.
   - **Rules** numbered R1..Rn, each with its source (criterion, comment, old ticket, knowledge module).
   - **Conflicts and gaps**: criteria vs comments vs old bugs vs current behaviour. Each one is either an assumption
     you can check on the test env in phase 2, or a **question for the user**.
   - **Channel** (see *Decisions*): web / mob / hybrid / api, and why.
   - **Users and data needed**: how many users, their roles, settings, records, master data.
5. **Update business-knowledge** with the new rules (source = the story / ticket) and the story-log row.

Questions: ask only what changes an expected value or the scope and cannot be settled by the story, its tickets or a
walk - all at once, each with a recommended answer. Everything else: decide, write the assumption in `analysis.md`,
go on.

## Phase 2 - Learn it on the test env

- The browser on the test env (the user is signed in there; never type a password - on the login page, ask them to
  sign in). Check first that you are looking at the design / language / role the automation runs on (**app-testing**).
  Mobile: the device with `adb` (framework skill, *walk it first*).
- If the test env is known to be unreliable at certain hours (**app-testing** → quirks), say so when walking then and
  re-check anything odd later.
- Walk the feature as the actor, in every UI language the story shows text in. Watch the network calls: endpoint,
  body, and the response fields behind each value on screen - that is how phase 6 tells front end from back end, and
  **each write call seen is a future set-up / clean-up step: record it in api-catalog** the same day.
- Own data only: create the user(s) through the project's create-by-API utility and delete them after. Never touch
  data you did not create; never the protected accounts. A **setting** the walk has to flip: ask first, put it back.
- Write what you saw into `analysis.md` (rules confirmed / contradicted) and **update app-testing** (page map,
  locators verified with 1 match and a non-zero size, quirks) and **business-knowledge** (observed behaviour).

## Phase 3 - Test cases, then test by hand

1. Test cases in `test-cases.md`: `TC-01..` in the create-test-case block format - title, criterion / rule covered,
   priority, type (positive / negative / boundary / permission / language / empty state / setting on-off), channel,
   **Set-up (API)** (one line per precondition with the block or endpoint that makes it true - data by tag, never a
   password), **Steps**, **Expected** (real values from phase 2 when the story does not fix them, each a named
   assertion), **Clean-up (API)** (reverse order, only what the case created, settings put back), and **automate? yes
   / no + why** (no = one-off visual check, needs a real device feature, needs a password, costs more than it covers).
   Before the cases: the **data plan table** (entity → state → set-up call → id kept → clean-up call → catalog status
   ✅ / 🔎 / ❌). A 🔎 or ❌ call is wrapped and verified with own data first - that is part of this phase, not of
   automation. End with a **criterion → TC** coverage table: every criterion has a TC or a reason.
2. Run them by hand with **ai-test-run** → `manual-run.md` (pass / fail / skipped, times, evidence): **set-up through
   the API**, the steps by hand, **clean-up through the API**, then the leftover check (nothing with the run's tag is
   listed any more). Manual testing comes first - automation starts only after it.
3. Each fail: product bug or my mistake? A product bug → draft it in the **automation-bug-report** structure
   (`bugs/<slug>.md`, linked to the story), list it to the user, file only on a yes. Do not wait for the answer to go
   on with phase 4.
4. Update **app-testing** / **business-knowledge** / **api-catalog** with what the run taught.

## Phase 4 - Plan and design the automation

Write `automation-plan.md` - the decisions below, each with its reason:
- **Channel** and **placement**: the existing suite to extend (which blocks, inserted where) or the new suite's path.
- **Blocks** in order, in three groups - `Set up - ... -` (API), the scenario's blocks, `Clean up - ... -` (API,
  reverse order) - one line each: test class + method (existing or new), parameters, what it asserts (named
  assertions, a soft group for several fields).
- New / changed code: page objects, screens, API clients, generic test methods (parameterised on the class that owns
  the area - never a class per scenario, never two methods that differ only by data).
- Data: the data plan table from phase 3 (users by tag, account-level entities with their unique names, settings to
  set and restore, ids kept for clean-up, the account).
- What stays manual, and which assertion is expected red because of a filed / drafted bug.

### Decisions

**Channel** - where the actor acts and where the result is seen:

| Story is about | Channel | Suite folder / test package |
|---|---|---|
| a web page, one role | web | `suiteFiles/web`, `ui.<area>` |
| an app screen, one role | mob | `suiteFiles/mob`, `mob.<area>` |
| an action on one side and its effect on the other (user requests on the phone → admin approves on the web) | hybrid | `suiteFiles/hybrid` (generic web / mob methods) |
| a rule or calculation with no screen of its own | api (plus the web / mob check when a screen shows it) | `suiteFiles/api`, `apis.<area>` |

Set-up and clean-up - everything that is not what the story tests - go **through the API**, whatever the channel; the
recipes are in **api-catalog**. A UI step is never used as set-up or clean-up. A fresh account of a suite group gets
its base data built by the suite's set-up blocks and removed by its clean-up blocks.

**Placement - extend first, new suite only when it does not fit.** Find the suites on the same area (grep the area's
class or page in `suiteFiles`, the framework skill's *Every suite* table, `bugcycle/suite-groups.json`). **Extend** an
existing suite when all hold:
- same channel (or the suite is already hybrid) and the same feature / screen;
- the new blocks can use that suite's data as it is, before its clean-up block, **without changing** any existing
  block's inputs or expected values;
- the suite stays readable (about ≤ 16 blocks) and does not start depending on a setting it did not touch before.

Mark inserted blocks with `<!-- Story <id>: ... -->` and renumber the block comments. Otherwise a **new suite**, end
to end on its own data (set-up blocks → the story's blocks → clean-up blocks), named after the **business feature**
with the channel prefix - never the story number. Add it to its group in `bugcycle/suite-groups.json` and to the
framework skill's tables.

**How many users:** one → the standard create / delete blocks. Several (requester + approver, two to compare) → one
create block per user, each under its own `RunContext` key; a relation between them is set **by API** in set-up; the
clean-up deletes them all. None (settings / master-data screens only) → still create and delete whatever the test
writes; read-only checks may use existing data but never change it.

**Settings**: set the value the case needs in set-up (by API once that call is verified), restore the **original**
value in clean-up, and put the CI job in the "one at a time" category. Account-wide settings belong on the suite
group's **own account**, not on the shared one; a new group → ask the user for its account.

## Phase 5 - Implement

Framework skill rules, all of them: a new branch from the latest main (check `git branch --show-current` before and
after every run when the checkout is shared), locators verified by hand (phase 2), named assertions, every parameter
`@Optional` with a default, API set-up and clean-up, working on **today**. Commit as soon as it compiles.

## Phase 6 - Execute locally on the test env until green

- `mvn test -Dtestng=<suite> -DserverType=<test env> -DbrowserType=headlessChrome` (mob / hybrid: the device
  connected). An extended suite runs **whole**, so the old blocks prove they still pass.
- After **each** run: the run report table in the reply (`node jenkins/run-report.js "<env> (local)"`) + one
  root-cause line per red from the screenshot / log.
- A red is either a **test problem** → fix and run again, or a **product bug** (confirmed by hand and by the API
  answer) → keep the assertion red, draft the bug, list it to the user. "Green" for this phase = every block passes
  except the ones red for a listed product bug.
- Leftovers: a stopped run's data is printed by its set-up block - delete it by API right away. After every run (green
  or red) check through the list calls that nothing with the run's tag is left, and say so in the report.

## Phase 7 - Deliver and run in CI

The framework's *Delivery workflow*:
1. Commit (code, suite XML, `testingActivity/stories/<id>/*.md`, the updated skills) and push the branch.
2. Pull request into main: title `Story <id>: <title> - <suite>`, the full description in the create call (story link,
   rules covered, placement decision, the local run table, bugs found, what is manual), linked to the story.
3. Merge in a separate step. Refused → say so, the user merges, go on.
4. CI: the suite's job must exist. **New suite → create the job** as a copy of a sibling, changing only the suite
   name, report folders and description; add the "one at a time" category when it flips settings or uses the phone.
5. Run it on the merged main; post the run report table with the build number; check the published report shows it.
6. Switch the checkout back to the updated main.

## Phase 8 - Close

- Final updates: **business-knowledge** (rules the automation proved; the story-log row complete: channel, users,
  placement, manual run result, pull request, CI build), **app-testing** (locators), **api-catalog** (every call
  verified or newly seen), **automation-framework** (a new pattern, a new set-up / clean-up block, the new suite's
  row). These ride in the same pull request; a change after the merge goes in a small follow-up.
- Report to the user: the story, criterion coverage (automated / manual / not covered and why), manual run result,
  suite + blocks added, local and CI tables, bugs drafted (waiting for a yes), questions left, then the clickable
  `path:line` list of every change.

## Rules

- Nothing in the tracker is written except the pull request (and a bug after the user's yes). No comments or state
  changes on the story.
- Test env only; never production; other envs only on request.
- Never delete or edit data the run did not create; never the protected accounts; ask before flipping a setting in a
  walk.
- Never type a password or read one out; test users' passwords come from the data file.
- Don't stop between phases to ask "shall I go on?" - stop only for a question from phase 1, a sign-in, a refused
  step, or a destructive surprise.
