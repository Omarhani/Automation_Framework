package base;

import data.Env;
import data.MOBILE;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import lombok.Getter;
import lombok.Setter;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.*;
import pages.*;
import screens.*;
import utils.BrowserGifRecorder;
import utils.MethodHandlesWeb;
import utils.MobileScreenRecorder;
import utils.MobileSession;
import utils.NetworkRecorder;
import utils.PhoneConnection;
import utils.ReportListener;
import utils.RunContext;
import utils.TestTimeline;
import utils.UtilsTests;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static reader.ReadDataFromJson.dataModel;

/**
 * Base of every web and mobile test class: starts the browser / phone, opens a numbered report entry per test,
 * records it (browser GIF + Timeline, phone recording), attaches the last screen, closes the entry with its result.
 *
 * <p>Which hooks run is chosen by the suite's groups: {@code Web} for browser tests, {@code Mob} for phone tests
 * ({@code <groups><run><include name="Mob"/></run></groups>}). The env comes from the suite's {@code server}
 * parameter and every URL, app package and device from the data file (data/testData.json, or the suite's
 * {@code dataFile}). Your project's page objects and screens are created in {@link #setUpWeb} and {@link #setUp}.
 */
@Listeners(ReportListener.class)
public class BaseTests {

    /// //////////////////////////////////////////////////////////////////////////////////////
    /// ////  Mobile Obj and Var
    /// //////////////////////////////////////////////////////////////////////////////////////
    URL url;
    protected AndroidDriver androidDriver;
    UiAutomator2Options options;
    private static AppiumDriverLocalService service;

    @Getter
    @Setter
    public static String serverType;
    @Setter
    @Getter
    public static String baseURL;

    private static final String GBOARD_PACKAGE = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME";

    // Screen Objects - add your app's screens here
    public HomeScreen homeScreen;

    // Flag to prevent the @BeforeMethod from running before the first test
    private boolean isFirstTest = true;

    /// //////////////////////////////////////////////////////////////////////////////////////
    /// ////  Web Obj and Var
    /// //////////////////////////////////////////////////////////////////////////////////////


    /**
     * Rolled forward one day per test class instance ({@code data/testDate.txt}). Built on first use,
     * so only the suites that actually ask for it move the file.
     */
    private String testDate;
    protected WebDriver driver;

    /**
     * Rolled forward one day per run ({@code data/workDate.txt}). Built on the first
     * {@code workDate="auto"}, so only the suites that ask for it move the file.
     */
    private static String suiteWorkDate;

    /** This run's day per {@code workDate="auto:yyyy-MM-dd"} seed, each rolled in its own file. */
    private static final java.util.Map<String, String> seededSuiteWorkDates = new java.util.HashMap<>();

    /**
     * The day a scenario works on - for apps whose data is per day (bookings, timesheets, schedules) and refuse
     * the same action twice on one day. Four ways to ask for one, from a suite {@code <parameter>} - or from a
     * pipeline workDateFor&lt;Suite&gt; option, when the suite says e.g. {@code value="${workDateForMySuite}"}:
     *
     * <table>
     *   <tr><td>{@code "today"}</td>
     *       <td>the current date. Also what any unresolved {@code ${workDateFor...}} gives, i.e. a run
     *           started without that option.</td></tr>
     *   <tr><td>{@code "auto"}</td>
     *       <td>the run's own day, rolled forward one day per run ({@code data/workDate.txt}; the first run
     *           starts at {@code -DworkDateSeed} or today). Every {@code <test>} block of the run gets the same
     *           day, so a scenario can be spread over as many blocks as it needs and the next run
     *           moves to the next day on its own - nothing to edit between runs.</td></tr>
     *   <tr><td>{@code "auto:yyyy-MM-dd"}</td>
     *       <td>same as {@code auto}, but starting from that day and kept in its own file, e.g.
     *           {@code auto:1910-01-01} → {@code data/workDate-1910-01-01.txt}: 1910-01-01, then
     *           1910-01-02, ... one day per run.</td></tr>
     *   <tr><td>a date</td><td>that exact day, e.g. {@code 2026-07-17}, pinned.</td></tr>
     *   <tr><td>empty / absent</td>
     *       <td>the rolling {@code data/testDate.txt} value. <b>Careful:</b> that one is built once per test class
     *           instance, which TestNG makes once per {@code <test>} tag, so it moves a day per block.</td></tr>
     * </table>
     */
    protected String workDate(String workDate) {
        if (workDate == null || workDate.trim().isEmpty()) {
            if (testDate == null) {
                testDate = new UtilsTests(driver).getNextDynamicTestDate();
            }
            return testDate;
        }
        if (workDate.trim().equalsIgnoreCase("today") || workDate.trim().matches("\\$\\{workDateFor\\w+}")) {
            return LocalDate.now().toString();
        }
        if (workDate.trim().equalsIgnoreCase("auto")) {
            synchronized (BaseTests.class) {
                if (suiteWorkDate == null) {
                    suiteWorkDate = new UtilsTests(driver).getNextSuiteWorkDate();
                }
            }
            return suiteWorkDate;
        }
        if (workDate.trim().matches("(?i)auto:\\d{4}-\\d{2}-\\d{2}")) {
            String seed = workDate.trim().substring(5);
            synchronized (BaseTests.class) {
                return seededSuiteWorkDates.computeIfAbsent(seed, s ->
                        new UtilsTests(driver).getNextSuiteWorkDate("data/workDate-" + s + ".txt", s));
            }
        }
        return workDate.trim();
    }

    // Page Objects - add your app's pages here
    public HomePage homePage;

    ChromeOptions chromeOptions;
    EdgeOptions edgeOptions;
    protected String currentBrowser;

    protected UtilsTests utilsTests;

    /// //////////////////////////////////////////////////////////////////////////////////////
    /// ////  Web Methods
    /// //////////////////////////////////////////////////////////////////////////////////////
    /**
     * Starts a browser with the newest driver already in Selenium's cache (~/.cache/selenium/&lt;driver&gt;).
     * Without this, Selenium Manager looks the driver up online (googlechromelabs.github.io) on every start
     * and waits up to its 300 s timeout when that site is slow or unreachable - a 5 minute browser setup.
     * If the cached driver does not fit the installed browser (e.g. right after a browser update), it falls
     * back to Selenium Manager once, which downloads the right one.
     */
    private static WebDriver startWithCachedDriver(String driverName, String driverProperty,
                                                   java.util.function.Supplier<WebDriver> start) {
        if (System.getProperty(driverProperty) == null) {
            java.io.File[] versions = new java.io.File(System.getProperty("user.home"),
                    ".cache/selenium/" + driverName + "/" + platformFolder()).listFiles(java.io.File::isDirectory);
            String exe = driverName + (System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "");
            java.util.Arrays.stream(versions == null ? new java.io.File[0] : versions)
                    .filter(v -> new java.io.File(v, exe).isFile())
                    .max(java.util.Comparator.comparing(v -> java.util.Arrays.stream(v.getName().split("\\."))
                            .map(p -> String.format("%06d", Integer.parseInt(p.replaceAll("\\D", "0"))))
                            .collect(java.util.stream.Collectors.joining("."))))
                    .ifPresent(v -> System.setProperty(driverProperty, new java.io.File(v, exe).getAbsolutePath()));
        }
        WebDriver started;
        try {
            started = start.get();
        } catch (org.openqa.selenium.SessionNotCreatedException e) {
            // cached driver is for another browser version - let Selenium Manager fetch the right one
            System.clearProperty(driverProperty);
            return start.get();
        }
        // A driver one version behind can still open a session (ChromeDriver 153 with Chrome 154) and then pages
        // hang ("Timed out receiving message from renderer"). Mismatched majors: close it and start once more
        // through Selenium Manager, which caches the matching driver, so the next start picks it straight away.
        String browserMajor = majorVersion(((org.openqa.selenium.HasCapabilities) started).getCapabilities().getBrowserVersion());
        String driverMajor = majorVersion(driverVersion(((org.openqa.selenium.HasCapabilities) started).getCapabilities()));
        if (!browserMajor.isEmpty() && !driverMajor.isEmpty() && !browserMajor.equals(driverMajor)) {
            System.out.println("⚠️ " + driverName + " " + driverMajor + " does not match the browser " + browserMajor
                    + " - restarting with the matching driver");
            started.quit();
            String cachedDriver = System.getProperty(driverProperty);
            System.clearProperty(driverProperty);
            try {
                return start.get();
            } catch (RuntimeException blocked) {
                // The matching driver could not run - e.g. Windows' Application Control blocked a freshly
                // downloaded driver on a build agent. Carry on with the cached one rather than failing.
                System.out.println("⚠️ The matching " + driverName + " could not start (" + String.valueOf(blocked.getMessage()).split("\n")[0]
                        + ") - using the cached " + driverMajor + " one. Allow the new driver on this machine to fix it.");
                if (cachedDriver != null) {
                    System.setProperty(driverProperty, cachedDriver);
                }
                return start.get();
            }
        }
        return started;
    }

    /** Selenium Manager's cache folder for this machine: win64, linux64, mac-x64 or mac-arm64. */
    private static String platformFolder() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "win64";
        }
        if (os.contains("mac")) {
            return System.getProperty("os.arch").contains("aarch64") ? "mac-arm64" : "mac-x64";
        }
        return "linux64";
    }

    /** chromedriverVersion / msedgedriverVersion from the session's capabilities ("153.0.8010.52 (...)"). */
    private static String driverVersion(org.openqa.selenium.Capabilities capabilities) {
        for (String browser : new String[]{"chrome", "msedge"}) {
            Object details = capabilities.getCapability(browser);
            if (details instanceof java.util.Map) {
                Object version = ((java.util.Map<?, ?>) details).get(browser.equals("chrome") ? "chromedriverVersion" : "msedgedriverVersion");
                if (version != null) {
                    return version.toString();
                }
            }
        }
        return "";
    }

    private static String majorVersion(String version) {
        return version == null ? "" : version.trim().replaceFirst("^(\\d+).*$", "$1").replaceAll("\\D.*", "");
    }

    /**
     * Chrome / Edge keep their DevTools Network events and console errors, for the Timeline of each test's
     * report entry ({@link NetworkRecorder} reads them). Off with {@code -Dnetwork=off}.
     */
    private static void logNetworkAndConsole(org.openqa.selenium.chromium.ChromiumOptions<?> options, String loggingPrefs) {
        if (!NetworkRecorder.ENABLED) {
            return;
        }
        org.openqa.selenium.logging.LoggingPreferences logs = new org.openqa.selenium.logging.LoggingPreferences();
        logs.enable(org.openqa.selenium.logging.LogType.PERFORMANCE, java.util.logging.Level.ALL);
        logs.enable(org.openqa.selenium.logging.LogType.BROWSER, java.util.logging.Level.SEVERE);
        options.setCapability(loggingPrefs, logs);
        options.setExperimentalOption("perfLoggingPrefs", Map.of("enableNetwork", true, "enablePage", false));
    }

    @Parameters({"browser"})
    public void setUpBrowserWeb(String browser) {
        currentBrowser = browser;
        if (browser.equalsIgnoreCase("Chrome") || browser.equalsIgnoreCase("${browserType}")) {
            chromeOptions = new ChromeOptions();
            chromeOptions.addArguments(
                    "--window-size=1920,1080"
            );
            logNetworkAndConsole(chromeOptions, ChromeOptions.LOGGING_PREFS);
            driver = startWithCachedDriver("chromedriver", "webdriver.chrome.driver", () -> new ChromeDriver(chromeOptions));
        } else if (browser.equalsIgnoreCase("Edge")) {
            edgeOptions = new EdgeOptions();
            edgeOptions.addArguments(
                    "--window-size=1920,1080"
            );
            logNetworkAndConsole(edgeOptions, EdgeOptions.LOGGING_PREFS);
            driver = startWithCachedDriver("msedgedriver", "webdriver.edge.driver", () -> new EdgeDriver(edgeOptions));
        } else if (browser.equalsIgnoreCase("headlessChrome")) {
            chromeOptions = new ChromeOptions();
            chromeOptions.addArguments(
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--headless=new",
                    "--disable-gpu",
                    "--window-size=1920,1080",
                    // no fixed --remote-debugging-port: two headless runs on one agent machine would fight over it
                    "--disable-extensions"
            );
            logNetworkAndConsole(chromeOptions, ChromeOptions.LOGGING_PREFS);
            driver = startWithCachedDriver("chromedriver", "webdriver.chrome.driver", () -> new ChromeDriver(chromeOptions));
        } else if (browser.equalsIgnoreCase("headlessEdge")) {
            edgeOptions = new EdgeOptions();
            edgeOptions.addArguments(
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--headless=new",
                    "--disable-gpu",
                    "--window-size=1920,1080",
                    // no fixed --remote-debugging-port: two headless runs on one agent machine would fight over it
                    "--disable-extensions"
            );
            logNetworkAndConsole(edgeOptions, EdgeOptions.LOGGING_PREFS);
            driver = startWithCachedDriver("msedgedriver", "webdriver.edge.driver", () -> new EdgeDriver(edgeOptions));
        } else {
            throw new IllegalArgumentException("Unknown browser \"" + browser
                    + "\": use Chrome, Edge, headlessChrome or headlessEdge");
        }
    }


    @Parameters("browser")
    @BeforeClass(groups = "Web")
    public void setUpWeb(@Optional("Chrome") String browser) {
        utilsTests = new UtilsTests(driver);
        setUpBrowserWeb(browser);
        driver.manage().window().setSize(new Dimension(1920, 1080));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        // your project's page objects
        homePage = new HomePage(driver);
    }

    @AfterMethod(groups = "Web")
    public void afterMethodWeb(Method method, ITestResult result) {
        utilsTests = new UtilsTests(driver);
        try {
            BrowserGifRecorder.stopRecord();
            TestTimeline.stop();
            // a hung browser can't give its last screen, but the recording up to that point still goes in
            utilsTests.saveLastScreenOrExplain(method);
            utilsTests.addTimeline(result);
            utilsTests.addAttachment(method);
        } catch (Exception e) {
            // a dead browser must not leave the entry without its result
            System.out.println("Could not save the attachments: " + e.getMessage());
        } finally {
            utilsTests.finishTestCase(result);
        }
    }

    @AfterClass(groups = "Web")
    public void tearDownWeb() {
        if (driver != null) {
            driver.quit();
        }
    }

    @BeforeMethod(groups = "Web")
    public void goHomeWeb(Method method, ITestContext context, Object[] parameters) throws Exception {
        utilsTests = new UtilsTests(driver);
        // numbered entry with the xml <test> name and parameters; also names this run's files
        utilsTests.createTestCaseInReport(method, context, parameters, UtilsTests.WEB,
                currentBrowser == null || currentBrowser.startsWith("${") ? "Chrome" : currentBrowser);
        // records the browser only, so it works headless and on Jenkins too
        BrowserGifRecorder.startRecord(driver, UtilsTests.getTestCaseFileName());
        // steps with their locators + the browser's API calls, for the entry's Timeline tab
        TestTimeline.start(driver);
        openWithOneRetry(getBaseURL());
    }

    /**
     * Chrome now and then hangs on a page load ("Timed out receiving message from renderer", 30 s) and the whole
     * block was skipped while every other block loaded the same page fine. Stop the stuck load and try once
     * more before failing.
     */
    private void openWithOneRetry(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("No web URL for env \"" + Env.current() + "\": set ENVIRONMENTS." + Env.current()
                    + ".WEB_URL in " + reader.ReadDataFromJson.dataFile());
        }
        try {
            driver.get(url);
        } catch (org.openqa.selenium.TimeoutException first) {
            System.out.println("⏳ Page load hung (" + first.getMessage().split("\n")[0] + ") - loading it again");
            try {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.stop();");
            } catch (Exception ignored) {
                // the renderer may not answer either; the second get decides
            }
            driver.get(url);
        }
    }

    @Parameters({"server", "dataFile"})
    @BeforeSuite(groups = "Web")
    public void beforeSuiteWeb(@Optional("Test") String server, @Optional("") String dataFile, ITestContext context) {
        reader.ReadDataFromJson.useSuiteDataFile(dataFile);
        // report/testEnv|stageEnv/<suite xml name>/<suite xml name>.html + this suite's png/gif
        UtilsTests.setReportFolder(context.getSuite(), server);
        setEnv(server);
        MethodHandlesWeb.setURL(getBaseURL());
        utilsTests = new UtilsTests(driver);
        utilsTests.createReport();
        RunContext.clear();

        // the work day is picked on the first workDate="auto" of this run, not here
        suiteWorkDate = null;
        seededSuiteWorkDates.clear();
    }

    @AfterSuite(groups = "Web")
    public void afterSuiteWeb() {
        utilsTests = new UtilsTests(driver);
        utilsTests.flushReport();
    }

    // Start Appium server before Mob suite
    @Parameters({"server", "dataFile"})
    @BeforeSuite(groups = "Mob")
    public void beforeSuiteMob(@Optional("Test") String server, @Optional("") String dataFile, ITestContext context) {
        reader.ReadDataFromJson.useSuiteDataFile(dataFile);
        // same report folder and html as the web suites (a hybrid suite shares one report)
        UtilsTests.setReportFolder(context.getSuite(), server);
        new UtilsTests(androidDriver).createReport();

        setEnv(server);
        RunContext.clear();
        startServer();
    }

    // Stop Appium server after Mob suite
    @AfterSuite(groups = "Mob")
    public void afterSuiteMob() {
        new UtilsTests(androidDriver).flushReport();
        stopServer();
    }

    /// //////////////////////////////////////////////////////////////////////////////////////
    /// ////  Mob Methods
    /// //////////////////////////////////////////////////////////////////////////////////////

    /** testData.json MOBILE - the phone and the app under test. */
    protected static MOBILE mobile() {
        return dataModel().MOBILE;
    }

    /**
     * The phone's loginProgrammatically: the first call for a user runs {@code uiLogin} (your app's login
     * screens) and, once {@code loggedIn} says the app is past login, saves the app's login file
     * ({@link MobileSession}, needs MOBILE.SESSION_FILE); every later call - a new {@code <test>} block, whose
     * session starts with the app's data cleared - writes it back and the app opens logged in. If the saved
     * login does not get past login, it is dropped and the UI login runs again.
     */
    protected void loginOnPhone(String userName, Runnable uiLogin, BooleanSupplier loggedIn) {
        RunContext.setCurrentUser(userName);
        String key = sessionKey(userName);
        if (MobileSession.restore(androidDriver, key)) {
            if (loggedIn.getAsBoolean()) {
                return;
            }
            MobileSession.forget(androidDriver, key);
        }

        // the session starts without the app when a saved login exists; a UI login still needs it on screen
        MobileSession.launchIfClosed(androidDriver, mobile().APP_PACKAGE);
        uiLogin.run();
        if (loggedIn.getAsBoolean()) {
            MobileSession.save(androidDriver, key);
        }
    }

    /** The saved-login key: one per server and user, as a run may make its own user. */
    private String sessionKey(String userName) {
        return getServerType() + "|" + userName;
    }

    public UiAutomator2Options setUpDeviceAndTheAppApplication() {
        MOBILE mobile = mobile();
        if (mobile.APP_PACKAGE == null || mobile.APP_PACKAGE.isBlank()) {
            throw new IllegalStateException("No app: set MOBILE.APP_PACKAGE and MOBILE.APP_ACTIVITY in " + reader.ReadDataFromJson.dataFile());
        }
        options = new UiAutomator2Options();
        options.setDeviceName(mobile.DEVICE_NAME == null || mobile.DEVICE_NAME.isBlank() ? "Android" : mobile.DEVICE_NAME);
        options.setAutomationName("UiAutomator2");
        options.setPlatformName("Android");
        // USB when the cable is in, wireless adb otherwise (PhoneConnection)
        String udid = PhoneConnection.udid();
        options.setUdid(udid);
        options.autoGrantPermissions();
        options.ignoreHiddenApiPolicyError();
        options.setCapability("appium:unicodeKeyboard", true);
        options.setCapability("appium:resetKeyboard", true);
        options.setCapability("appium:disableWindowAnimation", true);
        // 0: an app screen with an endless animation never lets the app go idle, so any wait here was paid in
        // full on every command (~4 s each). The click helpers verify each tap themselves.
        options.setCapability("appium:waitForIdleTimeout", 0);
        options.setAppActivity(mobile.APP_ACTIVITY);
        options.setAppPackage(mobile.APP_PACKAGE);
        // A saved login for the run's user: loginOnPhone writes it back and starts the app itself, so the
        // session must not start the app too - that showed the app open, close and open again.
        // Every <test> block is a new session, so this is where the extra start came from.
        if (RunContext.currentUser() != null && MobileSession.hasSaved(sessionKey(RunContext.currentUser()))) {
            options.setCapability("appium:autoLaunch", false);
            System.out.println("Session starts without the app: the saved phone login will start it");
        }
        options.setEnforceAppInstall(false);
        // the phone streams its screen here for the report's recording (MobileScreenRecorder)
        options.setCapability("appium:mjpegServerPort", MobileScreenRecorder.MJPEG_PORT);
        // the recorder keeps at most 4 frames a second; the phone's default 10 was work for nothing
        options.setCapability("appium:settings[mjpegServerFramerate]", 5);
        if (PhoneConnection.isWifi(udid)) {
            // smaller frames: over Wi-Fi the stream shares the link with every command
            options.setCapability("appium:settings[mjpegScalingFactor]", 35);
            options.setCapability("appium:settings[mjpegServerScreenshotQuality]", 40);
        }
        return options;
    }

    @BeforeMethod(groups = "Mob")
    public void goHome(Method method, ITestContext context, Object[] parameters) {
        // the entry first: if the app restart below fails, the skip is reported under this test, not the one before
        utilsTests = new UtilsTests(androidDriver);
        utilsTests.createTestCaseInReport(method, context, parameters, UtilsTests.MOBILE, mobile().DEVICE_NAME);
        MobileSession.newTest();
        String app = mobile().APP_PACKAGE;
        if (isFirstTest) {
            isFirstTest = false;
        } else {
            androidDriver.terminateApp(app);
            // Same rule as the session start (setUpDeviceAndTheAppApplication): with a saved login for the
            // run's user, loginOnPhone starts the app itself. Only a block with several tests gets here.
            if (!MobileSession.hasSaved(sessionKey(RunContext.currentUser()))) {
                androidDriver.activateApp(app);
            }
        }
        MobileScreenRecorder.startRecord(androidDriver, UtilsTests.getTestCaseFileName());
    }

    @AfterMethod(groups = "Mob")
    public void afterMethodMob(Method method, ITestResult result) {
        utilsTests = new UtilsTests(androidDriver);
        // the app refreshed its tokens during the test: the next block needs this copy, not the one from login
        MobileSession.saveAgain(androidDriver);
        try {
            MobileScreenRecorder.stopRecord();
            utilsTests.saveLastScreenOrExplain(method);
            utilsTests.addAttachment(method, true);
        } catch (Exception e) {
            // a lost phone session must not leave the entry without its result
            System.out.println("Could not save the attachments: " + e.getMessage());
        } finally {
            utilsTests.finishTestCase(result);
        }
    }

    @BeforeClass(groups = "Mob")
    public void setUp() throws MalformedURLException {
        url = new URL("http://127.0.0.1:4723/");
        UiAutomator2Options baseOptions = setUpDeviceAndTheAppApplication();

        try {
            androidDriver = new AndroidDriver(url, baseOptions);
            System.out.println("App started via package/activity (app assumed installed on device).");
        } catch (Exception e) {
            String apk = mobile().apkPath();
            if (apk.isEmpty() || !new java.io.File(apk).isFile()) {
                throw new IllegalStateException("The app did not start (" + String.valueOf(e.getMessage()).split("\n")[0]
                        + ") and there is no APK to install: set MOBILE.APK_PATH (now \"" + apk + "\")", e);
            }
            System.out.println("Failed to start app via package/activity, attempting to install APK: " + e.getMessage());
            baseOptions.setApp(apk);
            baseOptions.setEnforceAppInstall(true);
            androidDriver = new AndroidDriver(url, baseOptions);
            System.out.println("APK installed and app started.");
        }

        // your project's screens
        homeScreen = new HomeScreen(androidDriver);
    }

    @AfterClass(groups = "Mob")
    public void tearDown() {
        if (androidDriver != null) {
            try {
                // unicodeKeyboard swaps in Appium's keyboard; give the phone its usual one (GBoard) back
                System.out.println("Attempting to reset keyboard to GBoard via ADB...");
                Map<String, Object> args = new HashMap<>();
                args.put("command", "ime set " + GBOARD_PACKAGE);
                androidDriver.executeScript("mobile: shell", args);
                System.out.println("Keyboard reset command sent successfully.");
            } catch (Exception e) {
                // Log the error but don't fail the test. The driver still needs to quit.
                System.err.println("Could not reset keyboard to GBoard via ADB: " + e.getMessage());
            }
            androidDriver.quit();
        }
    }

    public static void startServer() {
        System.out.println("Launching Appium in the background...");

        AppiumServiceBuilder builder = new AppiumServiceBuilder()
                .withIPAddress("127.0.0.1")
                .usingPort(4723)
                // mobile: shell (MobileSession, keyboard reset) needs adb_shell allowed
                .withArgument(() -> "--allow-insecure", "uiautomator2:adb_shell")
                // every command with its time ("<-- POST /element 200 1429 ms"), to see where a slow run goes;
                // copied to target/appium.log below (Appium's own --log file stays empty: it is killed at the end)
                .withArgument(() -> "--log-timestamp")
                .withArgument(() -> "--log-no-colors")
                // default 20 s: loading the drivers can take longer under Maven;
                // start() returns as soon as the server listens, so a fast start loses nothing
                .withTimeout(java.time.Duration.ofSeconds(60));

        service = AppiumDriverLocalService.buildService(builder);
        try {
            new java.io.File("target").mkdirs();
            service.addOutPutStream(new java.io.FileOutputStream("target/appium.log"));
        } catch (java.io.IOException e) {
            System.out.println("⚠️ No target/appium.log: " + e.getMessage());
        }
        service.start(); // Starts the server completely in the background

        System.out.println("Appium Server is up and running safely!");
    }

    public static void stopServer() {
        if (service != null && service.isRunning()) {
            service.stop();
            System.out.println("Appium Server stopped cleanly.");
        }
    }

    /**
     * The env of the run - a key of ENVIRONMENTS in the data file; {@code ${serverType}} (no -DserverType)
     * means Test. Sets the web URL every web test opens, and the hosts the API clients call.
     */
    @Parameters("server")
    public void setEnv(@Optional("Test") String server) {
        setServerType(Env.set(server));
        setBaseURL(dataModel().env().WEB_URL);
    }

}
