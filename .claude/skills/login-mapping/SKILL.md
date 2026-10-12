---
name: login-mapping
description: Which login to use for a task in the app under test - env, account / tenant, user and role, picked automatically from data/testData.json (ENVIRONMENTS, ACCOUNTS, UI.USERS, API.USERS, MOBILE.USERS) - and how to open the web app already logged in (web/webOpenLoggedIn) and drive that same session (tools/browser.js). Use for EVERY task done in the app (check, walk, learn a scenario, verify, reproduce a bug), even when the tester names no user - pick it yourself from "Pick automatically" - and when they say "log in as / open as / use account X / user Y / admin", or a login fails with a missing password.
---

# Login mapping

**Passwords are never written in this skill, in chat, or in any other committed file.** They live in
`data/testData.json` only (keep real values out of git - see *Where passwords live*). This skill maps *which* login
fits a task and *where* its password is. The framework's code types the password into the login page; Claude never
types, prints or asks for one.

## Pick automatically - don't ask

Any task in the app: choose env, account and user from this table, open the app with `web/webOpenLoggedIn`, do the
task, and **say in one line what you picked and why** ("Test, account `<code>`, MAIN_USER - nothing role-specific").
The tester named one → their choice wins.

Fill the right-hand columns once per project (keys of `data/testData.json`, never the passwords):

| The task is about | Env (`ENVIRONMENTS` key) | Account (`ACCOUNTS` CODE) | User (`UI.USERS` / `MOBILE.USERS` key) |
|---|---|---|---|
| Nothing specific (a page, a list, a walk, a check) | Test | `<main account>` | `MAIN_USER` (the main admin) |
| Something only one role can do or see (e.g. a doctor vs a receptionist) | Test | `<main account>` | `<the key of that role's user>` |
| What an end user does or sees (own records, own requests) | Test | `<main account>` | a user the task creates by API and deletes after - never a shared fixture user |
| A suite group that has its own account (settings that change calculations) | Test | `<that group's account>` | its admin |
| A bug ticket | the env in the ticket, else Test | the account in its steps, else the main one | the role in its steps |
| A story / test case | the env of its phase (Test; another env only on request) | the suite group's account, else the main one | the role the story is about |
| "On Stage" / "on every env" | Stage / Test then Stage | as above | as above |
| Production | never - the tester does it | | |

- Pick, don't ask - unless **two rows fit and lead to different roles or accounts**; then one short question.
- Still ask before anything irreversible in the opened session (delete, approve / reject, save a setting, send).
- A test env that is known to be unreliable at some hours (**app-testing** → quirks): say so if a result looks off.

## Where passwords live

| Login | Where in `data/testData.json` |
|---|---|
| Web users (by key) | `UI.USERS.<KEY>.PASSWORD` (`USERNAME`, else `EMAIL`, is the login name) |
| API users | `API.USERS.<KEY>.PASSWORD` |
| Phone users | `MOBILE.USERS.<KEY>.PASSWORD` |
| Admin of an account / tenant | `ACCOUNTS[] { CODE, ENV, ADMIN, PASSWORD }` (ENV "" = every env) |
| Users a run creates | the project's default test password (where the create-by-API block takes it from) |

- A login with **no password in the file** → the run stops and names the key to fill. Ask the tester to fill it in
  `data/testData.json` themselves; never ask them to paste it in chat, never write one into a file yourself.
- Real passwords stay local: keep the committed `data/testData.json` empty of them (the public template), or give a
  local file with `-DdataFile=data/local.json` that is gitignored.

## Open the web app logged in, then work on it

One-time set-up per project: fill `UI.LOGIN` in `data/testData.json` - the login page's path and four locators (XPath
when they start with `/` or `(`, CSS otherwise):

```json
"LOGIN": {
  "PATH": "/login",
  "USERNAME_FIELD": "input[name='email']",
  "PASSWORD_FIELD": "input[type='password']",
  "SUBMIT": "//button[@type='submit']",
  "LOGGED_IN_CHECK": "<an element shown only after a good login>"
}
```

Then:

```bash
mvn test -Dtestng=web/webOpenLoggedIn -DserverType=<Test|Stage> [-Duser=<UI.USERS key>] [-Dpage=/<path>]
MSYS_NO_PATHCONV=1 node tools/browser.js url | goto /path | text [sel] | click <sel> | type <sel> <text> | key Enter | wait <sel> | shot [file] | eval "<js>" | close
```

| Ask | Parameters |
|---|---|
| "open it" / nothing role-specific | none (MAIN_USER on Test) |
| "as the `<role>`" | `-Duser=<that role's UI.USERS key>` |
| "on Stage, the orders page" | `-DserverType=Stage -Dpage=/orders` |

- `web/webOpenLoggedIn` opens headed Chrome on the first free DevTools port from 9333, logs in through the login page
  (the code types the password), optionally opens `-Dpage`, writes `target/open-browser.json` (port, env, user, url -
  no secret) and **leaves the browser open**. Its driver is stopped; the browser stays.
- `tools/browser.js` (Node 22+, no packages) drives that browser over DevTools: the session stays logged in between
  commands, so **do not close and re-open per step**. Its `eval` refuses expressions that read cookies, tokens or
  passwords.
- Git Bash: `MSYS_NO_PATHCONV=1` before `node tools/browser.js` (else `/path` turns into a Windows path); never for mvn.
- **Two actors** (one creates, the other sees): two browsers - `-DdebugPort=9333` for the first, `-DdebugPort=9334`
  for the second (`-Duser=<other key>`), then `node tools/browser.js --port=9334 ...` for the second one.
- A suite can run **inside** that open browser: `-DattachBrowser=127.0.0.1:<port>` (headed Chrome / Edge); at the end
  only its driver session ends, the browser stays logged in.
- Close with `node tools/browser.js close` (also stops leftover drivers); `node tools/browser.js cleanup` only stops
  leftover drivers.
- The app shows only what the logged-in role may open: if a menu is missing, check the role before calling it a bug.

### Walk an unknown scenario in the same session
When a scenario is not in **business-knowledge** `scenarios.md`, this is how it is learned: open logged in as above,
then keep driving **that** browser with `browser.js` until the scenario ends; note the real texts and values, and the
API calls each step makes (`node tools/browser.js eval "performance.getEntriesByType('resource').map(e => e.name)"`,
or the network panel / the report's Timeline tab). Two actors = two browsers. End with `browser.js close`, delete the
walk's own data, and write the scenario row (agent **create-test-case**, step 0).

## Open the mobile app logged in (pattern to fill)

The same idea on the phone - fill it once the project has a login screen object:
- A small suite `mob/mobOpenLoggedIn` + a class in `mob/tools` that logs in with the project's own login screen object
  (`screens/<Login>Screen`) through `BaseTests.loginOnPhone(user, uiLogin, loggedInCheck)` - the user picked by
  `-Duser=<MOBILE.USERS key>`, the password from its row - and leaves the app open (no app reset at the end).
- Then drive the phone with `adb` in the same session: `adb exec-out uiautomator dump /dev/tty` (texts, ids, bounds),
  `adb shell input tap <x> <y>` on a bound's centre, `adb shell input text "<ascii>"` (never a password),
  `adb exec-out screencap -p > shot.png`, `adb shell input keyevent BACK`. A `tools/phone.js` wrapper with
  `screen | tap | type | back | swipe | shot | close` is the natural next step.
- Never tap something irreversible (send, pay, delete, change the profile) without the tester's yes.

## Rules

- Don't delete or edit what this task did not create; protected accounts (the main admin, shared fixtures) never.
- Ask before anything irreversible in the opened session.
- A new login → add its row to `data/testData.json` (the tester fills the password), then add it to *Pick
  automatically* if a task kind maps to it.
- **Passwords in the app are typed by the framework, never by the tester and never by Claude**: a case that needs a
  password typed (change password, log in with the new one, wrong current password) is a suite method that reads the
  passwords from `data/testData.json`, run with `mvn test -Dtestng=<suite>`.
- Close the browsers and tabs you opened before you answer; never leave one open "to keep it".
