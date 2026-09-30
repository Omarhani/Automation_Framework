---
name: app-testing
description: Explore and test the application under test in the browser, run tasks in it, and supply verified locators for this project's Selenium / Appium page objects. Holds the app map (environments, login flow, pages, known quirks) that the team fills in per project. Use for any hands-on task, check, bug hunt, or locator request against the app itself, and before automating a scenario (walk it here first).
---

# App under test: tasks, exploration, verified locators

This skill has two modes:
- **Mode A: tasks in the app.** The user says "do X in the app" (add a record, delete one, check a filter, reproduce a
  bug). Run it in the browser with the **Task recipes** below, verify it, and report in one or two lines.
- **Mode B: automation.** The user needs locators, page objects or a test. Take mapped locators from the **App map**;
  for anything not mapped yet, follow **xpath-locators**, then write the Java with **automation-framework**.

Save tokens by using the stored locators, helpers and recipes. Don't re-explore the DOM unless a locator fails. When
one fails, or the user sends you to a page that isn't mapped yet, map only what the task needs, finish the task, then
**offer to add the new locators and facts to the App map below** (propose the edit).

> **Fill in the App map for your project.** Everything under "App map" is a placeholder. Keep entries short, mark
> each with the date it was verified, and remove facts that stop being true.

---

## App map

### Environments

| Env | Web URL | API base | Notes |
|---|---|---|---|
| **Test** (default) | `<https://test.your-app.example>` | `<https://api.test.your-app.example/>` | |
| Stage | `<...>` | `<...>` | |
| Production | `<...>` | | **Never test here** unless the user explicitly says so. |

These match `ENVIRONMENTS` in `data/testData.json` (the suite's `server` parameter picks one). Use the default env
unless the user names another; if a task could apply to more than one env, ask. Write the env name and URL in every
report.

### Stack

`<framework and version, component libraries (e.g. Angular Material, MUI), toast library, dialog library, UI languages / RTL>`

### Login flow

`<steps: e.g. tenant / account code -> Next -> username + password -> OTP?>`

- Claude **never types passwords**. If the app is on its login page, stop and ask the user to sign in (their browser
  session is then reused).
- Where the app keeps its session: `<localStorage keys / cookies>` - this is what `UtilsTests.saveSession()` replays.
- How to check which user / role is signed in without exposing tokens: `<e.g. read localStorage.USER and return only UserName / role flags>`.

### Browser profiles / roles

| Profile | Role | Signed-in user | How to recognise it |
|---|---|---|---|
| `<profile 1>` | `<role>` | `<user>` | `<device id / check>` |

Verify the role before acting. Pick the profile whose role the task needs; if unclear, ask.

### Pages

| Page (as labelled in the UI, every language) | URL | Mapped? | Notes |
|---|---|---|---|
| `<Dashboard>` | `/dashboard` | no | |
| `<Users>` | `/users` | no | |

Per mapped page, add a section:

```
### Page: <name> (`/path`)
| Element | Locator | Verified |
|---|---|---|
| Header | //h1[normalize-space()='...'] | 1 match, <date> |
| Add button | ... | |
| Row by key | //table/tbody/tr[td[N][normalize-space()='KEY']] | |
Gotchas: <fields that appear after their dialog, filters that need a refresh click, toasts that don't show ...>
```

### Global locators

| Element | Locator |
|---|---|
| Toast | `<...>` |
| Confirm dialog OK / Cancel | `<...>` |
| Dialog / drawer container | `<...>` |
| Loading spinner / overlay | `<...>` |
| Empty state | `<...>` |
| Language toggle | `<...>` |

### Known quirks and issues

- `<date> - <what happens, where, how to work around it>`

### Test data and safety

- Test data Claude may create for its own checks: `<naming pattern, e.g. "AI Tester" / qa+ai<N>@example.com>` - delete
  it at the end.
- Records that must never be touched: `<shared fixtures, admin accounts>`.
- Set-up and clean-up of test data go through the API, not the UI (**automation-framework** rule); the calls are in
  **api-catalog**. How to create / delete a throwaway user here: `<the project's create-by-API and delete-by-API suites or jobs>`.

### Accounts and suite groups

| Account / tenant | Admin (from `data/testData.json`) | Used for |
|---|---|---|
| `<main>` | `<ACCOUNTS / UI.USERS key>` | most suites; **no account-wide setting is changed here** |
| `<one per group of suites that change account-wide settings>` | | `<the group>`; its base data is built by the suites' own set-up |

Which switches need a higher role (super admin) and where they are: `<...>`.

### Project rules of the road

- Which design / theme / language the automation runs on, and how to check and set it before a walk: `<e.g. a localStorage flag>`.
- Hours or conditions when an env is unreliable: `<e.g. "the test env is slow in the evening and emails arrive late - re-check odd results the next morning">`.
- Devices: `<phone model / serial, USB vs Wi-Fi, emulator limits (e.g. the app blocks some actions on emulators)>`.
- CI: `<where it runs, how it is reached, which jobs must not overlap>`.
- What a walk shows about the API (a new call, a body, a refusal) is recorded in **api-catalog**; what it shows about
  the business (a rule, a calculation) in **business-knowledge** - not here.

### Standard smoke test

1. `<open page, check global locators with __xc -> ALL OK>`
2. `<create a record, expect toast / count +1>`
3. `<find it, check its row>`
4. `<delete it, expect empty state>`

---

## Working method (any app)

### Browser setup
- Use the browser tools available (Claude in Chrome, or the built-in browser pane). If this session has none, say so
  and help with code and locators instead.
- Open a new tab and navigate straight to the page URL. Reuse an existing tab of the app if there is one.
- **A tab that stops responding is not a reason to stop.** Symptoms: script / screenshot time-outs. Open a new tab on
  the same URL (the session is shared, no new login), paste the helpers again, continue from the last confirmed step.
  Stop only if the new tab also fails or lands on the login page.

### Token-saving rules
1. **Don't take a screenshot by default.** Verify with JS and `document.evaluate` on the locators. Screenshot only for
   visual checks or when something is unexpected.
2. Chain actions with the browser's batch tool. Keep JS return values short (under ~900 characters).
3. Some browser tools block output that looks like a query string or a secret. Don't return raw HTML, tokens or
   `key=value&...`; return only what you need.
4. Paste these helpers once per page load, then call them:
```js
window.__X=x=>document.evaluate(x,document,null,9,null).singleNodeValue;
window.__xc=m=>Object.entries(m).map(([k,x])=>{const n=document.evaluate(x,document,null,7,null).snapshotLength;return (n===1?'OK ':n?'MULTI'+n+' ':'ZERO ')+k}).filter(s=>!s.startsWith('OK')).join('\n')||'ALL OK';
window.__w=ms=>new Promise(r=>setTimeout(r,ms));
window.__set=(x,v)=>{const e=__X(x);const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('blur'));};
window.__size=x=>{const e=__X(x);if(!e)return 'ZERO';const r=e.getBoundingClientRect();const h=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);return Math.round(r.width)+'x'+Math.round(r.height)+' hits '+(h?h.tagName:'-');};
```
   `__xc({name: xpath, ...})` checks many locators at once (1 match each). `__set` fills framework-bound inputs
   (React / Angular / Vue listen to the `input` event). `__size` gives a click target's size and what a real click at
   its centre would hit.
5. Generic gotchas:
   - A JS `.click()` on a custom dropdown host often doesn't open it; click its trigger or use a real click.
   - Setting a search box's value with JS usually does **not** filter. Type for real and press Enter.
   - `Escape` may close the whole dialog, not just an open dropdown panel.
   - Browser autofill can overwrite fields after you set them - clear and re-check before saving.
   - Closed modals often stay in the DOM in a hidden state; wait for invisibility, not removal.
   - Dialogs / drawers animate, and their fields or edit data can appear after the container.

### Safety
- A task that names the record and the action ("delete order 1042") is permission for that one action. Find the
  record, check that **exactly one** matches, then do it. With 0 or several matches, or a vague target ("delete the
  old ones"), list what you found and ask.
- Always ask before bulk actions, anything that emails / notifies real people, approvals, downloads, and any action on
  a record the user didn't name.
- Never type passwords; never act on production unless told to.

### Reporting a task
Say what was done, to which record, and how it was verified ("Deleted order 1042; search now shows no results").
Mention any bug or odd behaviour. Screenshot only on failure or when asked.

### Mapping a new page
1. Open it from the app's own navigation (some routes refuse a typed URL but open from the menu).
2. List the elements the task needs; build each locator with **xpath-locators** (anchor priority, scope, every UI
   language).
3. Check them in one `__xc` call, in each UI language and in the state the test will be in (dialog open, row present).
4. For click targets, `__size`: non-zero size and the hit element is the control (or its label).
5. Note timing: anything that appears *after* its container, filters that need an explicit refresh, toasts that come
   and go around a reload.
6. Offer the new rows for the App map.

### Before automating a scenario: walk it here first
Perform every step of the scenario by hand in the browser, on the env and account the suite will use, before writing
any Java:
1. Do the step as a user would.
2. Record the **real** values it produces (toast texts, figures, statuses, durations) and use those as the suite's
   expected values - never from translations files, old reports or assumptions.
3. Check the locator of every element touched: 1 match, non-zero size, `elementFromPoint` reaches it.
4. Watch the timing and put the wait on the element that appears last, not on its container.
5. Clean up what the walkthrough created, or say exactly what was left behind.

Then write the test (**automation-framework**), run it, and report what was verified by hand and what was not. If a
step cannot be exercised (needs a password, writes to shared settings), say so and ask instead of writing it as a guess.

For mobile, walk it on the phone with `adb`: `shell input tap/text/keyevent`, `exec-out screencap -p` for the picture
and `shell uiautomator dump` + `exec-out cat` for the tree (resource-id, content-desc, bounds). In Git Bash set
`MSYS_NO_PATHCONV=1` so `/sdcard/...` is not turned into a Windows path, and redirect `screencap` from Bash, not
PowerShell (which corrupts the PNG).

## Related skills
- **xpath-locators** - writing a new locator, fixing a flaky one, multilingual rules, the Java `By` format.
- **automation-framework** - the Java project: layout, lifecycle, helper API, test data, suites, running, reports.
- **ai-test-run** - running a list of cases by hand with timing, retries, GIFs and an HTML report.
- **business-knowledge** - the rules behind the screens; **api-catalog** - the calls behind them.
- **testing-activity** / agent **create-test-case** - a whole user story, or one test case, end to end.
