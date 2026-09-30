---
name: automation-framework
description: The project's Java test-automation framework - Maven + TestNG + Selenium + Appium + RestAssured + ExtentReports. Use when adding or changing a test, web page object, mobile screen, API client, suite XML or test data (data/testData.json), when running a suite (mvn test -Dtestng=...), when reading a run's report or Jenkins result, or when debugging framework-level problems (driver / Appium setup, groups, parameters, saved logins, reports, Live Runner). Covers the project layout, the BaseTests / BaseApi lifecycle, the MethodHandlesWeb / MethodHandlesMobile helper API, named assertions, the JSON data model, suite conventions, the set-up → test → clean-up-by-API shape of every test case, the delivery workflow (branch → run → PR → CI → report) and Jenkins wiring.
---

# Automation framework

A generic Java 17 test framework: you write **locators and actions**, the framework gives you drivers, test data per
environment, logins, and a full report (steps, recording, network timeline, attachments) for every test.

| Layer | Library |
|---|---|
| Runner | TestNG 7.11 (suites in `suiteFiles/`, run through Maven Surefire) |
| Web | Selenium 4.43 (Chrome / Edge, headed or headless) |
| Mobile | Appium java-client 10 (Android, UiAutomator2; Appium server started in-process) |
| API | RestAssured 5.5.6 |
| Report | ExtentReports 5.1.2 + the framework's own recorders (browser GIF, phone recording, network timeline) |
| Data | Gson -> `data/testData.json` |

Related skills and agents:

| Need | Use |
|---|---|
| write / fix a locator | skill **xpath-locators** |
| the app under test: its map, login, quirks; walk a scenario by hand before automating it | skill **app-testing** |
| the business rules behind the screens (what is expected, and why) | skill **business-knowledge** |
| every API call a set-up or clean-up step can use, and whether it is verified | skill **api-catalog** |
| one test case built as set-up → test → clean-up | agent **create-test-case** |
| one user story end to end (analyse → walk → manual test → automate → PR → CI) | skill + agent **testing-activity** |
| run cases by hand in the browser with a report (not the Java suites) | skill **ai-test-run** |
| run a suite group with retries, confirm real bugs, retest after the fix | skill **suite-bug-cycle** + agent **suite-bug-runner** |
| turn a red that is a product bug into a developer-ready bug | skill **automation-bug-report** |

Project facts are **not** in this skill: URLs, users and devices live in `data/testData.json`; the app map in
**app-testing**; business rules in **business-knowledge**; endpoints in **api-catalog**; tracker and retest settings in
`bugcycle/config.json`.

## Working rule: walk it first, then write it

Before writing a test for a scenario, perform it by hand once (browser for web - see **app-testing**; `adb` +
`uiautomator dump` for mobile), on the env the suite targets. Take the expected values (toast texts, figures,
statuses) from what the app really shows, and verify every locator (1 match, non-zero size, clickable centre - see
**xpath-locators**). Compile, run the suite, read the report. If something cannot be exercised (needs a password you
don't have, writes to shared settings), say so and ask instead of shipping a guess.

## Rule: a test case is set-up → test → clean-up, and set-up / clean-up go through the API

Every test case (manual or automated) and every suite has three parts, in this order:

| Part | How | What |
|---|---|---|
| **Set-up** | **API only** (clients in `apis/services`, as suite blocks) | everything the scenario needs but does not test: the user(s) it acts as or on, the admin session, master data (the entities the screen lists or links to), a record that must already exist, a setting in a known state |
| **Test** | the channel under test (web, phone, or API when the rule has no screen) | only the steps the case is about, with named assertions |
| **Clean-up** | **API only**, last block(s), reverse order of creation | every entity the run created (by the ids it kept in `RunContext`), and every setting put back to the value it had |

- The UI never does set-up or clean-up, unless that UI step **is** the thing under test (a "create from the web" case
  tests the create form itself - its delete is still API).
- Clean-up is its own `<test>` block, so it runs after a failed block, shows in the report with its API calls, and
  fails naming anything it could not delete. An `@AfterSuite` clean-up would hide all of that.
- A set-up entity with no API client yet: look the call up in **api-catalog**, add it to the area's client
  (*Adding an API client*), verify it once on the test env with the run's own data, and record it in the catalog as
  verified. Never click it "just this once".
- Prefer one **user / tenant record per run** as the root of the run's data: what hangs on it (its requests,
  transactions, assignments) usually goes with it when it is deleted, so one clean-up call is enough. Shared, account-level
  entities (master data, settings, roles) need their own clean-up.
- Suites that change **account-wide settings** get their own account / tenant (one per group of similar suites), so they
  cannot change what the other suites calculate. Ask the user for it; do not flip shared settings on the main account.
- The ready blocks of the project are listed under *Set-up and clean-up blocks*.

## Rule: delete only what this run created

**Never delete, edit or overwrite anything the run did not create itself** - no user, record, master data or setting
that existed before, on any env.

- A clean-up acts only on ids the run kept (`RunContext`), or on exact unique names it generated
  (`<Tag> <ddMMHHmmss>`). Never look an entity up by a fixed or pattern name and delete it, never "clean up leftovers"
  by pattern, never loop-delete a list.
- A delete block that takes an id / name as a parameter refuses one this run did not create - except in a standalone
  utility suite a person starts on purpose for one named item. Protected accounts (the admin the suites log in as,
  fixtures other suites depend on) are refused everywhere; keep that guard in the client.
- Probes and walkthroughs: uniquely named data, delete exactly that, then check through a list call that nothing with
  the tag is left.
- Something the run did not create is in the way? **Stop and ask**, do not delete it.

## Delivery workflow - every change, every time

A change is done only when all of these are done, in this order, without stopping half-way to ask:

1. **Branch** from the latest main branch (`git switch <main> && git pull --ff-only && git switch -c <topic>`). Never
   work on the main branch. If the checkout is shared (other sessions, scheduled jobs), check
   `git branch --show-current` before and after every run, commit as soon as the change compiles, and do side work in
   its own `git worktree`.
2. **Build** it the house way: set-up and clean-up by API, the run's own data, named assertions, expected values in the
   suite XML, working on **today's** date (no pinned or rolling days unless the scenario needs a shared day).
3. **Run locally on every env the suite targets**, first the test env: `mvn test -Dtestng=<suite> -DserverType=<Env>
   -DbrowserType=headlessChrome`. Fix until green. Read the report and the screenshots, not only the counts. After
   **each** run post the run report (below); the next env starts only when the first is green.
4. **Commit, push, open the pull request** into the main branch. Its description says what changed, what ran on which
   env, and what did not run. Put the full text in the create call.
5. **Merge** in a separate step. If a permission check or branch policy blocks it, say so and leave that one step to
   the user.
6. **CI job**: make sure the suite has a job (copy a sibling job and change only the suite name, report folders and
   description). A new suite also goes into `bugcycle/suite-groups.json`.
7. **Run it in CI on every env**, on the merged main branch. Check the result and that the published report shows
   that run.
8. **Report "done"**: a table suite × env with pass / fail and build numbers, then every changed file as a clickable
   `path:line` list.

When the user says "run the suites", that means **queue the CI jobs**; a local `mvn` is for developing a change.

### Run report - after every run, every time

```bash
node jenkins/run-report.js "Test (local)"          # or "Stage (local)", "Test - CI #12" <path to testng-results.xml>
```

It reads `target/surefire-reports/testng-results.xml` and prints passed / failed / skipped with percentages, the run
time, then one row per `<test>` block: result, time and **why it failed** (the assertion's message, or the exception
and the project line it failed on). A block whose set-up failed shows as SKIP with the set-up's error.

**Write the table into the reply itself** as a markdown table - the user does not see tool output. Header line = env,
passed / failed / skipped with %, time; then `| # | Block | Result | Time | Why it failed |`. Then one line per failure
with its **root cause** (read the screenshot `report/<env>Env/<suite>/<n>_<method>.png` and the log, not only the
message) and whether later failures are knock-on effects of the first. Then:
- all green → run the next env straight away, same report after it;
- red → it is either a **test problem** (fix, run again) or a **product bug** (keep the assertion red, triage it with
  **automation-bug-report**; nothing is filed without the user's yes).

A stopped run keeps its data: delete it by API right away (the set-up block printed what it created).

## Layout

```
pom.xml                          surefire -> suiteFiles/${testng}.xml; UTF-8 argLine
data/testData.json               all test data, per environment (Gson -> src/main/java/data)
data/testDate.txt, workDate*.txt rolling dates (gitignored, created on first use)
suiteFiles/web|mob|api|hybrid/   TestNG suites; *Regression.xml compose the smaller ones
report/<env>Env/<suite>/         the Extent report of each run (gitignored)
jenkins/                         liveRunner.html, jobForm.html, job-form-button.js, run-report.js (also a module)
Jenkinsfile                      pipeline: SUITE / ENV / BROWSER -> mvn test, publishes "<ENV> Report"
bugcycle/                        suite groups with retries, bug registry, retest + tracker moves (skill suite-bug-cycle)
testingActivity/story.js         reads a user story and its tickets into testingActivity/stories/<id>/ (skill testing-activity)
.claude/skills, .claude/agents   the skills and agents (tracked; a knowledge update is a change like any other)

src/main/java/
  pages/*Page.java               web page objects        extends utils.MethodHandlesWeb
  screens/*Screen.java           Android screens         extends utils.MethodHandlesMobile
  utils/MethodHandlesWeb.java    web helpers: waits, clicks, typing, step logging, myAssert*
  utils/MethodHandlesMobile.java mobile helpers
  utils/ReportedAssertions.java  the engine behind myAssert* / softly: numbered, named assertions in report, log, failure
  utils/EmailReader.java         reads the app's emails over IMAP (OTP, passwords, links)
  data/*.java                    POJOs of testData.json (DataModel, Environment, UI, API, MOBILE, EMAIL, Account, User)
  reader/ReadDataFromJson.java   dataModel() entry point

src/test/java/
  base/BaseTests.java            web + mobile lifecycle, driver start, page / screen wiring
  base/BaseApi.java              API lifecycle (report entry per test, RestAssured reset)
  ui/<feature>/*Tests.java       web tests          (ui/home/HomeTests = smoke example)
  mob/<feature>/*Tests.java      mobile tests       (mob/home/HomeTests = smoke example)
  apis/services/SpecFactory.java request spec every API client starts from
  apis/<area>/*Tests.java        API tests          (apis/health/HealthApiTests = smoke example)
  utils/                         report + recorders: UtilsTests, ReportListener, ApiReport, EmailReport,
                                 RunContext, JobResult, LiveFeed, TestTimeline, NetworkRecorder,
                                 BrowserGifRecorder, MobileScreenRecorder, MobileSession, PhoneConnection
```

The shipped `HomePage` / `HomeScreen` are empty placeholders wired in `BaseTests`; the three smoke tests work for any
app once `ENVIRONMENTS` / `MOBILE` are filled.

## Running

`-Dtestng` is the suite path under `suiteFiles/` **without** `.xml`:

```bash
mvn test -Dtestng=web/webSmoke   -DserverType=Test  -DbrowserType=headlessChrome
mvn test -Dtestng=api/apiSmoke   -DserverType=Stage
mvn test -Dtestng=mob/mobSmoke   -Dudid=<adb serial>
mvn test -Dtestng=hybrid/hybridSmoke -DdataFile=data/other.json
```

| `-D` property | Effect |
|---|---|
| `testng` | suite file (default `web/webSmoke`) |
| `serverType` | env = a key of `ENVIRONMENTS` (`Test`, `Stage`, ...). Reaches the suite through `<parameter name="server" value="${serverType}"/>`; unset / unresolved = `Test` |
| `browserType` | `Chrome`, `Edge`, `headlessChrome`, `headlessEdge` via `<parameter name="browser" value="${browserType}"/>`; unset = Chrome |
| `dataFile` | another data file; wins over the suite's `dataFile` parameter, which wins over `data/testData.json` |
| `udid` | force one phone (serial or `ip:port`); otherwise USB first, then `MOBILE.WIFI_UDID` |
| `network=off` | no DevTools network capture (Timeline shows steps only) |
| `networkNoise=host\.a\|host\.b` | extra monitoring hosts (regex part) to drop from the network capture |
| `recording=full` | browser recording every 300 ms as well as per step (debugging); default = one frame per step |
| `live=on\|off`, `liveDir`, `liveUrl` | Live Runner feed; on by default when `BUILD_NUMBER` is set (Jenkins) |
| `workDateSeed=yyyy-MM-dd` | first day for `workDate="auto"` |
| `dateDir=<folder>` | shared folder for the rolling date files (several Jenkins agents, locked while written) |
| `MAIL_USER`, `MAIL_APP_PASSWORD` | mailbox and its app password for `EmailReader` (also env vars; never in the repo) |

After every run, post the per-block table (`node jenkins/run-report.js "<label>" [path/to/testng-results.xml]`) in the
reply - see *Run report* above.

## Lifecycle - `base/BaseTests.java` and `base/BaseApi.java`

Hooks are **group-scoped**. A web or mobile block gets its driver only when **both** are true:

1. the suite block includes the group: `<groups><run><include name="Web"/></run></groups>` (or `Mob`), and
2. the test methods carry it: `@Test(groups = "Web")` / `@Test(groups = "Mob")`.

API test classes extend `BaseApi`, whose hooks are `alwaysRun = true`: **no group** on API blocks or methods.

**Web (`groups = "Web"`)**

| Hook | Does |
|---|---|
| `@BeforeSuite` `beforeSuiteWeb(server, dataFile)` | picks the data file, report folder, env (`setEnv`), `MethodHandlesWeb.setURL(WEB_URL)`, creates the report, `RunContext.clear()` |
| `@BeforeClass` `setUpWeb(browser)` | starts the browser (1920x1080, 30 s page load) with the newest cached driver, **constructs the page objects** |
| `@BeforeMethod` `goHomeWeb` | opens the numbered report entry, starts the GIF recorder + Timeline, opens `ENVIRONMENTS.<env>.WEB_URL` (one retry on a hung load) |
| `@AfterMethod` `afterMethodWeb` | stops recording, last screenshot, Timeline + Attachments, closes the entry with its result (in `finally`) |
| `@AfterClass` / `@AfterSuite` | `driver.quit()` / flush the report |

**Mobile (`groups = "Mob"`)**

| Hook | Does |
|---|---|
| `@BeforeSuite` `beforeSuiteMob` | data file, report, env, starts Appium on `127.0.0.1:4723` (`--allow-insecure uiautomator2:adb_shell`, log in `target/appium.log`) |
| `@BeforeClass` `setUp` | `AndroidDriver` from `MOBILE` (package / activity; installs `APK_PATH` if the app is missing), phone via `PhoneConnection.udid()`, **constructs the screens** |
| `@BeforeMethod` `goHome` | opens the report entry, restarts the app between tests of one block, starts the phone recording |
| `@AfterMethod` `afterMethodMob` | re-saves the phone login, stops recording, screenshot, closes the entry |
| `@AfterClass` / `@AfterSuite` | resets the keyboard, quits / flushes, stops Appium |

**API (`BaseApi`)**: `@BeforeSuite` data file + report folder + env; `@BeforeMethod` opens a numbered entry (type
API); `@AfterMethod` closes it; `@AfterClass` `RestAssured.reset()`.

Every `<test>` block is a **new test-class instance** (new browser / new Appium session). Anything a later block needs
must be kept in `RunContext` or a saved login - fields do not survive.

Statics are everywhere (`MethodHandlesWeb.test`, `BaseTests.serverType`, saved sessions, `RunContext`): suites are
**single-threaded**; do not turn on TestNG `parallel`.

## Adding a web page

`src/main/java/pages/OrdersPage.java`:

```java
package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import utils.MethodHandlesWeb;

import static reader.ReadDataFromJson.dataModel;

public class OrdersPage extends MethodHandlesWeb {

    public OrdersPage(WebDriver driver) {
        super(driver);
    }

    // --- Locators (see xpath-locators) ---
    private final By header     = By.xpath("//h1[normalize-space()='Orders']");
    private final By addButton  = By.cssSelector("button[data-testid='add-order']");
    private final By nameInput  = By.cssSelector("input[name='name']");
    private final By saveButton = By.xpath("//div[@role='dialog']//button[normalize-space()='Save']");

    private By rowByName(String name) {
        return By.xpath("//table/tbody/tr[td[1][normalize-space()='" + name + "']]");
    }

    // --- Actions: one short method per user action ---
    public void open()                   { openPage(dataModel().UI.url("ORDERS"), header); }
    public void clickAdd()               { click(addButton, 10); }
    public void insertName(String name)  { clear(nameInput, 10); sendKeys(nameInput, 10, name); }
    public void clickSave()              { smartClick(saveButton, 10); }
    public boolean rowIsDisplayed(String name) { return isDisplayed(rowByName(name), 10); }
}
```

Then register it in `BaseTests` - otherwise it stays `null`:

```java
// Page Objects - add your app's pages here
public HomePage homePage;
public OrdersPage ordersPage;                   // 1. field
...
public void setUpWeb(...) {
    ...
    homePage = new HomePage(driver);
    ordersPage = new OrdersPage(driver);        // 2. construct in setUpWeb
}
```

Conventions: `private final By` fields at the top; parameterised locators as `private By x(String)`; action methods
named `clickX` / `insertX` / `selectX` / `getX` / `xIsDisplayed`; timeout `10` for elements, `20-30` for page-level
waits. The helpers log the calling method's name as the report step (`OrdersPage.clickSave`), so name actions for
readers. A multi-step business method (`createOrder(...)`) may chain the small ones and end with the wait that proves it
finished (`waitForToasterToAppearAndDisappear()`).

### Web helper API - `utils/MethodHandlesWeb` (protected unless noted)

Each element helper waits (presence + clickable), scrolls the element to the viewport centre, draws the numbered step
box (recorded in the GIF and Timeline), retries `StaleElementReferenceException` up to 5 times, and logs a step.

| Group | Methods |
|---|---|
| Navigate | `openPage(url, headerBy)` (reloads once if the header does not show in 20 s) · static `getURL()` / `setURL()` |
| Find / wait | `webElement(By)` · `webElements(By)` · `explicitWait(By, time)` · `invisibilityOf(By, time)` (already gone counts) |
| Click | `click(By, time)` · `smartClick(By, time)` (Actions, then JS fallback) · `clickWithActions` · `doubleClick` · `contextClick` · `clickAndHold` · `moveToElement` · `dragAndDrop(src, target, time)` · `release` · `submit` |
| Type | `sendKeys(By, int time, String text)` (checks the value landed, retries once) · public `sendKeysWithDelay(By, text)` (per character, for OTP boxes) · `sendKeysWithEnter` · `sendKeysWithEnterAndEscape` · `clear(By, time)` · `escapeButton()` · public `insertTimeMovement(value, By)` (native value setter + input/change events, for framework-bound inputs) |
| Read | `getText(By, time)` · `getTextViaJS` · `getTextFromDOM` (raw textContent) · `getTextUsingHorizontalScroll` (wide / RTL tables) |
| State | `isDisplayed(By, time)` · `isEnabled` · `isSelected` - these **wait and throw** on a missing element; for "is it there?" use `webElements(by).isEmpty()` |
| Native `<select>` | public `selectByVisibleText / selectByValue / selectByIndex(By, x, time)` - only real `<select>`; custom dropdowns = click, then click the option |
| Scroll / DOM | public `scrollIntoView(By)` · `scrollToElementInModal(By)` · public `hideElement(By)` (a floating widget covering a button) · `highlight(By or WebElement, label)` (step box for a step done without a helper) |
| Toast | fields `toaster` (`#toast-container`) / `toasterContent` (`//div[@role='alert']`) - ngx-toastr defaults, reassign them in a page for another library · public `getToasterContent()` · public `waitForToasterToAppearAndDisappear()` · `waitForToaster(appear, disappear)` |
| Confirm dialog | public `clickAccept()` / `clickReject()` (SweetAlert2 `.swal2-confirm` / `.swal2-cancel`) |
| JS alerts | `acceptAlert` · `dismissAlert` · `sendKeysAlert` · `getTextAlert` |
| Asserts | static `myAssertEquals(name, actual, expected)` · `myAssertContains(name, actualText, part)` · `myAssertTrue(name, condition)` · `myAssertFalse(name, condition)` · `softly(title, () -> {...})` - see *Assertions*. **Always give the name; never raw TestNG asserts** (web, mobile, API tests and page objects alike). |

### Assertions - `utils.ReportedAssertions`

Every assertion goes through `MethodHandlesWeb.myAssert*`, which delegate to `utils.ReportedAssertions`.

- **Name every assertion** with what it checks, first argument, in the style `<screen or answer> · <field>`:
  `myAssertEquals("Order details · total", actual, expected)`, `myAssertTrue("Order left the list", gone)`. An unnamed
  call still works and is called "Assertion at OrdersTests:43".
- **Look-alikes are told apart:** each assertion has a number per test (`#1`, `#2`...); a name used twice in one test
  becomes `name (2)`; an assertion made in a helper (page object, private method) shows the helper line **and** the
  test line that called it: `OrdersPage:74 ← OrdersTests:191`.
- **Report:** one table per assertion `# | Assertion (+ where) | Expected | Actual | Result`. A failed text comparison
  gets a hint: first differing character with its code point, lengths, "same text apart from spaces or invisible
  characters", "differs only in case", "the actual text contains the expected one". Invisible characters are spelled
  out in the values (`⟨NBSP⟩`, `⟨RLM⟩`, `⟨ZWSP⟩`...) - they look identical on screen, above all in RTL apps.
- **Log:** one line per assertion, `ASSERTION #6 Order details · total [block] @ OrdersTests:43 | expected "…" |
  actual "…" | hint`, and an `ASSERTIONS <group>: n of m passed` line per soft group.
- **Failure message** (= the run report's "Why it failed"): `#6 Order details · total: expected [120.00] but found
  [-]`. The bug cycle matches a filed bug to its assertion by this name - **renaming an assertion breaks its retest**
  (update `bugcycle/bug-registry.json` with it).
- **Soft groups:** `softly("Order details of " + id, () -> { ...assertions... })` runs every assertion of the group,
  reports each, adds a summary table, then fails once listing all failed ones. A non-assertion exception (element not
  found) still stops the group. Use it where one screen or one API answer has several fields; keep single gate
  assertions (a toast, "is shown") hard.
- **Contains:** `myAssertContains(name, text, part)` instead of `myAssertTrue(text.contains(...))` - the report shows
  the whole actual text.
- **Language-dependent text:** compare against the expected text of the language shown, not
  `myAssertTrue(a.equals(x) || a.equals(y))`.
- Wording in outputs is **assertion**, not "check".

## Adding a mobile screen

`src/main/java/screens/LoginScreen.java`:

```java
package screens;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import utils.MethodHandlesMobile;

public class LoginScreen extends MethodHandlesMobile {

    public LoginScreen(AndroidDriver androidDriver) {
        super(androidDriver);
    }

    private final By userField   = AppiumBy.xpath("//android.widget.EditText[@resource-id='login_user']");
    private final By passField   = AppiumBy.xpath("//android.widget.EditText[@resource-id='login_password']");
    private final By loginButton = AppiumBy.accessibilityId("Login");
    private final By homeTitle   = AppiumBy.accessibilityId("Home");

    public void insertUser(String user)     { tapField(userField, 10); sendKeys(userField, user, 10); }
    public void insertPassword(String pass) { tapField(passField, 10); sendKeys(passField, pass, 10); hideKeyboardIfShown(); }
    public void clickLogin()                { clickOnce(loginButton, 10); }
    public boolean homeIsShown()            { return isPresent(homeTitle, 20); }
}
```

Register it in `BaseTests`: a field next to `public HomeScreen homeScreen;` and `loginScreen = new LoginScreen(androidDriver);`
in `setUp()`. The driver field of `MethodHandlesMobile` is private: a screen that needs the raw driver keeps its own
reference from the constructor. Tests reach it as `androidDriver` and the app data as `mobile()` (both from `BaseTests`).

### Mobile helper API - `utils/MethodHandlesMobile`

| Group | Methods |
|---|---|
| Tap | `click(By, time)` (re-taps if the screen did not change within 3 s - for plain navigation) · `clickOnce(By, time)` (exactly one tap - **submit / navigate buttons and tabs**) · `tapField(By, time)` (one tap to focus a field) · `smartClick` · `tapPoint(x, y)` (element with no node) · `clickFlutterLeftAsset(By)` |
| Type | `sendKeys(By, String text, int time)` - **argument order differs from web** · `hideKeyboardIfShown()` |
| Wait / state | `explicitWait(By, time, pollMs)` · `isPresent(By, seconds)` (no step, no tap) · `isDisplayed(By, time)` (returns false, never throws) · `waitForScreenToSettle(quietMs, maxSeconds)` (screens that rebuild while loading) |
| Scroll | `scrollAndClick(By)` · `scrollIntoViewWithin(listBy, itemBy, maxSwipes)` / `swipeWithin(rect, revealBelow)` (scroll inside one list, not the screen) |
| Read | `getFlutterText(By)` (content-desc, then text) |
| Toast | field `toasterMessage` (snackbar by resource-id - reassign for your app) · `getToasterMessage()` · `getToasterMessageOnce()` (one page-source read - use for short toasts) · `toasterMessageIsDisplayed()` |
| App | `restartApp()` (force-stop + launch, stays logged in) · `setStep()` |

Use `tapField` before `sendKeys` (not `click`: a field never goes stale, so `click` waits 3 s and taps twice). Use
`clickOnce` for anything that submits or navigates, or a second tap may submit twice. Mobile tests assert with the same
`myAssert*` methods.

## Adding an API client and test

Clients live in `src/test/java/apis/services/`, tests in `src/test/java/apis/<area>/`. Every request starts from
`SpecFactory.getSpec(resource)` (web API = `ENVIRONMENTS.<env>.API_BASE_URI`) or `getMobileSpec(resource)`
(`MOBILE_API_BASE_URI`, falling back to the web one). The spec adds JSON content type, `API.HEADERS` from the data
file, and `ApiReport.FILTER`, so **every call is logged in the running entry** (method, endpoint, status, time, the
server's Status / Message, bodies with secrets masked) - also when called from a web or mobile test.

```java
package apis.services;

import io.restassured.response.Response;
import utils.ApiReport;

import static io.restassured.RestAssured.given;

public final class OrdersApi {
    private OrdersApi() {}

    public static String create(String name) {
        Response r = given(SpecFactory.getSpec("orders"))
                .body(java.util.Map.of("name", name))
                .post();
        String id = r.jsonPath().getString("id");
        ApiReport.step("Created order " + id);        // readable business step in the report
        return id;
    }

    public static void delete(String id) {
        given(SpecFactory.getSpec("orders")).delete("/" + id).then().statusCode(200);
        ApiReport.step("Deleted order " + id);
    }
}
```

```java
package apis.orders;

import apis.services.OrdersApi;
import base.BaseApi;
import org.testng.annotations.Test;
import utils.RunContext;

public class OrdersApiTests extends BaseApi {

    @Test                                          // no groups on API tests
    public void createOrderByApi() {
        RunContext.put("orderId", OrdersApi.create("Order " + System.currentTimeMillis()));
    }

    @Test
    public void deleteOrderByApi() {
        OrdersApi.delete(RunContext.get("orderId"));
        RunContext.remove("orderId");
    }
}
```

Authentication: log in once in the client (e.g. a `token()` method that caches per env) and add the header to the
spec; keep passwords in `API.USERS` or env vars, never in code. `ApiReport` masks any JSON key containing
`password`, `token`, `verificationcode` or `secret`.

Rules for API clients - they are the API's page objects, and what set-up and clean-up are made of:

- **One client per area of the app** (`OrdersApi`, `UsersApi`), methods named after the user's action (`create`,
  `approve`, `delete`), request bodies **copied from what the app itself sends** (**api-catalog**) - same keys, same
  casing, same wrappers. The test class owns only the scenario and the assertions.
- A body with many fields gets a small builder with the form's defaults and setters only for what scenarios change.
- A call that must succeed fails loudly with the endpoint and the server's message (many apps answer HTTP 200 with a
  `success: false` flag - check the flag, not only the status); a negative case reads the refusal's message and
  asserts it.
- **Ids come back into `RunContext`** the moment they are created; when the create call returns no id, find it through
  the list call by the unique name the run generated.
- Generated data is unique per run and recognisable: `<Tag> <ddMMHHmmss>` for names, a stamped address for users.
  Print one line per created / deleted item (`Created user by API: <id>`), so a cut-off run's leftovers can be found
  (`bugcycle/config.json` → `leftovers`).
- A multi-step business flow (sign up → approve → assign) is one static method in a flow class that calls several
  clients and logs each step with `ApiReport.step(...)`.
- An admin login that sends a one-time code collides when two CI builds log in as the same user at once: make the
  login wait and retry on that refusal, and seed browser sessions from the API login instead of the UI login.
- A new call is **verified once with the run's own data** (create → read back → clean up → read again) before a suite
  relies on it, then marked verified in **api-catalog**.

## Suites and parameters (`suiteFiles/`)

```xml
<suite name="Orders">
    <parameter name="server"  value="${serverType}"/>   <!-- env; unset = Test -->
    <parameter name="browser" value="${browserType}"/>  <!-- unset = Chrome -->
    <!-- optional: <parameter name="dataFile" value="data/other.json"/> -->

    <!-- SET-UP (API) -->
    <test name="1. Set up - create the order by API -">  <!-- API block: no <groups> -->
        <classes><class name="apis.orders.OrdersApiTests">
            <methods><include name="createOrderByApi"/></methods>
        </class></classes>
    </test>

    <!-- TEST (the channel under test) -->
    <test name="2. Order shows on the web">
        <parameter name="expectedStatus" value="New"/>
        <groups><run><include name="Web"/></run></groups>
        <classes><class name="ui.orders.OrdersTests">
            <methods><include name="verifyThatTheOrderIsListed"/></methods>
        </class></classes>
    </test>

    <!-- CLEAN-UP (API), reverse order of creation -->
    <test name="3. Clean up - delete the order by API -">
        <classes><class name="apis.orders.OrdersApiTests">
            <methods><include name="deleteOrderByApi"/></methods>
        </class></classes>
    </test>
</suite>
```

- **Three parts, always:** `Set up - ... -` blocks (API) → the scenario's blocks → `Clean up - ... -` blocks (API). The
  names make the run report read as the three parts.
- One `<test>` block per step of the scenario; the block name is the report entry's title, so write it for a reader.
  **A block name is also a key**: the bug cycle finds a filed bug's block by it - renaming a block means updating
  `bugcycle/bug-registry.json`.
- **A scenario composes generic tests - it does not get a class of its own.** Write the step as a parameterised
  method on the class that owns that area and call it from as many blocks as needed. Two methods that differ only by
  data are one method with a parameter (`enable` / `disable` / `restore` = one method taking `enabled`).
- **Extend before adding:** a new case goes into an existing suite when it is the same channel and feature, can use
  that suite's data as it is (before its clean-up block) without changing any existing block's inputs or expected
  values, and the suite stays readable (about 16 blocks). Otherwise a new suite, named after the business feature
  (`web<Feature>.xml`, `mob<Feature>.xml`, `api<Feature>.xml`, `hybrid/<feature>.xml`) - never after a ticket number.
- **Data-driven by `<parameter>`**, not `@DataProvider`: the same method in several blocks with different values.
  Bind with `@Parameters({"a", "b"})` and give **every argument `@Optional(default)`** so the method still runs from
  the IDE or a suite that does not set it. Keep expected values (toast texts, figures) in the XML.
- Regression suites compose others with `<suite-files><suite-file path="webSmoke.xml"/></suite-files>` (path relative
  to the file); each child keeps its own report folder.
- A **hybrid** suite mixes API, `Web` and `Mob` blocks; they share one report and one numbering.
- Do not name a parameter after an OS environment variable (`userName`, `path`, `temp`, `os` ...): an unresolved
  `${userName}` is filled from the Windows `USERNAME` variable. Prefix them (`orderUserName`).

### Multi-block pattern with `RunContext`

Each block is a new class instance, so values cross blocks through `utils.RunContext`:

1. **Create** the data the scenario needs by API in the first block(s): `RunContext.put("orderId", id)`; for a user
   the run made, also `RunContext.setCurrentUser(userName)`.
2. **Use** it on the web / phone: `RunContext.get("orderId")` (throws a clear message if the creating block did not
   run or failed), `RunContext.has(key)`.
3. **Clean up** by API in the **last** block, and `RunContext.remove(key)`.

`RunContext` is cleared when a suite starts. Cleanup is a test block, not a hook, so it shows in the report. A run that
is stopped half-way keeps its data: clean it with the delete call by hand.

Several users in one scenario (a requester and the approver, two users to compare): one create block per user, each
under its own key (`RunContext.put("managerId", ...)`); the clean-up deletes them all, newest first, and fails at the
end naming any that is left.

### Set-up and clean-up blocks - keep this table current

The project's ready blocks: what a suite can drop in instead of writing set-up again. Add a row whenever a new
`...ByApi` block is verified; the **create-test-case** agent reads this table first.

| Need | Block (class → method) | Parameters | Gives (RunContext key) / removes |
|---|---|---|---|
| `<a user the run owns>` | `apis.<area>.<X>ApiTests` → `create<X>ByApi` | `<tag>` | `<key>` |
| `<admin session in the browser>` | `<login block>` | - | the saved session |
| `<delete everything the run created>` | `apis.<area>.<X>ApiTests` → `delete<X>ByApi` | none | removes `<key>` |

A set-up block may assert (a failed set-up is then a red block with the API answer, and the blocks after it fail
fast). A set-up that needs an entity with no block yet gets **one generic, parameterised** `...ByApi` method backed by
an API client - not a private helper inside a UI test.

### Every suite - keep this table current

| Suite | Blocks | Own data | What it does | Group (`bugcycle/suite-groups.json`) | CI job |
|---|---|---|---|---|---|
| `api/apiSmoke` | 1 | - | health check of the API | api, smoke | |
| `web/webSmoke` | 1 | - | the app opens | web, smoke | |
| `mob/mobSmoke` | 1 | - | the app is in front | mobile | |
| `hybrid/hybridSmoke` | 3 | - | API + web in one report | hybrid | |

### Work dates (apps with per-day data)

`BaseTests.workDate(String param)` turns a `workDate` parameter into a day: `today`; `auto` (one day per run, rolled in
`data/workDate.txt`, starts at `-DworkDateSeed` or today); `auto:yyyy-MM-dd` (same, own file and seed); a fixed date;
empty = `data/testDate.txt`, which moves **per block**. Use `auto` when several blocks must share a day.

## Test data (`data/testData.json` -> `src/main/java/data`)

```
ENVIRONMENTS  { "<Env>": { WEB_URL, API_BASE_URI, MOBILE_API_BASE_URI } }   the suite's server picks one
ACCOUNTS      [ { CODE, ENV, ADMIN, PASSWORD } ]                              admins per account / tenant (ENV "" = every env)
UI            { URLs {name: path}, USERS {name: User}, VERIFICATION_CODES {name: code} }
API           { HEADERS {name: value}, USERS {name: User} }
MOBILE        { DEVICE_NAME, WIFI_UDID, APP_PACKAGE, APP_ACTIVITY, APK_PATH, SESSION_FILE,
                VERIFICATION_CODES {}, USERS {name: User} }
EMAIL         { IMAP_HOST, MAILBOX, SENDER }
User          { EMAIL, USERNAME, PASSWORD, NEW_PASSWORD, WRONG_PASSWORD, FIRST_NAME, LAST_NAME }  - fill only what you need
```

```java
import static reader.ReadDataFromJson.dataModel;

dataModel().env().WEB_URL                        // the running env's entry (case-insensitive key)
dataModel().UI.url("ORDERS")                     // WEB_URL + UI.URLs.ORDERS
dataModel().UI.user("MAIN_USER").login()         // USERNAME, or EMAIL when USERNAME is empty
dataModel().UI.user("MAIN_USER").PASSWORD
dataModel().API.user("ADMIN")
dataModel().MOBILE.user("MAIN_USER")
dataModel().UI.VERIFICATION_CODES.get("OTP")
dataModel().account("ACME")                      // the ACCOUNTS row for that code on this env, or null
data.Env.current() / data.Env.is("Stage")        // the running env
```

- `user(...)`, `url(...)` throw a message naming the missing key, the section and the file.
- Named maps (`USERS`, `URLs`, `HEADERS`, `VERIFICATION_CODES`) take new keys with **no Java change**. A new field or
  section needs a matching `public` field in the POJO - Gson silently drops JSON keys with no field.
- `dataModel()` re-reads the file on every call (a test that edits it sees the change); don't call it in a tight loop.
- Secrets that must not be committed go in env vars / `-D` properties (the mail password already does).

## Saved logins

**Web** (`UtilsTests`, in memory for the run, per name):

```java
// first test of the suite: a real UI login, then
utilsTests.saveSession();                 // or saveSession("admin")  - localStorage + cookies of the origin
// every later test / block:
utilsTests.loginProgrammatically("/orders");            // default session, opens that path logged in
utilsTests.loginProgrammatically("admin", "/orders");
UtilsTests.hasSession("admin"); UtilsTests.forgetSession("admin");   // e.g. after a password change
```

So a web suite starts with a login block. On Chrome / Edge the keys are written by a DevTools script before the
app's own code runs (no race with the app's start-up).

**The UI login is a test only where the login is what is tested.** Everywhere else the session is set-up: once the
project has an API login client, add a block that logs in by API and writes the session the way the app stores it
(the keys listed in **app-testing** → Login flow), then `saveSession()`. No login page, no one-time code, no captcha -
and no collision when several CI builds log in as the same admin at once.

**Phone** (`BaseTests.loginOnPhone`, needs `MOBILE.SESSION_FILE` = the app's login file in its data folder, e.g.
`shared_prefs/<name>.xml`, and a **debuggable** build):

```java
loginOnPhone(userName,
        () -> { loginScreen.insertUser(user); loginScreen.insertPassword(pass); loginScreen.clickLogin(); },
        () -> loginScreen.homeIsShown());
```

The first call for a user (per env) runs the UI login and saves the file; later blocks write it back before the app
starts and open logged in. If the restored login does not reach home, it is dropped and the UI login runs again.
Empty `SESSION_FILE` = always the UI login.

## Email (`utils.EmailReader`, `utils.EmailReport`)

```java
long sentAfter = System.currentTimeMillis();
... the app sends the email ...
EmailReader.Email email = EmailReader.waitFor(null, body -> body.contains(userName), sentAfter, 120);
String code = email.value(EmailReader.CODE);        // also USERNAME, PASSWORD, LINK patterns
EmailReport.logLastEmail();                         // the email, rendered, in the entry's Attachments
```

`null` mailbox = `EMAIL.MAILBOX` / `MAIL_USER`; password from `MAIL_APP_PASSWORD`; only mails from `EMAIL.SENDER`.

## Reports

- **Where:** `report/<env>Env/<suite file name>/<suite file name>.html` (`Test` -> `testEnv`, `Stage` -> `stageEnv`),
  with each test's `.png`, frames and `.gif` beside it. A class run from the IDE without XML uses the suite name.
  `UtilsTests.getReportDir()` returns the folder. Surefire XML is in `target/surefire-reports/`.
- **One entry per test**, numbered in run order, typed `API` / `Web` / `Mobile` (Tags view filters by it), with env,
  block name, `class.method`, device and parameters. Failures log the exception; a test skipped before its setup ran
  still gets an entry with the reason (`ReportListener`).
- **Steps tab:** every helper call (as `Page.method`), one table per assertion (# · name · expected · actual · result), API calls, `Ends of Steps`,
  result.
- **Timeline tab (web):** steps with element + locator + calling method, the browser's XHR / fetch / page loads
  (status, time, bodies; HTTP 200 with `Status: false` marked as refused), assertions, console errors, the failure. Hover
  shows the recording frame of that moment. Filters All / Steps / Network / Problems. `-Dnetwork=off` removes the calls.
- **Attachments tab:** last screen (click = full size) and the recording player (web GIF frames, phone recording) with
  play / pause / frame step / full screen / download; `utilsTests.attachScreenshot("title")` adds a mid-test shot;
  `EmailReport` adds emails.
- **Masking:** passwords, tokens, secrets and verification codes are masked in API steps, network bodies and the
  live feed. Element labels never use an input's value.
- A step done without a helper (raw `findElement().click()`, JS click) is not numbered or boxed: call
  `highlight(by, "click")` first.

## Jenkins

- **One job per suite**, named like the suite file, with the env as a parameter; each publishes its report per build. A
  new suite gets its job as a copy of a sibling (only the suite name, report folders and description change).
- **Serialise what cannot overlap:** every job that uses the phone (one device = one run; Appium's port), and every
  job that changes account-wide settings - one throttle / lock category for them. Web and API jobs otherwise run in
  parallel over the agents.
- Builds that overlap log in as the same admin: see the note on one-time codes under *API clients*.
- Agents update their browser by themselves; `startWithCachedDriver` handles a driver / browser mismatch.
- After every CI run: the run report table with the build number, and check that the published report shows that run.
- A page published by a job (Live Runner, Run Job form) changes only when that job is **rebuilt** - a page fix is not
  done until the published page is checked.

- `Jenkinsfile`: parameters `SUITE`, `ENV`, `BROWSER` -> `mvn -B test -Dtestng=... -DserverType=... -DbrowserType=...`;
  publishes JUnit results and `report/<env>Env` as "`<ENV> Report`" (HTML Publisher).
- **Live Runner** (`jenkins/liveRunner.html`, served by Jenkins through HTML Publisher or `userContent`): pick a job,
  run it, watch tests, steps (element, locator, method), API calls, the live browser / phone picture and the console.
  It reads the build console: `utils.LiveFeed` prints one `@@live {json}` line per event (ASCII only). Pictures go to
  `live/<BUILD_NUMBER>/` in the workspace and are deleted at the end. `?build=<build url>` opens a running build.
  Republish the page (rebuild the job that publishes it) after changing it.
- **Run Job form** (`jenkins/jobForm.html`): a form built from any job's parameters; query `?job=/job/<...>`,
  `&undoJob=/job/<...>`, `&title=...`, `&resultKey=id`. A job reports its result with
  `JobResult.print(Map.of("id", id, "env", data.Env.current()))` -> one `@@result {json}` console line; the page shows
  it as a result card and its Undo button starts the undo job with `RESULT_ID`.
- `jenkins/job-form-button.js`: adds a "Run Job" button to the Jenkins header (Simple Theme plugin "Extra JavaScript
  URL"); set `PAGE_URL` in it to where the form is published.
- `node jenkins/run-report.js "<label>" [testng-results.xml]` for the per-block table of a finished build (download
  its `testng-results.xml`, or run it in the workspace).

## Pitfalls

1. **No driver / `NullPointerException` on `driver` or a page** - the block misses `<groups>` or the method misses
   `groups = "Web"/"Mob"`, or the page / screen was not constructed in `BaseTests.setUpWeb` / `setUp`.
2. **Web vs mobile `sendKeys` order:** web `sendKeys(By, int time, String text)`, mobile `sendKeys(By, String text, int time)`.
3. **`isDisplayed` (web) throws** on a missing element after its wait; use `webElements(by).isEmpty()` for presence
   checks. Mobile `isDisplayed` returns false.
4. **Stale elements:** helpers retry; a raw `WebElement` held across a re-render goes stale - re-find via the `By`.
5. **Two toast reads in one test:** call `waitForToasterToAppearAndDisappear()` between them, or the second read
   returns the first toast. Something floating over a button (toast, chat widget): `invisibilityOf` / `hideElement`.
6. **Driver / browser version mismatch:** `startWithCachedDriver` detects a major-version mismatch and restarts through
   Selenium Manager (downloads the matching driver). If a fresh driver is blocked by the OS (application control on
   an agent), it falls back to the cached one - allow the new driver on that machine.
7. **Slow mobile runs:** wireless adb is several times slower per command than USB; `PhoneConnection` prefers USB,
   `-Dudid` forces one. Read `target/appium.log` (every command with its time; timestamps may be UTC).
8. **`EADDRINUSE 127.0.0.1:4723`:** another run (or a Jenkins agent on the same machine) holds Appium's port / the
   phone. Don't kill `adb` during a run - it takes the phone's UiAutomator2 server down with it.
9. **Stopping a run:** stopping the Maven task may not stop the forked Surefire `java` process; kill that PID,
   then Appium (`node`) and `chromedriver` / `msedgedriver`. Clean up data the run created.
10. **Unresolved `${x}`** reaches the test as the literal string `${x}`; the framework treats `${serverType}`,
    `${browserType}`, `${dataFile}` as unset. Handle it in your own parameters (`healthPath.startsWith("${")`) or give
    them `@Optional` defaults and don't reference unset properties.
11. **Gson drops unknown keys** in fixed sections - add the POJO field.
12. **Encoding:** non-ASCII text needs the UTF-8 `argLine` already in `pom.xml`; keep Java sources UTF-8.
13. **Rolling date files** are gitignored; a wiped workspace restarts them at their seed (use `-DdateDir` on CI).
