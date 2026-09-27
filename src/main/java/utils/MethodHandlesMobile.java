package utils;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mobile-specific helper similar in purpose to the existing MethodHandles but
 * focused on Appium/mobile flows. Phase 1 provides Actions-based click
 * and sendKeys plus a getText helper, and tapAtOffset for precise coordinate taps.
 */
public class MethodHandlesMobile {

    protected WebDriverWait wait;
    private final AndroidDriver androidDriver;
    // Global toaster message for mobile flows
    protected final By toasterMessage =
            AppiumBy.xpath("//android.widget.ImageView[@resource-id='error_snackbar' or @resource-id='success_snackbar']");

    public MethodHandlesMobile(AndroidDriver androidDriver) {
        this.androidDriver = androidDriver;
    }

    // Short verification timeout (milliseconds) used after a click to detect
    // whether the UI changed. Keep this small to avoid adding test time.
    // How long a click waits for its element to go stale (= the tap landed) before tapping once more.
    // Returns as soon as the screen changes. Was 250 ms, which only worked while waitForIdleTimeout (4 s)
    // made every check wait for the app: with that at 0, a slow submit got a second tap that undid
    // the first one.
    private static final int SHORT_CLICK_VERIFY_MS = 3000;

    protected void explicitWait(By locator, int time, int sleep) {
        wait = new WebDriverWait(androidDriver, Duration.ofSeconds(time), Duration.ofMillis(sleep));
        wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    protected WebElement webElement(By locator) {
        return androidDriver.findElement(locator);
    }

    /** Called when a step is about to act on an element - the phone recorder draws its numbered box. */
    public interface StepListener {
        void onStep(Rectangle rect, int number, String action);
    }

    /** Numbers the steps of the running test; reset when a recording starts. */
    private static int stepNumber;
    private static StepListener onStep;

    public static void resetStepNumber() {
        stepNumber = 0;
    }

    public static void setOnStep(StepListener listener) {
        onStep = listener;
    }

    /**
     * Mobile twin of MethodHandlesWeb's step highlight: numbers the step and hands the element's
     * position and the action ("click", "type") to the recorder. Cosmetic - it never fails a test.
     */
    private static void markStep(WebElement element) {
        String action = actionLabel(Thread.currentThread().getStackTrace()[2].getMethodName());
        int number = ++stepNumber;
        StepListener listener = onStep;
        if (listener == null) {
            return;
        }
        try {
            listener.onStep(element.getRect(), number, action);
        } catch (Exception ignored) {
            // highlighting is cosmetic
        }
    }

    private static String actionLabel(String helper) {
        String h = helper.toLowerCase();
        if (h.startsWith("sendkeys")) return "type";
        if (h.startsWith("get") || h.startsWith("is")) return "check";
        return "click";
    }

    /** Logs the screen method that ran (e.g. insertAccountCode) as a step in the report, as the web does. */
    protected void setStep() {
        if (MethodHandlesWeb.test != null) {
            MethodHandlesWeb.test.info(getMethodName());
        }
    }

    private static String getMethodName() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (stackTraceElements.length >= 5) {
            return stackTraceElements[4].getMethodName();
        }
        return stackTraceElements.length >= 2 ? stackTraceElements[2].getMethodName() : "Unknown";
    }


    /**
     * Clicks an element using Actions (moveToElement + click). Waits until element is clickable.
     */
    // How often a wait looks for its element again. Was 2000 ms: every element not on screen at the
    // first look cost a full 2 s (9 times in one run).
    private static final int POLL_MS = 250;

    /**
     * Waits until the screen stops changing: the same texts (every content-desc on the page) for
     * {@code quietMs} in a row, at most {@code maxSeconds}. For a screen that loads in steps and rebuilds
     * itself - acting in between lands on a screen that is about to be replaced.
     */
    protected void waitForScreenToSettle(int quietMs, int maxSeconds) {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        String last = null;
        long unchangedSince = System.currentTimeMillis();
        while (System.currentTimeMillis() < deadline) {
            Matcher desc = CONTENT_DESC.matcher(androidDriver.getPageSource());
            StringBuilder texts = new StringBuilder();
            while (desc.find()) {
                texts.append(desc.group(1)).append('|');
            }
            long now = System.currentTimeMillis();
            if (!texts.toString().equals(last)) {
                last = texts.toString();
                unchangedSince = now;
            } else if (now - unchangedSince >= quietMs) {
                return;
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println("⚠️ Screen still changing after " + maxSeconds + " s - going on");
    }

    /** True when the element is there within {@code seconds}; no step logged, nothing tapped. */
    protected boolean isPresent(By locator, int seconds) {
        try {
            new WebDriverWait(androidDriver, Duration.ofSeconds(seconds), Duration.ofMillis(POLL_MS))
                    .until(ExpectedConditions.presenceOfElementLocated(locator));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * Closes and reopens the app (still logged in). A fresh start reloads everything from the server -
     * the only thing that made the home screen show a newly created record (pull to refresh and
     * switching tabs fetched nothing).
     */
    protected void restartApp() {
        String app = androidDriver.getCurrentPackage();
        // Shell, not terminateApp/activateApp: activateApp left the app closed for ~70 s.
        // Needs the server's uiautomator2:adb_shell (BaseTests starts Appium with it).
        shell("am", "force-stop", app);
        shell("monkey", "-p", app, "-c", "android.intent.category.LAUNCHER", "1");
    }

    private void shell(String command, String... args) {
        androidDriver.executeScript("mobile: shell", Map.of("command", command, "args", List.of(args)));
    }

    /** Actions click; a native tap at the element's centre if that fails. Throws if both fail. */
    private void tap(WebElement element) {
        try {
            new Actions(androidDriver).moveToElement(element).click().build().perform();
        } catch (Exception e) {
            try {
                Rectangle r = element.getRect();
                int centerX = r.getX() + (r.getWidth() / 2);
                int centerY = r.getY() + (r.getHeight() / 2);

                PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
                Sequence tap = new Sequence(finger, 1);
                tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), centerX, centerY));
                tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
                tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

                androidDriver.perform(Collections.singletonList(tap));
            } catch (Exception ignore) {
                // If fallback also fails, rethrow the original to surface the issue
                throw e;
            }
        }
    }

    /** One native tap at a screen point - for an icon that has no accessibility node to find. */
    protected void tapPoint(int x, int y) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);
        tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y));
        tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        androidDriver.perform(Collections.singletonList(tap));
        setStep();
    }

    /** Closes the keyboard if one is open, so it cannot cover a button below the field. */
    protected void hideKeyboardIfShown() {
        try {
            if (androidDriver.isKeyboardShown()) {
                androidDriver.hideKeyboard();
            }
        } catch (Exception ignored) {
            // no keyboard to hide
        }
    }

    /**
     * Exactly one tap - for buttons that submit or navigate (a submit button, a confirm action, back
     * to home). {@link #click} taps again when the element has not gone stale within 3 s; on these
     * that second tap submitted or skipped a screen on its own (the next screen's action button can
     * sit where the first button was). A stale element before the tap is still found again, so a redraw
     * cannot swallow the tap.
     */
    protected void clickOnce(By locator, int time) {
        tapOnce(locator, time);
        setStep();
    }

    // setStep stays in the callers: it logs the screen method two frames up
    private void tapOnce(By locator, int time) {
        explicitWait(locator, time, POLL_MS);
        for (int attempt = 1; ; attempt++) {
            try {
                WebElement element = webElement(locator);
                markStep(element);
                tap(element);
                break;
            } catch (StaleElementReferenceException | NoSuchElementException stale) {
                // redrawn, or gone for a moment between the wait and the find: home reloads right after
                // login and a home button was missing 100 ms after it showed (over USB)
                if (attempt == 3) {
                    throw stale;
                }
                explicitWait(locator, time, POLL_MS);
            }
        }
    }

    /**
     * One tap on a text field, to focus it before {@link #sendKeys}. {@link #click} waits up to 3 s for the
     * element to go stale and then taps again - a field never goes stale, so every field paid 3 s, and on a
     * fast USB run the second tap closed the bottom sheet the field was on.
     */
    protected void tapField(By locator, int time) {
        tapOnce(locator, time);
        setStep();
    }

    protected void click(By locator, int time) {
        // Ensure the element is present before interacting
        explicitWait(locator, time, POLL_MS);

        // The screen can redraw between finding the element and tapping it (the home screen reloads
        // its data right after a submit), which leaves a stale element: find it again
        // and tap, up to 3 times. Without this the next tap was lost.
        WebElement element = null;
        for (int attempt = 1; ; attempt++) {
            try {
                element = webElement(locator);
                markStep(element);
                tap(element);
                break;
            } catch (StaleElementReferenceException stale) {
                if (attempt == 3) {
                    throw stale;
                }
                explicitWait(locator, time, POLL_MS);
            }
        }

        // Quickly verify whether the click caused a UI change. Keep this check very short
        // to avoid adding latency to test runs. If the element does NOT become stale within
        // the short timeout, perform one quick retry tap.
        try {
            WebDriverWait shortWait = new WebDriverWait(androidDriver, Duration.ofMillis(SHORT_CLICK_VERIFY_MS));
            // If the element becomes stale quickly, the click had effect and we return.
            shortWait.until(org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf(element));
        } catch (org.openqa.selenium.TimeoutException timeout) {
            // Element didn't become stale within the short timeout -> retry a native tap
            try {
                Rectangle r = element.getRect();
                int centerX = r.getX() + (r.getWidth() / 2);
                int centerY = r.getY() + (r.getHeight() / 2);

                PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
                Sequence tap = new Sequence(finger, 1);
                tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), centerX, centerY));
                tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
                tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

                androidDriver.perform(Collections.singletonList(tap));
            } catch (Exception ignore) {
                // best-effort retry — swallow to avoid test slowdown
            }
        } catch (Exception ignored) {
            // ignore any other unexpected exception from the quick verification
        }
        setStep();
    }

    /**
     * Mobile equivalent of MethodHandlesWeb.smartClick.
     * Performs a single attempt Actions click and falls back to a native touch tap
     * if Actions fails. Does NOT perform verification or multiple retries to keep
     * execution time low.
     */
    protected void smartClick(By locator, int time) {
        // Ensure the element is present quickly before interacting
        explicitWait(locator, time, 200);

        Actions actions = new Actions(androidDriver);

        try {
            WebElement element = webElement(locator);
            markStep(element);
            // Try Actions-based click first
            actions.moveToElement(element).pause(Duration.ofMillis(200)).click().build().perform();
        } catch (Exception e) {
            // Fallback to a quick native tap at element center
            try {
                WebElement element = webElement(locator);
                Rectangle r = element.getRect();
                int centerX = r.getX() + (r.getWidth() / 2);
                int centerY = r.getY() + (r.getHeight() / 2);

                PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
                Sequence tap = new Sequence(finger, 1);
                tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), centerX, centerY));
                tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
                tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

                androidDriver.perform(Collections.singletonList(tap));
            } catch (Exception e2) {
                throw new RuntimeException("Smart click failed for locator: " + locator.toString(), e2);
            }
        }
        setStep();
    }

    /**
     * Sends keys to an element. For Flutter apps, this uses a reliable
     * "select all" and overwrite strategy to clear the field before typing.
     */
    protected void sendKeys(By locator, String text, int time) {
        wait = new WebDriverWait(androidDriver, Duration.ofSeconds(time));
        explicitWait(locator, time, POLL_MS);
        markStep(webElement(locator));

        // A more robust way to clear text in Flutter is to select all and overwrite.
        // This avoids issues with cursor position or counting backspaces.
        Actions actions = new Actions(androidDriver);

        // Send CTRL+A to select all existing text.
        actions.keyDown(Keys.CONTROL).sendKeys("a").keyUp(Keys.CONTROL);

        // Send the new text, which overwrites the selection, and then perform.
        actions.sendKeys(text).perform();
        setStep();
    }

    /**
     * Checks if an element is interactable by attempting to move to it.
     * This is a reliable way to check for element presence in Flutter apps.
     *
     * @return True if the element can be moved to, false otherwise.
     */
    protected boolean isDisplayed(By locator, int time) {
        try {
            explicitWait(locator, time, 500);
            WebElement element = webElement(locator);
            markStep(element);
            Actions actions = new Actions(androidDriver);
            actions.moveToElement(element).perform();
            setStep();
            return true;
        } catch (Exception e) {
            System.out.println(locator + "❌ is Not appeared");
            // If we hit an exception while checking display, treat as not displayed
            return false;
        }
    }

    /**
     * Scrolls inside {@code list} (not the whole screen) until {@code item} shows as a full row, then returns.
     * A clipped row is still in the tree with a shorter height (Live-Dr was 88 px, the others 96), so "found"
     * is not enough: the row must be at least 90% of the tallest row showing. Swipes towards the end first,
     * then back towards the start, {@code maxSwipes} each way.
     */
    protected void scrollIntoViewWithin(By list, By item, int maxSwipes) {
        explicitWait(list, 10, POLL_MS);
        for (int swipe = 0; swipe <= 2 * maxSwipes; swipe++) {
            List<WebElement> found = androidDriver.findElements(item);
            if (!found.isEmpty() && isFullRow(found.get(0))) {
                return;
            }
            if (swipe < 2 * maxSwipes) {
                swipeWithin(webElement(list).getRect(), swipe < maxSwipes);
            }
        }
        throw new NoSuchElementException(item + " not shown in " + list + " after " + maxSwipes + " swipes each way");
    }

    private boolean isFullRow(WebElement element) {
        try {
            int height = element.getRect().getHeight();
            // compared with the other rows of the same class
            String siblings = "//" + element.getAttribute("class");
            int tallest = androidDriver.findElements(By.xpath(siblings)).stream()
                    .mapToInt(e -> e.getRect().getHeight()).max().orElse(height);
            return height > 0 && height >= tallest * 0.9;
        } catch (StaleElementReferenceException redrawn) {
            return false;
        }
    }

    /** One slow swipe inside {@code area}: {@code revealBelow} moves the content up to show what is below. */
    protected void swipeWithin(Rectangle area, boolean revealBelow) {
        int x = area.getX() + area.getWidth() / 2;
        int low = area.getY() + (int) (area.getHeight() * 0.8);
        int high = area.getY() + (int) (area.getHeight() * 0.2);
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 1);
        swipe.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, revealBelow ? low : high));
        swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        swipe.addAction(finger.createPointerMove(Duration.ofMillis(600), PointerInput.Origin.viewport(), x, revealBelow ? high : low));
        swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        androidDriver.perform(Collections.singletonList(swipe));
    }

    /**
     * Scrolls to an element and clicks it. It will swipe a maximum of 10 times.
     */
    protected void scrollAndClick(By locator) {
        int swipes = 0;
        while (swipes < 10) {
            // Use findElements to avoid throwing an exception if the element is not yet visible.
            List<WebElement> elements = androidDriver.findElements(locator);
            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                click(locator, 5);
                return; // Exit the method once the element is found and clicked.
            }
            // Perform a swipe gesture to scroll down.
            swipeDown();
            swipes++;
        }
        // If the element is not found after 10 swipes, throw an exception.
        throw new NoSuchElementException("Element with locator " + locator + " not found after 10 swipes.");
    }

    /**
     * Performs a swipe down gesture on the screen.
     */
    private void swipeDown() {
        Dimension size = androidDriver.manage().window().getSize();
        int startX = size.getWidth() / 2;
        int startY = (int) (size.getHeight() * 0.8);
        int endY = (int) (size.getHeight() * 0.2);

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 1);
        swipe.addAction(finger.createPointerMove(Duration.ofMillis(0), PointerInput.Origin.viewport(), startX, startY));
        swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        swipe.addAction(finger.createPointerMove(Duration.ofMillis(1000), PointerInput.Origin.viewport(), startX, endY));
        swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        androidDriver.perform(Collections.singletonList(swipe));
    }

    /**
     * Read a toaster / message element (Flutter or mobile) and store it in the global
     * toasterMessage field for tests/pages to reuse.
     */
    protected String getToasterContent(By locator) {
        return getFlutterText(locator);
    }

    /**
     * Waits for toaster to appear first, then waits for it to disappear
     * This replaces Thread.sleep() with dynamic waiting
     */
    protected void waitForToasterToAppear() {
        // Step 1: Wait for toaster to appear (1-2 seconds max)
        try {
            wait = new WebDriverWait(androidDriver, Duration.ofSeconds(5));
            wait.until(ExpectedConditions.presenceOfElementLocated(toasterMessage));
            System.out.println("✅ Toaster appeared");

        } catch (Exception e) {
            System.out.println("❌ Toaster Not appeared");
        }

    }

    protected void clickFlutterLeftAsset(By locator) {
        // 1. Find the target element using the passed locator
        WebElement element = androidDriver.findElement(locator);
        markStep(element);

        // 2. Get its exact screen position boundaries
        Rectangle rect = element.getRect();

        // 3. Calculate the target point:
        // Move 50 pixels to the right of the element's left edge (X)
        // Move to the exact middle vertically (Y)
        int clickX = rect.getX() + 50;
        int clickY = rect.getY() + (rect.getHeight() / 2);

        // 4. Construct and perform the precise native finger tap gesture
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1);

        tap.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), clickX, clickY));
        tap.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));

        androidDriver.perform(Collections.singletonList(tap));
        setStep();
    }


    protected String getFlutterText(By locator) {
        // Use explicit wait to ensure element is present
        explicitWait(locator, 15, 500);
        WebElement element = androidDriver.findElement(locator);
        markStep(element);
        setStep();

        // 1. Check content-desc first (standard for Flutter elements)
        String text = element.getAttribute("content-desc");

        // 2. Fallback to standard text attribute if content-desc is empty or null
        if (text == null || text.trim().isEmpty()) {
            text = element.getAttribute("text");
        }

        return text != null ? text.trim() : "";
    }

    public String getToasterMessage() {
        waitForToasterToAppear();
        return getToasterContent(toasterMessage);
    }

    /**
     * The snackbar reads "&lt;title&gt;, &lt;message&gt;", and on the English success toasts both are the same
     * text ("Saved successfully, Saved successfully"),
     * so this returns it once. Anything else comes back unchanged.
     */
    public String getToasterMessageOnce() {
        String text = readToasterText(10);
        int middle = text.length() / 2;
        if (text.length() > 2 && text.startsWith(", ", middle - 1)) {
            String first = text.substring(0, middle - 1);
            if (first.equals(text.substring(middle + 1))) {
                return first;
            }
        }
        return text;
    }

    private static final Pattern SNACKBAR_NODE =
            Pattern.compile("<[^>]*resource-id=\"(?:success|error)_snackbar\"[^>]*>");
    private static final Pattern CONTENT_DESC = Pattern.compile("content-desc=\"([^\"]*)\"");

    /**
     * The snackbar shows ~3 s, and a phone call takes 0.3 s on a good run but 2-5 s on a slow one.
     * Find-then-read is two calls, and the toast went stale between them. So this reads one
     * snapshot of the screen (page source) and takes the snackbar's content-desc out of it: nothing to go
     * stale. The step box is drawn afterwards if the toast is still there.
     */
    private String readToasterText(int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Matcher node = SNACKBAR_NODE.matcher(androidDriver.getPageSource());
            if (node.find()) {
                Matcher desc = CONTENT_DESC.matcher(node.group());
                if (desc.find() && !desc.group(1).isBlank()) {
                    try {
                        markStep(androidDriver.findElement(toasterMessage));
                        setStep();
                    } catch (Exception gone) {
                        // the box is for the recording only; the toast may already be gone
                    }
                    return unescapeXml(desc.group(1)).trim();
                }
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new org.openqa.selenium.TimeoutException("No toast within " + seconds + " s");
    }

    private static String unescapeXml(String value) {
        return value.replace("&#10;", "\n").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    public boolean toasterMessageIsDisplayed() {
        waitForToasterToAppear();
        return isDisplayed(toasterMessage, 10);

    }

}