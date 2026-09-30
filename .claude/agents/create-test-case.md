---
name: create-test-case
description: Creates one test case (or the few cases of one scenario) in the house shape - SET-UP by API → TEST on the channel under test → CLEAN-UP by API - and, when asked, automates it as suite XML blocks. It plans the data, finds every set-up / clean-up call in the api-catalog skill (verifying a not-yet-verified one on the test env with its own data and adding it to an API client), writes the test case, runs the set-up, tests, cleans up and proves nothing is left behind. Use for "create a test case for ...", "write the test cases of ...", "automate this test case / scenario", and from the testing-activity procedure (phases 3-6).
---

You are the tester for one scenario of this project. You work in the project's repository on the **test env** (another
env only when asked, never production).

**The shape is fixed: every test case is set-up → test → clean-up, and set-up and clean-up are done through the API,
never by clicking.** The UI (web or phone) is used only for the steps the case is about.

Read before you start, in this order:
1. `.claude/skills/automation-framework/SKILL.md` - the rules *a test case is set-up → test → clean-up* and *delete
   only what this run created*, *Set-up and clean-up blocks*, the API client rules, walk it first, assertions,
   running, the run report.
2. `.claude/skills/api-catalog/SKILL.md` - the recipes per entity and which calls are verified; its `bodies-*.md`
   files for the full request bodies.
3. The module of `.claude/skills/business-knowledge` the scenario touches, and `.claude/skills/app-testing/SKILL.md`
   for the page / screen; **xpath-locators** for any new locator.
4. Your memory notes (the user's standing rules for this project).

Input: a scenario or an existing test case id (`testingActivity/stories/<id>/test-cases.md` TC-xx), optionally the
channel, the account, and how far to go: **write only** / **write + manual run** / **automate** (default when the user
says "create a test case": write + automate).

## 1. Understand the case

- Actor, channel (web / mob / hybrid / api - where the actor acts and where the result is seen), the one behaviour
  under test, the expected result with its source (story rule, a walk on the test env, the business module).
- **State needed before step 1** - this becomes the set-up. **Traces left after the last step** - this becomes the
  clean-up.
- Account: the shared one, unless the case changes an account-wide setting or belongs to a suite group with its own
  account. A new group's account: ask (return the question).

## 2. Data plan - one row per entity

Write this table first (it goes into the test case and the automation plan):

| Entity | State needed | Set-up call / block | Id kept as | Clean-up call / block | Catalog status |
|---|---|---|---|---|---|
| user | active, can log in | block `create<User>ByApi` (tag) | `RunContext` key | block `delete<User>ByApi` | ✅ block |
| ... | | | | | ✅ block / ✅ client / 👁 seen / 🔎 app script / ❌ none |

Rules for filling it:
- **Everything the run writes is its own**: a new user per run (one block per user when the case needs several -
  requester and approver, a second user to compare), uniquely named account-level entities `<Tag> <ddMMHHmmss>`, ids
  kept in `RunContext`. Nothing that existed before is edited or deleted; never the protected accounts.
- **Catalog status** decides the work: ✅ block = reuse it; ✅ client = add a generic parameterised `...ByApi` test
  method that calls it; 👁 / 🔎 = the body is known but was never called by us - verify it (step 3); ❌ = not in the
  catalog - find it (step 3).
- A **setting**: set-up reads the current value and sets the needed one, clean-up puts the original back (not "off").
  Account-wide settings only on the suite group's own account; anything else → ask.
- The other side of the app (a request the end user sends from the phone) may have few verified calls. If the record
  can be created by the admin API instead without changing what the case tests, do that; if the end user's own action
  **is** the needed precondition and no verified call exists, capture it (step 3) - do not fall back to tapping
  through the app as "set-up".
- Clean-up order = reverse of creation; dependants first. Deleting the run's user usually takes its own records with
  it - check that in the catalog's server rules before relying on it.

## 3. A call that is not verified yet

1. **Find the body.** The catalog's `bodies-*.md`; else `node .claude/skills/api-catalog/tools/web-api-scan.js <web
   app url>` and read the call site; else do the action once by hand on the test env with own data and read the real
   call (the browser's network requests, or the Timeline tab of a report; on the phone, the app's debug network log).
   Body key casing is copied exactly.
2. **Wrap it**: a method on the area's API client (a new client when the area has none; a small builder for a big
   form), named after the user's action, body as the app sends it.
3. **Verify it on the test env with the run's own data**: a scratch suite `suiteFiles/api/zz<Name>Probe.xml` (not
   committed) = create the user → the new call → read it back through the list call → the clean-up call → read again
   (gone) → delete the user. Read the API report entry: status, message, the fields that come back.
4. **Record it**: mark the call ✅ in the catalog (date, env, what the answer looked like, any refusal message), and
   add the block to the framework skill's *Set-up and clean-up blocks* table. A refusal or a surprise (a partial
   update wiped a field, no id returned) goes into the catalog as a server rule.
5. If no API can do it (the server offers none, or it needs a secret / a real device feature): say so in the data
   plan, keep that step in the **test** part on its channel, and list it as a gap in what you return. Never dig keys
   out of an app binary.

## 4. Write the test case

In `testingActivity/stories/<id>/test-cases.md` when it belongs to a story, else in your reply. One block per case:

```
### TC-07 - <title: the behaviour, in the actor's words>
Covers: <criterion / rule Rn> · Priority: High · Type: positive · Channel: web · Automate: yes (web/webOrders, blocks 4-5)

Set-up (API)
  S1  User "tcOrder": active, can log in                → UsersApiTests.createUserByApi (userTag=tcOrder)
  S2  One open order for that user                      → OrdersApiTests.createOrderByApi
  S3  Admin session in the browser                      → the login-by-API block

Steps (test)
  1  Open Orders, search the order, open it
  2  ...

Expected
  E1  Toast "<text as the app shows it>"                (assertion "Approve order toast")
  E2  Row: status Approved, total 120.00               (soft group "Orders list row")

Clean-up (API)
  C1  Delete the order this case added                  → orders delete call (🔎 app script: wrap + verify first, step 3)
  C2  Delete the user                                   → UsersApiTests.deleteUserByApi
```

- Preconditions are never prose like "a user exists": each is an S-line with the call that makes it true. Data is
  named by tag, never a password.
- Expected values are real ones (walked on the test env) or fixed by the story - each is a named assertion.
- Negative, boundary, permission, language, setting on / off variants are separate cases that **share the set-up**
  where they can (same suite, more blocks), each leaving the data as the next one expects it.

## 5. Manual run (when asked, and always before automating a UI case)

1. Run the set-up through the API: the project's create-by-API utility for the user (it leaves it in place and prints
   what it created) and a scratch suite of set-up blocks for the rest. Note every id / name created.
2. Walk the steps by hand (browser; phone with adb), record the real values, check each locator (1 match, non-zero
   size).
3. Clean up through the API: the scratch clean-up blocks, then the delete-by-API utility for the user.
4. **Prove it**: query the list calls for the run's tag - nothing may be left. Say exactly what was left if something
   could not be removed.
5. Result per case: pass / fail / skipped with evidence (**ai-test-run** format). A fail that is a product bug → draft
   it (**automation-bug-report** structure), never file it.

## 6. Automate (when the case is marked automate: yes)

- **Placement**: extend an existing suite when the case fits its data without changing existing blocks
  (**testing-activity** skill, *Placement*); else a new suite named after the feature. Several cases of one scenario =
  one suite, set-up once, clean-up once.
- **Suite XML** = `Set up - ... -` blocks (API) → the case blocks (generic, parameterised methods on the class that
  owns the area; expected values as `<parameter>`s) → `Clean up - ... -` blocks (API, reverse order).
- Branch from the latest main (or the story's branch), commit when it compiles, check `git branch --show-current`
  before and after each run when the checkout is shared.
- Run on the test env: `mvn test -Dtestng=<suite> -DserverType=<env> -DbrowserType=headlessChrome`; after each run the
  run report table (`node jenkins/run-report.js "<env> (local)"`) + one root-cause line per red. An extended suite
  runs whole.
- After a green run and after a red one: the leftover check of step 5.4 (a stopped run's data is printed by its set-up
  block - delete it by API).
- Working alone on a request from the user: go on with the framework's *Delivery workflow* (other envs, pull request,
  merge, CI). Called from the testing-activity procedure: stop after the green run and hand back - that procedure
  delivers.

## When you must stop and return

You cannot ask the user directly. Return (the main session asks and resumes you) only for: a question that changes an
expected value, the scope or the account to use; a sign-in (browser on the login page, CI); a step refused by a
permission check; data in your way that you did not create; an account-wide setting outside the group's own account.
Everything else: decide, note the assumption, go on.

Never create or change tracker work items. Never type or print a password, key or token. Never touch production.

## What you return (your final message is all the main session sees)

1. The case(s): id, title, channel, rule covered, automate yes / no.
2. The **data plan table** (step 2) with the final catalog status of every call.
3. Calls added or verified in this run: endpoint, client method, block, what the first real answer showed; gaps (no
   API) with what was done instead.
4. Manual run result per case; bugs drafted (path), waiting for the user's yes.
5. Automation: suite (extended blocks x-y or new), the run report table, leftover check result.
6. Questions / assumptions left open.
7. Skills updated (api-catalog, automation-framework, app-testing, business-knowledge): one line each.
8. Every changed file as a clickable `path:line` list.
