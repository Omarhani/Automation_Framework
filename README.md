# Automation Framework

Test any app - **web**, **mobile (Android)** and **API** - by telling Claude Code what to test.

You say: *"test that a user can add an order and see it in the list"*.
The **create-test-case** agent sets the data up through the API, runs the steps, checks the result with named
assertions, cleans everything up again, and gives you the test case, the suite and the report.

Built with Java 17 · Maven · TestNG · Selenium 4 · Appium 2 · RestAssured · ExtentReports · Claude Code.

## 1. Set up once

1. Clone the repo and open the folder in Claude Code.
2. Put your app in `data/testData.json`:
   ```json
   "ENVIRONMENTS": {
     "Test": { "WEB_URL": "https://test.my-app.com", "API_BASE_URI": "https://api.test.my-app.com/" }
   },
   "UI": { "USERS": { "MAIN_USER": { "USERNAME": "admin", "PASSWORD": "..." } } }
   ```
3. Say to Claude Code: *"fill in the app-testing skill for my app"* - it walks your app once and writes the app map
   (pages, login, locators) into `.claude/skills/app-testing/SKILL.md`. Do the same for **api-catalog** (*"scan my
   app's API"*) so the agent knows which calls create and delete data.

Installed on the machine: Java 17, Maven, Node.js, Chrome or Edge. For mobile: Appium 2, the Android SDK, a phone
or emulator.

## 2. Ask for a test

Just say what to test. Examples:

| You say | What happens |
|---|---|
| *"test that a user can add an order and see it in the list"* | the **create-test-case** agent writes the case, runs it by hand, automates it and runs the suite |
| *"create test cases for the checkout page"* | several cases of one scenario, one suite, set-up once, clean-up once |
| *"test story 1234"* | the **testing-activity** agent reads the story and its tickets from the tracker, learns the rules, walks the feature, writes and runs the cases, automates, opens the pull request, runs it in CI |
| *"run the smoke group and find bugs"* | the **suite-bug-runner** agent runs the group, retries failures to confirm real bugs, drafts them - you say yes before any bug is filed |
| *"retest bug 1201"* | runs the suite behind the bug and tells you fixed / not fixed |

Every test case the agent makes has the same shape:

```
Set-up (API)      the user, the record, the setting the case needs - created by API, never by clicking
Steps             only the steps the case is about, on the web page, the phone or the API
Expected          named assertions: "Orders list · status" expected "New", actual "New"
Clean-up (API)    everything the run created is deleted again; nothing that existed before is touched
```

You get back: the test case text, the data plan (which API call makes each precondition true), the suite XML, the
run report as a table, and the list of files it changed.

## 3. Run a suite yourself

```bash
mvn test -Dtestng=web/webSmoke -DserverType=Test -DbrowserType=headlessChrome
node jenkins/run-report.js "Test"        # the result table: one row per block, why it failed
```

The full report is in `report/testEnv/<suite>/<suite>.html`: steps, screenshots, a recording, the API calls and
every assertion with expected / actual.

## 4. Where things are

| Folder | Holds |
|---|---|
| `.claude/skills/` | what Claude Code knows: the framework, your app map, business rules, the API catalog, how to write locators, how to test a story, the bug cycle |
| `.claude/agents/` | `create-test-case`, `testing-activity`, `suite-bug-runner` |
| `data/testData.json` | URLs, users, settings - no data lives in the code |
| `src/main/java/pages`, `screens` | one class per web page / app screen: locators + actions |
| `src/test/java/ui`, `mob`, `apis` | the tests; `apis/services` = the API clients set-up and clean-up use |
| `suiteFiles/` | which blocks run, on which env and browser |
| `bugcycle/`, `testingActivity/` | the bug cycle and the story reader (tracker settings in `bugcycle/config.json`) |

Want to write or read the Java yourself? See the [full guide](docs/framework-guide.md).
