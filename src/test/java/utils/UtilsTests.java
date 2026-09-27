package utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ISuite;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.Parameters;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;

import static utils.MethodHandlesWeb.extent;
import static utils.MethodHandlesWeb.test;

public class UtilsTests {

    WebDriver driver;

    /**
     * The saved web login ({@link #saveSession()}): every localStorage key and every cookie of the app's origin,
     * per session name. {@link #loginProgrammatically(String)} writes it back before the app starts, so a test
     * opens logged in without the login page. Kept for the run; a new run logs in on the UI once again.
     */
    private record WebSession(String origin, java.util.Map<String, String> localStorage,
                              java.util.Set<org.openqa.selenium.Cookie> cookies) {}

    private static final java.util.Map<String, WebSession> SESSIONS = new java.util.HashMap<>();

    /** The name used when a test saves / replays one login only. */
    public static final String DEFAULT_SESSION = "default";

    /** Where the running suite's html, screenshots and videos go: report/<suite xml name>. */
    private static String reportDir = "report";
    private static String reportName = "report";

    /** Saves the browser's login under {@link #DEFAULT_SESSION} - call right after a UI login. */
    public void saveSession() {
        saveSession(DEFAULT_SESSION);
    }

    /** Saves the browser's login (localStorage + cookies of the current origin) under {@code name}. */
    @SuppressWarnings("unchecked")
    public void saveSession(String name) {
        java.net.URI current = java.net.URI.create(driver.getCurrentUrl());
        String origin = current.getScheme() + "://" + current.getAuthority();
        Object storage = ((JavascriptExecutor) driver).executeScript(
                "var o = {}; for (var i = 0; i < localStorage.length; i++) { var k = localStorage.key(i);"
                        + " o[k] = localStorage.getItem(k); } return o;");
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        if (storage instanceof java.util.Map) {
            ((java.util.Map<String, Object>) storage).forEach((k, v) -> values.put(k, v == null ? null : v.toString()));
        }
        SESSIONS.put(name, new WebSession(origin, values, new java.util.HashSet<>(driver.manage().getCookies())));
        System.out.println("Saved the web login \"" + name + "\" (" + values.size() + " localStorage keys, "
                + driver.manage().getCookies().size() + " cookies)");
    }

    /** True when {@link #saveSession(String)} kept a login under {@code name} in this run. */
    public static boolean hasSession(String name) {
        return SESSIONS.containsKey(name);
    }

    /** Drops a saved login - e.g. after a test changed that user's password. */
    public static void forgetSession(String name) {
        SESSIONS.remove(name);
    }

    public UtilsTests(WebDriver driver) {
        this.driver = driver;
    }

    /**
     * The last screen, or - when the browser / phone no longer answers (Chrome "Timed out receiving message
     * from renderer" on a Jenkins run) - a line in the entry saying why it is missing,
     * instead of an entry with no attachments and no explanation. The recording is attached either way.
     */
    public void saveLastScreenOrExplain(Method method) {
        try {
            takeScreenShot(method);
        } catch (Exception e) {
            String reason = String.valueOf(e.getMessage()).split("\n")[0];
            System.out.println("Could not save the last screen: " + reason);
            if (test != null) {
                test.warning("No last screen: the browser / phone did not answer (" + reason
                        + "). The recording shows the steps up to that point.");
            }
        }
    }

    public void takeScreenShot(Method method) throws IOException {
        File file = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        FileUtils.copyFile(file, new File(reportDir + "/" + testCaseFileName + ".png"));

    }

    /** Numbers the test cases of the running report in run order; reset when a new report is made. */
    private static int testCaseNumber;
    /**
     * File name of the running test case's screenshot, frames and GIF: {@code 3_verifyThat...}.
     * The number keeps them apart when a suite runs one method several times (a suite that
     * runs the same test method in seven blocks) - by method name alone, every
     * run overwrote the one before.
     */
    private static String testCaseFileName = "test";

    public static String getTestCaseFileName() {
        return testCaseFileName;
    }

    /**
     * One folder per env and suite file, from the suite's server parameter:
     * Test  + webLogin.xml -> report/testEnv/webLogin/webLogin.html
     * Stage + webLogin.xml -> report/stageEnv/webLogin/webLogin.html
     * with that suite's .png and .gif beside it, so the relative links inside the html keep working.
     * A class run straight from the IDE has no xml file, so it falls back to the suite name.
     */
    public static void setReportFolder(ISuite suite, String server) {
        reportEnv = data.Env.set(server);
        String envFolder = data.Env.reportFolder();
        String fileName = suite.getXmlSuite().getFileName();
        String name = fileName != null
                ? Paths.get(fileName).getFileName().toString().replaceFirst("(?i)\\.xml$", "")
                : suite.getName();
        reportName = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        suiteTests = suite.getAllMethods().size();
        reportDir = "report/" + envFolder + "/" + reportName;
        new File(reportDir).mkdirs();
    }

    public static String getReportDir() {
        return reportDir;
    }

    /** Test or Stage, from the suite's server parameter - shown on the dashboard and in every entry. */
    private static String reportEnv = "Test";

    /** The html the current ExtentReports writes to. */
    private static String reportFile;
    /** How many test methods the running suite has - for the live view's progress. */
    private static int suiteTests;

    /**
     * A hybrid suite runs both the web and the mobile @BeforeSuite: the second call finds the report
     * already made for this suite's file and keeps it, so web and phone tests land in one report.
     */
    public void createReport() {
        String file = reportDir + "/" + reportName + ".html";
        if (extent != null && file.equals(reportFile)) {
            return;
        }
        reportFile = file;
        testCaseNumber = 0;
        extent = new ExtentReports();
        ExtentSparkReporter spark = new ExtentSparkReporter(file);
        spark.config().setDocumentTitle("My Report");
        spark.config().setTheme(Theme.DARK);
        // links to real pages (the email's "open full size", links in logged steps) open in a new tab, so the
        // report - on its own or inside Jenkins - stays where it was; Extent's own # links and downloads don't
        spark.config().setJs("document.addEventListener('click',function(e){"
                + "var a=e.target.closest&&e.target.closest('a[href]');if(!a||a.hasAttribute('download'))return;"
                + "var h=a.getAttribute('href');if(!h||h.charAt(0)==='#'||h.indexOf('javascript:')===0)return;"
                + "a.target='_blank';a.rel='noopener';},true);");
        extent.attachReporter(spark);
        extent.setSystemInfo("Env", reportEnv);
        LiveFeed.run(reportName, reportEnv, suiteTests);

    }
    /** Record types, shown as the entry's category (the report's Tags view filters by them). */
    public static final String API = "API", WEB = "Web", MOBILE = "Mobile";

    /**
     * Which test the open report entry belongs to (see {@link #entryKey}), null when none is open.
     * API, web and mobile entries share one report and one numbering, so an entry must be closed
     * before the next opens - otherwise the next test's steps land under the wrong number.
     */
    private static String openEntry;
    /** The open entry's result, noted by {@link ReportListener} for when its @AfterMethod never runs. */
    private static ITestResult openEntryResult;

    /** The same key from a @BeforeMethod (method + context + parameters) and from an ITestResult. */
    public static String entryKey(ITestContext context, Class<?> testClass, String methodName, Object[] parameters) {
        return (context == null ? "" : context.getName()) + "#" + testClass.getName() + "." + methodName
                + java.util.Arrays.deepToString(parameters == null ? new Object[0] : parameters);
    }

    static boolean isOpenFor(ITestResult result) {
        return openEntry != null && openEntry.equals(entryKey(result.getTestContext(),
                result.getMethod().getConstructorOrMethod().getDeclaringClass(), result.getMethod().getMethodName(), result.getParameters()));
    }

    static void noteResult(ITestResult result) {
        if (isOpenFor(result)) {
            openEntryResult = result;
        }
    }

    public void createTestCaseInReport(Method method) {
        createTestCaseInReport(method, null, new Object[0], WEB, null);
    }

    /**
     * Adds the test case to the report as "3. verifyThat...", with the suite xml's {@code <test name>}
     * underneath, its type (API / Web / Mobile) as the category, and a table of what ran: type, test
     * block, class and method, device, and the parameters this run got - from the xml, or the method's
     * {@code @Optional} default when the xml has none - so a method run several times with different
     * values can be told apart. An entry left open by the test before (its @AfterMethod never ran) is
     * closed first.
     */
    public void createTestCaseInReport(Method method, ITestContext context, Object[] parameters,
                                       String type, String device) {
        closeOpenEntry();
        openEntry(method.getDeclaringClass(), method.getName(), method.getAnnotation(Parameters.class),
                context, parameters, type, device);
        openEntry = entryKey(context, method.getDeclaringClass(), method.getName(), parameters);
    }

    /**
     * Opens a numbered entry; {@link #createTestCaseInReport} for a test that runs,
     * {@link ReportListener} for one TestNG skipped before its @BeforeMethod could open it.
     */
    static void openEntry(Class<?> testClass, String methodName, Parameters names, ITestContext context,
                          Object[] parameters, String type, String device) {
        int number = ++testCaseNumber;
        testCaseFileName = number + "_" + methodName;
        String xmlTestName = context != null ? context.getName() : null;
        LiveFeed.testStarted(number, methodName, xmlTestName, type);
        test = xmlTestName != null
                ? extent.createTest(number + ". " + methodName, xmlTestName)
                : extent.createTest(number + ". " + methodName);
        test.assignCategory(type);
        if (device != null) {
            test.assignDevice(device);
        }

        java.util.List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[]{"<b>Type</b>", type});
        rows.add(new String[]{"<b>Env</b>", reportEnv});
        if (xmlTestName != null) {
            rows.add(new String[]{"<b>Test block</b>", xmlTestName});
        }
        rows.add(new String[]{"<b>Test</b>", testClass.getName() + "." + methodName});
        if (device != null) {
            rows.add(new String[]{"<b>Device</b>", device});
        }
        rows.add(new String[]{"<b>Started</b>", java.time.LocalTime.now().withNano(0).toString()});
        if (parameters != null) {
            for (int i = 0; i < parameters.length; i++) {
                String name = names != null && i < names.value().length ? names.value()[i] : "parameter " + (i + 1);
                rows.add(new String[]{name, String.valueOf(parameters[i])});
            }
        }
        test.info(MarkupHelper.createTable(rows.toArray(new String[0][])));
        test.info(MarkupHelper
                .createLabel("------------------- Steps To Reproduce -------------------", ExtentColor.TEAL));
    }

    public void endsOfSteps(){
        test.info(MarkupHelper
                .createLabel("------------------- Ends of Steps -------------------", ExtentColor.TEAL));
    }

    /**
     * Closes the running entry: the failure (message + stack trace) or skip reason, Ends of Steps,
     * pass / fail / skip. Afterwards nothing is logged until the next entry opens, so API calls made
     * between tests can't land in a finished one.
     */
    public void finishTestCase(ITestResult result) {
        if (test == null || openEntry == null) {
            return;
        }
        Throwable error = result.getThrowable();
        if (result.getStatus() == ITestResult.FAILURE && error != null) {
            test.fail(error);
        } else if (result.getStatus() == ITestResult.SKIP) {
            test.skip(MarkupHelper.createLabel("Skipped: " + skipReason(result), ExtentColor.ORANGE));
        }
        endsOfSteps();
        setStatus(result);
        LiveFeed.testFinished(result.getStatus() == ITestResult.SUCCESS ? "pass"
                        : result.getStatus() == ITestResult.FAILURE ? "fail" : "skip",
                error == null ? (result.getStatus() == ITestResult.SKIP ? skipReason(result) : "")
                        : String.valueOf(error).split("\n")[0]);
        openEntry = null;
        openEntryResult = null;
        test = null;
    }

    /** An entry still open when the next one starts or its test block ends: its @AfterMethod did not run. */
    static void closeOpenEntry() {
        if (openEntry == null || test == null) {
            openEntry = null;
            return;
        }
        if (openEntryResult != null) {
            new UtilsTests(null).finishTestCase(openEntryResult);
        } else {
            test.skip("Test did not finish - its clean-up step (@AfterMethod) did not run");
            LiveFeed.testFinished("skip", "did not finish - its clean-up step (@AfterMethod) did not run");
            openEntry = null;
            test = null;
        }
    }

    /** Why TestNG skipped it - its own reason, and the set-up step that failed before it, if any. */
    static String skipReason(ITestResult result) {
        StringBuilder reason = new StringBuilder();
        if (result.getThrowable() != null && result.getThrowable().getMessage() != null) {
            reason.append(result.getThrowable().getMessage());
        }
        for (ITestResult config : result.getTestContext().getFailedConfigurations().getAllResults()) {
            reason.append(reason.length() == 0 ? "" : " - ").append("set-up ")
                    .append(config.getMethod().getMethodName()).append(" failed");
            if (config.getThrowable() != null) {
                reason.append(": ").append(config.getThrowable().toString().split("\n")[0]);
            }
        }
        return reason.length() == 0 ? "no reason given by TestNG" : reason.toString();
    }

    public void setStatus(ITestResult result){
        if (result.getStatus() == ITestResult.SUCCESS){
            test.pass("Test Pass");
        } else if (result.getStatus() == ITestResult.FAILURE) {
            test.fail("Test Fail");
        } else if (result.getStatus() == ITestResult.SKIP) {
            test.skip("Test Skipped");
        }

    }
    /**
     * One shared script drives every recording player in the report. Extent writes each test's log
     * twice (the hidden list and the visible details view), so a player can't find itself by id: all
     * state lives on the {@code .aqp} element's data attributes, and the handlers are delegated from
     * {@code document}. The script guards itself, so the copy that every attachment carries runs once.
     */
    /** The attachments / player script, for other rows that add a card to the Attachments tab (EmailReport). */
    static String attachmentsScript() {
        return PLAYER_SCRIPT;
    }

    private static final String PLAYER_SCRIPT = "<script>(function(){if(window.__aqp)return;window.__aqp=1;"
            + "function q(f,c){return f.querySelector(c);}"
            + "function dur(f){return f.dataset.d.split(',');}"
            + "function show(f,k){var n=dur(f).length,i=(k%n+n)%n;f.dataset.i=i;f.dataset.t=Date.now();"
            + "q(f,'.aqp-i').src=f.dataset.base+('00'+i).slice(-3)+'.jpg';q(f,'.aqp-r').value=i;"
            + "q(f,'.aqp-c').textContent=(i+1)+' / '+n;}"
            + "function setOn(f,on){f.dataset.on=on?'1':'0';f.dataset.t=Date.now();"
            + "q(f,'[data-act=play]').innerHTML=on?'&#10074;&#10074;':'&#9654;';}"
            + "function step(f,by){setOn(f,false);show(f,+f.dataset.i+by);}"
            // only playing players that are on screen advance - a hidden test's player waits
            + "setInterval(function(){var now=Date.now();document.querySelectorAll('.aqp').forEach(function(f){"
            + "if(f.dataset.on!=='1'||f.offsetParent===null)return;"
            + "if(now-(+f.dataset.t||0)>=+dur(f)[+f.dataset.i])show(f,+f.dataset.i+1);});},50);"
            + "var st=document.createElement('style');st.textContent="
            + "'.aqp-tabs{display:flex;gap:2px;border-bottom:1px solid rgba(128,128,128,.25);margin:6px 0 14px}"
            + ".aqp-tab{background:none;border:0;border-bottom:2px solid transparent;margin-bottom:-1px;color:inherit;opacity:.6;"
            + "padding:9px 16px;font-size:13px;font-weight:600;cursor:pointer;display:flex;align-items:center;gap:7px}"
            + ".aqp-tab:hover{opacity:.9}.aqp-tab:focus{outline:none}"
            + ".aqp-tab.on{opacity:1;color:#4f8cff;border-bottom-color:#4f8cff}"
            + ".aqp-n{min-width:18px;padding:0 6px;border-radius:9px;background:rgba(79,140,255,.16);color:#4f8cff;font-size:11px;line-height:18px;text-align:center}"
            + ".aqp-media{display:flex;flex-wrap:wrap;gap:16px;align-items:stretch}"
            + ".aqp-card{flex:1 1 360px;min-width:0;margin:0;padding:12px;border-radius:8px;"
            + "background:rgba(128,128,128,.07);border:1px solid rgba(128,128,128,.2)}"
            + ".aqp-card-h{display:flex;align-items:center;gap:8px;margin-bottom:10px;font-size:11px;font-weight:700;"
            + "letter-spacing:.06em;text-transform:uppercase;opacity:.85}"
            + ".aqp-card-h small{margin-left:auto;font-weight:500;letter-spacing:0;text-transform:none;opacity:.7}"
            + ".aqp-dot{width:8px;height:8px;border-radius:50%;background:#4f8cff}.aqp-dot.rec{background:#ff2d55}"
            + ".aqp-ov .aqp-card{flex:none;background:none;border:0;padding:0}"
            // phone screens are tall: cap their height in the report (full screen is not inside .aqp-portrait)
            + ".aqp-portrait .aqp-i,.aqp-portrait .aqp-png{width:auto!important;max-width:100%;max-height:640px;margin:0 auto}"
            // full screen: the picture never pushes the control bar off the screen
            + ".aqp-ov .aqp-i{max-height:calc(100vh - 120px);object-fit:contain;background:#000}';"
            + "document.head.appendChild(st);"
            // Tabs: each media row leaves the log table for the panel of its tab - Timeline (data-tab="tl") or
            // Attachments (the default) - next to a Steps tab that shows the table. Tab bar, table and panels stay
            // adjacent siblings, so a redraw that copies the html keeps working.
            // Re-checked on the timer, because Extent redraws the details view (test click, Esc)
            + "function panels(table){var a=[],p=table.nextElementSibling;while(p&&p.classList.contains('aqp-panel')){a.push(p);p=p.nextElementSibling;}return a;}"
            + "function setTab(bar,t){var table=bar.nextElementSibling;if(!table)return;var ps=panels(table);if(!ps.length)return;"
            // an entry without the remembered tab (an API test has no Timeline) shows Steps
            + "if(t!=='steps'&&!ps.some(function(p){return p.dataset.tab===t;}))t='steps';"
            + "table.style.display=t==='steps'?'':'none';ps.forEach(function(p){p.style.display=p.dataset.tab===t?'':'none';});"
            + "bar.querySelectorAll('.aqp-tab').forEach(function(b){b.classList.toggle('on',b.dataset.tab===t);});}"
            + "function build(){document.querySelectorAll('.aqp-media').forEach(function(m){"
            + "if(m.dataset.built)return;var tr=m.closest('tr'),table=m.closest('table');if(!tr||!table)return;m.dataset.built='1';"
            + "var tab=m.dataset.tab||'att',bar=table.previousElementSibling;"
            + "if(!bar||!bar.classList.contains('aqp-tabs')){bar=document.createElement('div');bar.className='aqp-tabs';"
            + "bar.innerHTML='<button type=\"button\" class=\"aqp-tab\" data-tab=\"steps\">Steps</button>';table.parentNode.insertBefore(bar,table);}"
            // a second attachment row (e.g. the registration email next to the screenshot) joins the same panel
            + "var ps=panels(table),panel=ps.filter(function(p){return p.dataset.tab===tab;})[0];"
            + "if(!panel){panel=document.createElement('div');panel.className='aqp-panel';panel.dataset.tab=tab;"
            + "var last=ps.length?ps[ps.length-1]:table;last.parentNode.insertBefore(panel,last.nextSibling);"
            + "var b=document.createElement('button');b.type='button';b.className='aqp-tab';b.dataset.tab=tab;"
            + "b.innerHTML=(tab==='tl'?'Timeline':'Attachments')+'<span class=\"aqp-n\"></span>';"
            // Timeline comes right after Steps
            + "if(tab==='tl')bar.insertBefore(b,bar.children[1]||null);else bar.appendChild(b);}"
            + "panel.appendChild(m);tr.style.display='none';"
            + "var nb=bar.querySelector('.aqp-tab[data-tab=\"'+tab+'\"] .aqp-n');"
            + "if(nb)nb.textContent=tab==='tl'?(m.dataset.n||''):panel.querySelectorAll('.aqp-card').length;"
            + "setTab(bar,window.__aqpTab||'steps');});}"
            + "build();setInterval(build,300);"
            // full screen: an overlay on <body> - a copy of the player, or the screenshot; closing hands the frame back
            + "var ov=null,orig=null;"
            + "function overlay(){closeFull();ov=document.createElement('div');ov.className='aqp-ov';"
            + "ov.style.cssText='position:fixed;top:0;left:0;right:0;bottom:0;z-index:2147483647;background:rgba(0,0,0,.92);"
            + "display:flex;align-items:center;justify-content:center;color:#fff';document.body.appendChild(ov);return ov;}"
            + "function openFull(f){var i=+f.dataset.i,on=f.dataset.on==='1';setOn(f,false);"
            + "var img=q(f,'.aqp-i'),r=(img.naturalWidth/img.naturalHeight)||(16/9),c=f.cloneNode(true);"
            + "c.classList.add('aqp-big');c.style.cssText='flex:none;margin:0;outline:none;width:'+Math.min(innerWidth*.96,(innerHeight-120)*r)+'px';"
            + "q(c,'.aqp-i').style.cursor='pointer';var x=q(c,'[data-act=full]');x.dataset.act='close';x.title='Close (Esc)';x.innerHTML='&#10005;';"
            + "overlay().appendChild(c);orig=f;show(c,i);setOn(c,on);c.focus({preventScroll:true});}"
            // width / height auto + contain: Extent's own img styles stretched a phone screenshot to the window's width
            + "function openPng(src){var o=overlay();"
            + "o.innerHTML='<img src=\"'+src+'\" style=\"width:auto!important;height:auto!important;max-width:96vw;max-height:94vh;"
            + "object-fit:contain;display:block;border-radius:4px\">'"
            + "+'<button type=\"button\" data-act=\"close\" title=\"Close (Esc)\" style=\"position:absolute;top:14px;right:18px;"
            + "background:#374151;color:#fff;border:0;border-radius:4px;padding:4px 12px;cursor:pointer;font-size:16px\">&#10005;</button>';}"
            + "function closeFull(){if(!ov)return;var c=q(ov,'.aqp');ov.remove();ov=null;"
            + "if(c&&orig){show(orig,+c.dataset.i);setOn(orig,c.dataset.on==='1');}orig=null;}"
            + "document.addEventListener('click',function(e){var t=e.target;if(!t.closest)return;"
            + "if(ov&&t===ov){closeFull();return;}"
            // the chosen tab is remembered, so moving to another test opens the same tab
            + "var tb=t.closest('.aqp-tab');if(tb){window.__aqpTab=tb.dataset.tab;"
            + "document.querySelectorAll('.aqp-tabs').forEach(function(x){setTab(x,tb.dataset.tab);});return;}"
            + "var pg=t.closest('.aqp-png');if(pg){openPng(pg.getAttribute('src'));return;}"
            + "var b=t.closest('.aqp [data-act], .aqp-ov [data-act]'),im=t.closest('.aqp .aqp-i');"
            // the small picture opens full screen; the big one toggles play / pause like a video
            + "if(im){var g=im.closest('.aqp');if(g.classList.contains('aqp-big'))setOn(g,g.dataset.on!=='1');else openFull(g);return;}"
            + "if(!b)return;var a=b.dataset.act;if(a==='close'){closeFull();return;}var f=b.closest('.aqp');"
            + "if(a==='play')setOn(f,f.dataset.on!=='1');else if(a==='full')openFull(f);"
            + "else step(f,a==='back'?-1:1);});"
            + "document.addEventListener('input',function(e){if(!e.target.classList.contains('aqp-r'))return;"
            + "var f=e.target.closest('.aqp');setOn(f,false);show(f,+e.target.value);});"
            + "document.addEventListener('mouseover',function(e){var f=e.target.closest&&e.target.closest('.aqp');"
            + "if(f&&!f.contains(document.activeElement))f.focus({preventScroll:true});});"
            // Esc for full screen is caught first (capture on window): Extent's own Esc redraws the details view
            + "window.addEventListener('keydown',function(e){if(e.key==='Escape'&&ov){e.preventDefault();e.stopImmediatePropagation();closeFull();}},true);"
            + "document.addEventListener('keydown',function(e){"
            + "var a=document.activeElement,f=a&&a.closest&&a.closest('.aqp');"
            + "if(!f||a.classList.contains('aqp-r'))return;"
            + "if(e.key===' '){e.preventDefault();setOn(f,f.dataset.on!=='1');}"
            + "else if(e.key==='ArrowLeft'){e.preventDefault();step(f,-1);}"
            + "else if(e.key==='ArrowRight'){e.preventDefault();step(f,1);}});"
            + TestTimeline.SCRIPT
            + "})();</script>";

    /**
     * The Timeline tab of the entry ({@link TestTimeline}): steps with their locators, the browser's API calls
     * and page loads, checks, console errors and the failure, in order, next to the recording's frames.
     * Logged before the attachments, so its tab comes right after Steps.
     */
    public void addTimeline(ITestResult result) {
        if (test == null) {
            return;
        }
        // the live page's way back to this test's steps once the run is over (from the published report)
        LiveFeed.recording(BrowserGifRecorder.framesFolderName(testCaseFileName) + "/", BrowserGifRecorder.getLastFrameSteps());
        try {
            String html = TestTimeline.html(BrowserGifRecorder.framesFolderName(testCaseFileName) + "/",
                    BrowserGifRecorder.getLastFrameSteps(), BrowserGifRecorder.getLastFrameTimes(), result);
            if (html != null) {
                test.log(Status.INFO, html + PLAYER_SCRIPT);
            }
        } catch (Exception e) {
            // the Timeline is an extra - the attachments and the result must still go in
            System.out.println("Could not write the Timeline: " + e);
        }
    }

    /**
     * The last screen and the browser recording, side by side. The recording is a small player over
     * the frames in {@code <method>_frames/} - play / pause, one frame back / forward, and a slider to
     * scrub - plus a GIF download. Clicking the recording (or the full screen button) opens the same
     * player full screen, and clicking the screenshot opens it full size in the same way (no new tab);
     * Esc, the close button or a click beside it closes either. The row shows no Info badge or
     * timestamp - the script hides those cells and lets the pictures use the full width. Keys while the pointer
     * is over a player: space = play / pause, left / right arrows = one frame back / forward.
     */
    public void addAttachment(Method method){
        addAttachment(method, false);
    }

    /** Same, for a phone test: {@code portrait} keeps the tall screenshots and recording to a readable height. */
    public void addAttachment(Method method, boolean portrait){
        if (test == null) {
            return;
        }
        String name = testCaseFileName;
        String png = name + ".png";
        boolean hasPng = new File(reportDir, png).isFile();   // missing when the browser hung (saveLastScreenOrExplain)
        List<Integer> durations = BrowserGifRecorder.getLastFrameDurations();
        if (!hasPng && durations.isEmpty()) {
            return;   // nothing to show
        }

        String image = "width:100%;display:block;border:1px solid rgba(128,128,128,.3);border-radius:6px";

        StringBuilder html = new StringBuilder()
                .append(portrait ? "<div class='aqp-media aqp-portrait'>" : "<div class='aqp-media'>");
        if (hasPng) {
            html.append("<figure class='aqp-card'>")
                    .append("<div class='aqp-card-h'><span class='aqp-dot'></span>Last screen<small>click to enlarge</small></div>")
                    .append("<img class='aqp-png' title='Open full screen' src='").append(png).append("' style='")
                    .append(image).append(";cursor:zoom-in'>")
                    .append("</figure>");
        }

        if (!durations.isEmpty()) {
            String frames = BrowserGifRecorder.framesFolderName(name) + "/";
            String durationList = durations.toString().replaceAll("[\\[\\] ]", "");
            String button = "background:#374151;color:#fff;border:0;border-radius:4px;padding:3px 10px;cursor:pointer;font-size:13px";
            double seconds = durations.stream().mapToInt(Integer::intValue).sum() / 1000.0;

            html.append("<figure class='aqp aqp-card' tabindex='0' data-base='").append(frames)
                    .append("' data-d='").append(durationList).append("' data-i='0' data-on='1' style='outline:none'>")
                    .append("<div class='aqp-card-h'><span class='aqp-dot rec'></span>Recording<small>")
                    .append(durations.size()).append(" frames &middot; ").append(String.format(java.util.Locale.ROOT, "%.1f", seconds))
                    .append(" s</small></div>")
                    .append("<img class='aqp-i' title='Open full screen' src='").append(frames).append("000.jpg' style='")
                    .append(image).append(";cursor:zoom-in'>")
                    .append("<div style='display:flex;align-items:center;gap:6px;margin-top:6px'>")
                    .append("<button type='button' data-act='back' title='One frame back' style='").append(button).append("'>&#9664;&#9664;</button>")
                    .append("<button type='button' data-act='play' title='Play / pause' style='").append(button).append(";min-width:40px'>&#10074;&#10074;</button>")
                    .append("<button type='button' data-act='fwd' title='One frame forward' style='").append(button).append("'>&#9654;&#9654;</button>")
                    .append("<input type='range' class='aqp-r' min='0' max='").append(durations.size() - 1)
                    .append("' value='0' style='flex:1;min-width:60px'>")
                    .append("<span class='aqp-c' style='font-size:12px;white-space:nowrap;font-variant-numeric:tabular-nums'>1 / ")
                    .append(durations.size()).append("</span>")
                    .append("<button type='button' data-act='full' title='Full screen' style='").append(button).append("'>&#10530;</button>")
                    .append("<a href='").append(name).append(".gif' download style='font-size:12px;white-space:nowrap'>Download GIF</a>")
                    .append("</div></figure>");
        }

        html.append("</div>").append(PLAYER_SCRIPT);
        test.log(Status.INFO, html.toString());
    }

    /**
     * A screenshot taken mid-test (e.g. the report once its figures are read), saved beside the report as
     * {@code <n>_<method>_<title>.png} and added as a card to the entry's Attachments tab, next to the
     * last screen and the recording. Click it to open it full size, like the last screen.
     */
    public void attachScreenshot(String title) {
        if (test == null || driver == null) {
            return;
        }
        String png = testCaseFileName + "_" + title.replaceAll("[^A-Za-z0-9]+", "_") + ".png";
        try {
            File file = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(file, new File(reportDir + "/" + png));
        } catch (Exception e) {
            System.out.println("Could not take the " + title + " screenshot: " + e.getMessage());
            return;
        }
        test.log(Status.INFO, "<div class='aqp-media'><figure class='aqp-card'>"
                + "<div class='aqp-card-h'><span class='aqp-dot'></span>" + title + "<small>click to enlarge</small></div>"
                + "<img class='aqp-png' title='Open full screen' src='" + png + "' style='width:100%;display:block;"
                + "border:1px solid rgba(128,128,128,.3);border-radius:6px;cursor:zoom-in'>"
                + "</figure></div>" + PLAYER_SCRIPT);
    }

    public void flushReport(){
        BrowserGifRecorder.awaitGifs(); // recordings' GIFs are built in the background after each test
        extent.flush();
    }


    /** Replays the login saved under {@link #DEFAULT_SESSION} and opens {@code path} (e.g. "/inventory.html"). */
    public void loginProgrammatically(String path) {
        loginProgrammatically(DEFAULT_SESSION, path);
    }

    /**
     * Writes a saved login ({@link #saveSession(String)}) back before any app code runs, then opens {@code path}
     * on the app's origin - the test starts logged in, without the login page.
     *
     * <p>Writing localStorage on the live app and refreshing races the app's own start-up: an app loaded
     * without a login may still be waiting on its first call, and when that answers after the keys were
     * written it logs out - deleting them - so the refresh lands on the login page.
     *
     * <p>Cookies go in first (the browser must be on the origin for that; BaseTests opened WEB_URL already).
     * Chrome / Edge: the localStorage keys go in through a DevTools script that runs at the very start of the
     * next page load, before the app's own code, and is removed right after - no extra page is shown.
     * Any other browser: {@code /robots.txt} of the same origin (same localStorage, no app code) takes the
     * keys first - it flashes on screen for a moment, but nothing can wipe them.
     */
    public void loginProgrammatically(String name, String path) {
        WebSession session = SESSIONS.get(name);
        if (session == null) {
            throw new IllegalStateException("No saved web login \"" + name + "\": log in on the UI once and call saveSession(\""
                    + name + "\") first");
        }
        String origin = session.origin();
        String target = origin + (path == null || path.isBlank() ? "/" : path.startsWith("/") ? path : "/" + path);

        if (!driver.getCurrentUrl().startsWith(origin)) {
            driver.get(origin + "/robots.txt");
        }
        for (org.openqa.selenium.Cookie cookie : session.cookies()) {
            try {
                driver.manage().addCookie(cookie);
            } catch (Exception e) {
                System.out.println("Could not put back the cookie " + cookie.getName() + ": " + e.getMessage().split("\n")[0]);
            }
        }

        com.google.gson.Gson gson = new com.google.gson.Gson();
        // exactly the saved state: keys a later test added (a cart, a draft) must not come along
        StringBuilder writes = new StringBuilder("localStorage.clear();");
        session.localStorage().forEach((k, v) -> writes.append("localStorage.setItem(").append(gson.toJson(k))
                .append(", ").append(gson.toJson(v)).append(");"));

        if (driver instanceof org.openqa.selenium.chromium.ChromiumDriver chromium) {
            // only on the app's own origin - not in the reCAPTCHA / other frames the page loads
            String script = "if (location.origin === " + gson.toJson(origin) + ") {" + writes + "}";
            Object id = chromium.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                    java.util.Map.of("source", script)).get("identifier");
            try {
                driver.get(target);
            } finally {
                // one-shot: later loads (and a test that logs out) must not get the keys again
                chromium.executeCdpCommand("Page.removeScriptToEvaluateOnNewDocument",
                        java.util.Map.of("identifier", id));
            }
            return;
        }

        driver.get(origin + "/robots.txt");
        ((JavascriptExecutor) driver).executeScript(writes.toString());
        driver.get(target);
    }

    /**
     * The work day for one whole suite run, rolled forward by one day per run.
     *
     * <p>Stored in {@code data/workDate.txt}, format {@code yyyy-MM-dd}; the first run uses
     * {@code -DworkDateSeed=yyyy-MM-dd}, or today when that is not given:
     * <pre>
     *   1st run: the seed
     *   2nd run: the seed + 1 day
     *   3rd run: the seed + 2 days   ... and so on, whenever it is actually run
     * </pre>
     *
     * <p>This is NOT {@link #getNextDynamicTestDate()}. That one is built once per test class
     * instance, which TestNG makes once per {@code <test>} tag, so a suite with eight blocks would
     * move eight days and every block would look at a different day. This one is built once per run,
     * on the first {@code workDate="auto"}, and handed to every block of the run, which is what a
     * scenario spread over several blocks needs - a run works one day, the next run works the next.
     *
     * <p>Reached from a suite file with {@code <parameter name="workDate" value="auto"/>} - no file
     * has to be edited between runs.
     */
    public String getNextSuiteWorkDate() {
        String seed = System.getProperty("workDateSeed", "");
        return getNextSuiteWorkDate("data/workDate.txt",
                seed.matches("\\d{4}-\\d{2}-\\d{2}") ? seed : LocalDate.now().toString());
    }

    /**
     * Same as {@link #getNextSuiteWorkDate()}, with its own file and first day - for
     * {@code workDate="auto:yyyy-MM-dd"}, e.g. {@code auto:1910-01-01} keeps
     * {@code data/workDate-1910-01-01.txt}: 1st run 1910-01-01, 2nd run 1910-01-02, ...
     */
    public String getNextSuiteWorkDate(String dateFilePath, String seedDate) {
        String next = rollDate(dateFilePath, seedDate);
        System.out.println("🗓️ Work date (this run): " + next);
        return next;
    }

    /**
     * Gets the next test date by incrementing by one day with each execution
     * (data/testDate.txt, first execution 1900-01-11), regardless of when it is run.
     */
    public String getNextDynamicTestDate() {
        String next = rollDate("data/testDate.txt", "1900-01-11");
        System.out.println("🗓️ Test Date (current execution): " + next);
        return next;
    }

    /**
     * Reads the stored day, adds one, writes it back and returns it; a missing file starts at the seed.
     *
     * <p>Jenkins runs a job on whichever of its agents is free, and each agent has its own workspace, so
     * the date files would split into one counter per agent and two agents would hand out the same day.
     * {@code -DdateDir=<folder>} makes every agent use one folder instead, and the file is locked while
     * it is read and written, so two runs at the same moment still get different days. The first run
     * with a new {@code dateDir} continues from the workspace's own {@code data/} file when there is one.
     */
    private static String rollDate(String dateFilePath, String seedDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        File local = new File(dateFilePath);
        String dir = System.getProperty("dateDir", "");
        File file = dir.isBlank() || dir.startsWith("${") ? local : new File(dir, local.getName());
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "rw");
                 java.nio.channels.FileLock ignored = raf.getChannel().lock()) {
                byte[] bytes = new byte[(int) raf.length()];
                raf.readFully(bytes);
                String last = new String(bytes).trim();
                if (last.isEmpty() && !file.equals(local) && local.isFile()) {
                    last = new String(Files.readAllBytes(local.toPath())).trim();
                }
                LocalDate next = last.isEmpty()
                        ? LocalDate.parse(seedDate, formatter)
                        : LocalDate.parse(last, formatter).plusDays(1);
                System.out.println(last.isEmpty()
                        ? "📁 First run - starting from " + seedDate + " (" + file + ")"
                        : "📁 Last run used " + last + " → this run uses " + next.format(formatter) + " (" + file + ")");
                raf.setLength(0);
                raf.write(next.format(formatter).getBytes());
                return next.format(formatter);
            }
        } catch (IOException e) {
            System.out.println("❌ Error reading/writing " + file + ". Using " + seedDate + ".");
            e.printStackTrace();
            return seedDate;
        }
    }

}
