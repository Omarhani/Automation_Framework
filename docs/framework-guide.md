# Automation Framework - the full guide

> The short version is the [README](../README.md): tell the Agent Bot what to test and it does the rest. This guide
> is for when you want to write or read the Java yourself, or understand what the agent builds.

One framework for testing **any** app — **Web**, **Mobile (Android)** and **API** — with a full report made for you
after every run.

You only write two things: **locators** (where an element is) and **actions** (what to do with it).
Waiting, clicking safely, screenshots, video, logging and the report are already done by the framework.

Built with: Java 17 · Maven · TestNG · Selenium 4 · Appium 2 · RestAssured · ExtentReports

---

## Contents

1. [How it works](#1-how-it-works)
2. [What you need installed](#2-what-you-need-installed)
3. [Start in 5 minutes](#3-start-in-5-minutes)
4. [Project folders](#4-project-folders)
5. [Example: your first web test](#5-example-your-first-web-test)
6. [Example: your first mobile test](#6-example-your-first-mobile-test)
7. [Example: your first API test](#7-example-your-first-api-test)
8. [Example: API + Web in one suite (hybrid)](#8-example-api--web-in-one-suite-hybrid)
9. [The test data file](#9-the-test-data-file)
10. [Running tests](#10-running-tests)
11. [The report](#11-the-report)
12. [Handy tools inside](#12-handy-tools-inside)
13. [CI: Jenkins and Azure DevOps](#13-ci-jenkins-and-azure-devops)
14. [Common problems](#14-common-problems)

---

## 1. How it works

```
 suiteFiles/web/mySuite.xml          (which tests to run, on which env and browser)
            │
            ▼
 BaseTests / BaseApi                  opens the browser or phone, opens a report entry,
            │                         starts recording - before EVERY test
            ▼
 Your test  (src/test/java/ui/...)    calls actions on pages:  loginPage.login(user, pass)
            │
            ▼
 Your page  (src/main/java/pages/...) locators + actions:  click(loginButton, 10)
            │
            ▼
 MethodHandlesWeb / Mobile            waits, highlights, clicks, types, logs the step
            │
            ▼
 After every test                     screenshot + recording + result saved into the report
            │
            ▼
 report/testEnv/mySuite/mySuite.html  open it in a browser
```

- **Pages / Screens** = one class per screen of your app. It holds the locators and the actions.
- **Tests** = call the actions and check the result. No waits, no driver code.
- **Suites** (XML) = choose which tests run, on which environment (Test / Stage) and browser.
- **testData.json** = URLs, users and app settings. No data is written inside the code.

---

## 2. What you need installed

| For | Install |
|---|---|
| Everything | Java 17+, Maven, IntelliJ IDEA (with the Lombok plugin) |
| Web tests | Chrome or Edge (the driver is downloaded automatically) |
| Mobile tests | Node.js, Appium 2 (`npm i -g appium`), the driver (`appium driver install uiautomator2`), Android SDK (adb), a phone with USB debugging on or an emulator |
| API tests | Nothing extra |

---

## 3. Start in 5 minutes

```bash
git clone https://github.com/Omarhani/Automation_Framework.git my-project-tests
cd my-project-tests
```

1. Open `data/testData.json` and put in your app's address:
   ```json
   "ENVIRONMENTS": {
     "Test": { "WEB_URL": "https://test.my-app.com", "API_BASE_URI": "https://api.test.my-app.com" }
   }
   ```
2. Run the ready-made smoke test:
   ```bash
   mvn test -Dtestng=web/webSmoke
   ```
3. Open `report/testEnv/webSmoke/webSmoke.html`. You should see one passed test with a screenshot and a recording.

It works. Now add your own pages and tests (next sections).

---

## 4. Project folders

```
data/
  testData.json            ← your URLs, users, app settings (starts empty)

src/main/java/
  pages/                   ← WEB pages: locators + actions      (HomePage.java is ready)
  screens/                 ← MOBILE screens: locators + actions (HomeScreen.java is ready)
  utils/                   ← MethodHandlesWeb / MethodHandlesMobile (the helpers you call)
  data/, reader/           ← reads testData.json (don't need to touch)

src/test/java/
  base/                    ← BaseTests (web + mobile), BaseApi (API) — the engine
  ui/                      ← your WEB tests
  mob/                     ← your MOBILE tests
  apis/                    ← your API tests;  apis/services/ = API clients
  utils/                   ← report, recording, network, live feed ... (don't need to touch)

src/test/resources/schemas ← JSON schemas to check API responses
suiteFiles/                ← web/ mob/ api/ hybrid/ suite XML files
jenkins/                   ← Jenkins pages (Live Runner, Run Job) and the run report script
bugcycle/                  ← run suite groups with retries, retest filed bugs, move them in the tracker
testingActivity/           ← story.js: reads a user story and its tickets for the testing-activity skill
.claude/skills, .claude/agents ← what the Agent Bot knows about this framework (see the end of this file)
report/                    ← created by each run
```

---

## 5. Example: your first web test

Say your app has a login page with a user field, a password field and a Login button.

**Step 1 — the page** · `src/main/java/pages/LoginPage.java`

```java
package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import utils.MethodHandlesWeb;

public class LoginPage extends MethodHandlesWeb {

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    // locators
    private final By userName    = By.id("username");
    private final By password    = By.id("password");
    private final By loginButton = By.xpath("//button[normalize-space()='Login']");
    private final By errorText   = By.cssSelector(".error-message");

    // actions
    public void login(String user, String pass) {
        sendKeys(userName, 10, user);    // 10 = seconds to wait for the element
        sendKeys(password, 10, pass);
        click(loginButton, 10);
    }

    public String getError() {
        return getText(errorText, 10);
    }
}
```

**Step 2 — register the page** · `src/test/java/base/BaseTests.java`

```java
// Page Objects - add your app's pages here
public HomePage homePage;
public LoginPage loginPage;                 // ← add

// inside setUpWeb(...)
homePage = new HomePage(driver);
loginPage = new LoginPage(driver);          // ← add
```

**Step 3 — the test** · `src/test/java/ui/login/LoginTests.java`

```java
package ui.login;

import base.BaseTests;
import data.User;
import org.testng.annotations.Test;

import static reader.ReadDataFromJson.dataModel;
import static utils.MethodHandlesWeb.myAssertEquals;

public class LoginTests extends BaseTests {

    @Test(groups = "Web")
    public void verifyThatWrongPasswordShowsAnError() {
        User user = dataModel().UI.user("MAIN_USER");          // from testData.json
        loginPage.login(user.login(), user.WRONG_PASSWORD);

        myAssertEquals(loginPage.getError(), "Wrong user name or password");
    }
}
```

**Step 4 — the suite** · `suiteFiles/web/webLogin.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="Web Login">
    <parameter name="server" value="${serverType}"/>
    <parameter name="browser" value="${browserType}"/>

    <test name="Login">
        <groups><run><include name="Web"/></run></groups>
        <classes>
            <class name="ui.login.LoginTests"/>
        </classes>
    </test>
</suite>
```

**Step 5 — run it**

```bash
mvn test -Dtestng=web/webLogin
```

> **Rule:** web test methods have `@Test(groups = "Web")` and their suite block includes the `Web` group.
> Mobile uses `Mob`. API tests need no group.

---

## 6. Example: your first mobile test

**Set the app** in `data/testData.json`:

```json
"MOBILE": {
  "DEVICE_NAME": "Pixel 7",
  "APP_PACKAGE": "com.mycompany.myapp",
  "APP_ACTIVITY": "com.mycompany.myapp.MainActivity",
  "APK_PATH": "Downloads/my-app-debug.apk"
}
```

> Find the package and activity of an installed app with:
> `adb shell dumpsys window | findstr mCurrentFocus` (Windows) or `| grep mCurrentFocus` (Mac/Linux).

**The screen** · `src/main/java/screens/LoginScreen.java`

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

    private final By userName    = AppiumBy.id("com.mycompany.myapp:id/username");
    private final By password    = AppiumBy.id("com.mycompany.myapp:id/password");
    private final By loginButton = AppiumBy.accessibilityId("Login");

    public void login(String user, String pass) {
        sendKeys(userName, user, 10);    // note: mobile sendKeys is (locator, text, seconds)
        sendKeys(password, pass, 10);
        click(loginButton, 10);
    }
}
```

Register it in `BaseTests` (field + `loginScreen = new LoginScreen(androidDriver);` inside `setUp()`),
write the test with `@Test(groups = "Mob")`, and include `Mob` in the suite:

```xml
<test name="Login on phone">
    <groups><run><include name="Mob"/></run></groups>
    <classes><class name="mob.login.MobLoginTests"/></classes>
</test>
```

Appium is started and stopped by the suite. A phone on USB is picked first; set `MOBILE.WIFI_UDID`
(e.g. `192.168.0.10:5555`) to use Wi-Fi when no cable is in.

---

## 7. Example: your first API test

**The client** · `src/test/java/apis/services/UsersApi.java`

```java
package apis.services;

import io.restassured.response.Response;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class UsersApi {

    public Response getUser(int id) {
        return given(SpecFactory.getSpec("users/" + id)).get();
    }

    public Response createUser(String name, String email) {
        return given(SpecFactory.getSpec("users"))
                .body(Map.of("name", name, "email", email))
                .post();
    }
}
```

`SpecFactory` already adds: the env's API address, JSON headers, your `API.HEADERS` from the data file, and the
report logging (every request and response goes into the report, passwords and tokens hidden).

**The test** · `src/test/java/apis/users/UsersApiTests.java`

```java
package apis.users;

import apis.services.UsersApi;
import base.BaseApi;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static utils.MethodHandlesWeb.myAssertEquals;

public class UsersApiTests extends BaseApi {

    @Test
    public void verifyThatAUserCanBeCreated() {
        Response response = new UsersApi().createUser("Test User", "test.user@example.com");

        myAssertEquals(response.statusCode(), 201);
        myAssertEquals(response.jsonPath().getString("name"), "Test User");
    }
}
```

To check the response shape, put a schema in `src/test/resources/schemas/user.json` and add:
`response.then().body(io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/user.json"));`

---

## 8. Example: API + Web in one suite (hybrid)

The common real-life flow: **create data by API → use it on the web or phone → delete it by API**.
All blocks write into **one** report.

TestNG creates a new class for every `<test>` block, so pass values between blocks with `RunContext`:

```java
// block 1 (API):   RunContext.put("userId", id);
// block 2 (Web):   int id = RunContext.get("userId");
// block 3 (API):   delete it — always the LAST block, so cleanup runs even if block 2 failed
```

```xml
<suite name="User Journey">
    <parameter name="server" value="${serverType}"/>
    <parameter name="browser" value="${browserType}"/>

    <test name="1. Create user by API">
        <classes><class name="apis.users.UsersApiTests"><methods><include name="createUser"/></methods></class></classes>
    </test>

    <test name="2. User logs in on the web">
        <groups><run><include name="Web"/></run></groups>
        <classes><class name="ui.login.LoginTests"/></classes>
    </test>

    <test name="3. Clean up - delete user by API">
        <classes><class name="apis.users.UsersApiTests"><methods><include name="deleteUser"/></methods></class></classes>
    </test>
</suite>
```

See `suiteFiles/hybrid/hybridSmoke.xml` for a ready one.

---

## 9. The test data file

`data/testData.json` holds everything that changes per project or environment:

| Section | What goes in it | Read it in code |
|---|---|---|
| `ENVIRONMENTS` | `WEB_URL`, `API_BASE_URI`, `MOBILE_API_BASE_URI` for each env (`Test`, `Stage`, add more) | `dataModel().env().WEB_URL` |
| `UI` | `USERS` (any names you like), page `URLs`, fixed `VERIFICATION_CODES` | `dataModel().UI.user("MAIN_USER")` |
| `API` | `HEADERS` sent on every call, `USERS` | `dataModel().API.user("ADMIN")` |
| `MOBILE` | device, app package / activity / APK, `USERS` | `dataModel().MOBILE.APP_PACKAGE` |
| `EMAIL` | IMAP host, mailbox, sender (for reading OTP / password emails) | `EmailReader` |
| `ACCOUNTS` | admin per account / tenant code (multi-tenant apps) | `dataModel().account("code")` |

A user has: `EMAIL`, `USERNAME`, `PASSWORD`, `NEW_PASSWORD`, `WRONG_PASSWORD`, `FIRST_NAME`, `LAST_NAME` —
fill only what you need.

> **Never put real passwords of real people or production secrets in this file.** Use test accounts.
> Secrets such as the mailbox password come from environment variables (`MAIL_APP_PASSWORD`).

---

## 10. Running tests

```bash
mvn test -Dtestng=web/webSmoke                                   # a web suite on Test, Chrome
mvn test -Dtestng=web/webSmoke -DserverType=Stage                # on Stage
mvn test -Dtestng=web/webSmoke -DbrowserType=headlessChrome      # no browser window
mvn test -Dtestng=mob/mobSmoke                                   # mobile
mvn test -Dtestng=api/apiSmoke                                   # API
mvn test -Dtestng=hybrid/hybridSmoke                             # API + web in one report
```

In IntelliJ: right-click a suite XML → **Run**.

| Option | Meaning | Default |
|---|---|---|
| `-Dtestng=` | suite file under `suiteFiles/` (no `.xml`) | `web/webSmoke` |
| `-DserverType=` | environment = a key of `ENVIRONMENTS` | `Test` |
| `-DbrowserType=` | `Chrome`, `Edge`, `headlessChrome`, `headlessEdge` | `Chrome` |
| `-DdataFile=` | use another data file | `data/testData.json` |
| `-Dudid=` | force a phone (serial or `ip:port`) | USB phone first |
| `-Dnetwork=off` | don't record network calls in the Timeline | on |

---

## 11. The report

After a run open **`report/<env>Env/<suite>/<suite>.html`** (e.g. `report/testEnv/webLogin/webLogin.html`).

Every test is one numbered entry with:

- **Info table**: type (Web / Mobile / API), env, test block, device / browser, parameters, start time
- **Steps**: every click / type / check in order, with the failure and stack trace when it fails
- **Timeline** (web): each step with its locator, the app's network calls (status, time, request / response),
  console errors — hover a row to see the screen at that moment
- **Attachments**: last screenshot, a recording player (play / pause / frame by frame / full screen) and a GIF download
- **API calls**: method, URL, status, time, and the request / response bodies (passwords and tokens shown as `***`)

Quick pass / fail table in the terminal:

```bash
node jenkins/run-report.js "Test"
```

---

## 12. Handy tools inside

| You want to… | Use |
|---|---|
| Skip the login page in later tests | After one UI login: `utilsTests.saveSession("admin")`. Later: `utilsTests.loginProgrammatically("admin", "/dashboard")` |
| Same on the phone | `loginOnPhone(user, () -> loginScreen.login(user, pass), () -> homeScreen.isOpen())` + set `MOBILE.SESSION_FILE` |
| Add a screenshot in the middle of a test | `utilsTests.attachScreenshot("Cart total")` |
| Write a readable step in the report | `ApiReport.step("Created order 1052")` |
| Read an OTP / password from an email | `EmailReader.waitFor(...)` / `EmailReader.waitForPassword(...)`, then `EmailReport.logLastEmail()` |
| Pass a value to the next suite block | `RunContext.put("orderId", id)` / `RunContext.get("orderId")` |
| Use a new date on every run | `workDate("auto")` — today, then +1 day each run |
| Show a job's result on the Jenkins Run Job page | `JobResult.print(Map.of("id", id, "user", name))` |

Helpers you call inside pages (`MethodHandlesWeb`): `click`, `sendKeys`, `getText`, `isDisplayed`, `clear`,
`selectByVisibleText`, `smartClick`, `clickWithActions`, `doubleClick`, `moveToElement`, `dragAndDrop`,
`scrollIntoView`, `acceptAlert`, `waitForToaster`, `openPage` … and in screens (`MethodHandlesMobile`):
`click`, `clickOnce`, `sendKeys`, `isDisplayed`, `isPresent`, `scrollAndClick`, `scrollIntoViewWithin`,
`hideKeyboardIfShown`, `restartApp`, `getToasterMessage` …
Assertions: `myAssertEquals(name, actual, expected)`, `myAssertContains`, `myAssertTrue(name, ...)`, `myAssertFalse`,
and `softly(title, () -> {...})` for a group that reports every field before it fails. Give each one a name
(`"Order details · total"`): the report shows a numbered table per assertion with expected / actual and a hint about
where two texts differ, and the run report names the failed assertion.

---

## 13. CI: Jenkins and Azure DevOps

**Jenkins** — `Jenkinsfile`: parameters `SUITE`, `ENV`, `BROWSER`; publishes the report as **"<ENV> Report"**.
Extra pages in `jenkins/`:

- `liveRunner.html` — start suites and watch them live: tests, steps, network calls and the browser screen.
- `jobForm.html` — turns any Jenkins job with parameters into a simple form, shows the result
  (printed with `JobResult.print`) and can start an "undo" job.

**Azure DevOps** — `azure-pipelines.yml`: pick suite, env and browser in "Run pipeline"; the report is attached
to the run.

---

## 14. Common problems

| Problem | Fix |
|---|---|
| `Tests run: 0` | The test method is missing `groups = "Web"` / `"Mob"`, or the suite block doesn't include that group |
| `No web URL for env "Test"` | Fill `ENVIRONMENTS.Test.WEB_URL` in `data/testData.json` |
| `No "MAIN_USER" in UI.USERS` | Add that user to `UI.USERS` in the data file |
| Browser starts very slowly | First run downloads the driver; later runs use the cached one |
| `No phone` | `adb devices` must list the phone as `device`; allow USB debugging on the phone |
| Mobile tests very slow | You're on Wi-Fi adb — plug in the USB cable |
| Arabic / non-English text shows as `????` | Already handled by the pom (`-Dfile.encoding=UTF-8`); run through Maven |

---

## Agent Bot skills and agents

`.claude/skills/` and `.claude/agents/` teach the Agent Bot how to work in this framework. They hold **no project
data**: the places marked `<...>` are templates you fill in for your app.

| Skill | What it is for |
|---|---|
| `automation-framework` | the framework itself: layout, lifecycle, helpers, named assertions, suites, data, reports, CI - and the house rules: every test case is **set-up by API → test → clean-up by API**, delete only what the run created, branch → run → pull request → CI → report |
| `app-testing` | *template*: your app's map - environments, login, pages, locators, quirks, accounts |
| `business-knowledge` | *template*: your app's business rules per module, plus a log of the stories handled |
| `api-catalog` | *template*: every API call set-up and clean-up can use, marked verified / seen / read from the app / unknown; `tools/web-api-scan.js` builds the list from a web app's own script |
| `xpath-locators` | writing and verifying stable locators |
| `ai-test-run` | running test cases by hand in the browser with a timed report |
| `testing-activity` | one user story end to end: read it and its tickets, walk it, write and run the test cases, automate, deliver |
| `suite-bug-cycle` | run a suite group with retries, confirm real bugs, retest them after the fix and move them in the tracker |
| `automation-bug-report` | turning a red run into a developer-ready bug - filed only after you say yes |

| Agent | What it does |
|---|---|
| `create-test-case` | builds one test case as set-up → test → clean-up, verifying any API call it needs first |
| `testing-activity` | runs the testing-activity procedure for one story |
| `suite-bug-runner` | runs a suite group (or retests bugs) in the background and returns the report |

Tools they use: `jenkins/run-report.js` (the per-block result table), `bugcycle/*.js` (fill
`bugcycle/config.json` with your tracker and the states that mean "fixed on this env"), `testingActivity/story.js`.
The tracker code is for Azure DevOps and uses the login git already has; for another tracker re-implement
`bugcycle/ado.js`.
