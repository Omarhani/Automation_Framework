---
name: api-catalog
description: Every API call the automation can use for set-up and clean-up of the application under test - the admin / web API and the mobile API - with its request body and whether it is verified in code or only read from the app. Use when a test case or suite needs data created, changed or removed by API, when adding a method to an API client, when a walk or a run shows a new call, and to build or refresh the catalog from the web app's own script (tools/web-api-scan.js). Holds the per-project catalog the team fills in.
---

# API catalog - what set-up and clean-up can call

The house rule is: **a test case is set-up → test → clean-up, and set-up / clean-up go through the API**
(**automation-framework**). This skill is the list of calls that makes that possible. The framework skill says how the
Java side is built (`apis/services` clients, `RunContext`, suite blocks); the **create-test-case** agent uses this
catalog to plan a case's data.

> **Fill the catalog in for your project.** Everything in `<...>` is a placeholder. Keep entries short, date them, and
> change a mark the day a call is verified.

## Status of a call - read this first

| Mark | Meaning | What you may do |
|---|---|---|
| ✅ | **Verified**: wrapped in an API client, run green with the run's own data | use the client method / suite block |
| 👁 | **Seen live** on the wire (browser network tab, a report's Timeline, the app's debug log) but not wrapped | wrap it, run it once with own data, then mark ✅ |
| 🔎 | **Read from the app's script** (`bodies-*.md`): endpoint + body as the app builds it, never called by us | wrap it, **verify on the test env with the run's own data** (below), record the real answer here, then mark ✅ |
| ❌ | no call known / none exists | keep that step on its UI channel, or capture the call first |

A 🔎 body is the app's code, not a promise about the server: create calls often return no id (find it through the list
call), a refusal may arrive as HTTP 200 with a failure flag and a message, and "does a partial body wipe the other
fields?" is unknown until tried. **Never build a suite on a 🔎 call without the verification run.**

## How to call

| Side | Client | Base URL | Headers | Body conventions |
|---|---|---|---|---|
| Admin / web API (what the web app calls) | `<apis.services.* via SpecFactory.getSpec>` | `ENVIRONMENTS.<env>.API_BASE_URI` | `<auth header, fixed headers from API.HEADERS, anything computed per call>` | `<wrapper keys, paging keys, fields an interceptor adds; note that key casing is per endpoint - copy it exactly>` |
| Mobile API (what the app calls) | `<... via SpecFactory.getMobileSpec>` | `ENVIRONMENTS.<env>.MOBILE_API_BASE_URI` | `<...>` | `<envelope the app wraps every body in>` |

Answer shape: `<e.g. {data, success, message}; what a refusal looks like>`. Login: `<the calls of the login, in order;
what is encoded; one-time codes; what collides when two runs log in at once>`.

## Set-up / clean-up recipes

One row per entity a scenario may need. "Find id" matters when the create call returns none.

| Entity | Create / set | Find id / read | Clean-up | Status - where |
|---|---|---|---|---|
| Admin session (API + browser) | `<login calls>` | - | - | `<✅ block ...>` |
| `<User the run owns>` | `<call(s) + body>` | `<list call, filter by the unique name>` | `<delete call>` | `<✅ client.method, blocks create…ByApi / delete…ByApi>` |
| `<Master data: e.g. department, location, plan>` | | | | `<🔎 bodies-<area>.md>` |
| `<A record that must exist: request, order, movement>` | | | | |
| `<The same record created from the other side (mobile)>` | | | | `<❌ not captured>` |
| `<A setting in a known state>` | `<read current → change one field → save; does the save need the whole object?>` | `<the read call>` | `<post the original back>` | |

**Reading results for assertions** (no clean-up): `<report / list calls a test reads to assert on, with the fields that carry the figures>`.

## Verified calls (✅) - endpoint → Java

| Endpoint | Client method | Notes |
|---|---|---|
| `<POST users>` | `<UsersApi.create(name)>` | `<what the answer holds>` |

## What the server taught us (rules that are not in any body)

Write here what only a real call shows - each with its date:

- `<uniqueness and case rules: "user names are stored lower case; the duplicate check is case sensitive">`
- `<refusals and their exact messages: "delete refused while children exist: <message>">`
- `<dates and times: format, time zone, "today at a past time is refused">`
- `<what is applied on save vs on read>`
- `<calls that add vs replace; calls that are queued and finish later>`
- `<status numbers per area - they often differ between areas>`

## Gaps - say so instead of guessing

- `<entities with no create call; calls only one side (mobile) has; endpoints that exist as constants but are never called>`
- Where a missing call can be captured: do the action once by hand on the test env with own data and read it from the
  browser's network tab or the Timeline tab of a test report; for a mobile app, the debug build's network log.
- Do **not** dig secrets or signing keys out of an app binary to make a call work - ask the owning team for them, or
  for the check to be switched off on the test env, and record the blocker here.

## Adding or verifying a call - the only way a 🔎 becomes ✅

1. Body from `bodies-*.md` (or a fresh read: next section). Copy the key casing.
2. Method on the area's API client, named after the user's action; a big form gets a small builder with the form's
   defaults. Ids go into `RunContext`.
3. Generic `...ByApi` test method (parameterised; acts only on what this run created).
4. Scratch suite `suiteFiles/api/zz<Name>Probe.xml` (not committed) on the **test env**: create the run's user → the
   call → read it back → the clean-up call → read again (gone) → delete the user. Account-level writes: unique names
   `<Tag> <ddMMHHmmss>`; settings only on the suite group's own account.
5. Record here: the row's mark → ✅ with the date, the real answer (what the data holds, the refusal text), and add
   the block to the framework skill's *Set-up and clean-up blocks* table. Then the next env.

## Building / refreshing the catalog from the web app

```bash
node .claude/skills/api-catalog/tools/web-api-scan.js <web app url on the test env>
```

Fetches only the app's static script files (no login, no API call) into `<temp>/web-api-scan`: `endpoints.md` (every
endpoint by controller - copy it here as `endpoints.md`; on a refresh, a new line = a new endpoint), `calls/<Controller>.md`
(every call site with the body as written), `no-call-site.txt` (constants the app never calls). It understands a
webpack / Angular-CLI style bundle with endpoint constants; for another build adjust its two patterns, or collect the
calls from the network tab.

Turning `calls/` into `bodies-<area>.md`: one block per endpoint - the UI action that triggers it, the full body with
exact key names and casing (follow a body that is a variable or a form value back to where it is built; list enum
values), what the code reads from the answer (that tells a test how to find the id), client-side rules. Mark each fact
`[R]` read in the code or `[I]` inferred. Never guess; write "not determined" and why. Reading several areas at once is
a good job for parallel sub-agents, one per group of controllers, each writing one file; review what they return
against a call you know.

Run the scan after each release of the app, or when a walk shows a call that is not here.

## Files of this skill

| File | Holds |
|---|---|
| `endpoints.md` | every endpoint name by controller, with the date and build it was read from |
| `bodies-<area>.md` | request bodies per area, read from the app's script (`[R]` / `[I]`) |
| `tools/web-api-scan.js` | the scan script |

Never write a password, key, token or one-time code into this skill - header **names** only. Secrets stay in the data
file / environment variables.
