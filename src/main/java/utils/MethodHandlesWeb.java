package utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.testng.Assert.*;

public class MethodHandlesWeb {

    @Setter
    @Getter
    public static String URL;


    protected WebDriver driver;
    WebDriverWait wait;
    static ExtentReports extent;
    static ExtentTest test;
    Select select;
    Actions actions;

    // General Locators - ngx-toastr's container and alert; a page object of an app with other toasts sets its own
    protected By toaster = By.id("toast-container");
    protected By toasterContent = By.xpath("//div[@role='alert']");


    public MethodHandlesWeb(WebDriver driver) {
        this.driver = driver;
    }

    /**
     * Opens a page and waits for its header. The whole app can hang on its loading spinner for 20 s+ on a slow
     * Test server (nothing but the spinner) - reload once and
     * wait again before failing.
     */
    protected void openPage(String url, By header) {
        driver.get(url);
        try {
            isDisplayed(header, 20);
        } catch (TimeoutException stuck) {
            System.out.println("⏳ " + url + " still loading after 20 s - reloading it once");
            driver.navigate().refresh();
            isDisplayed(header, 30);
        }
    }

    protected WebElement webElement(By locator) {
        return driver.findElement(locator);
    }

    protected List<WebElement> webElements(By locator) {
        return driver.findElements(locator);
    }

    protected void explicitWait(By locator, int time) {
        wait = new WebDriverWait(driver, Duration.ofSeconds(time));
        wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    /**
     * Waits until the element is gone or hidden - "already gone" counts. It used to look the element up
     * first (webElement), which threw NoSuchElementException exactly when it had already disappeared
     * (a dialog that closed before the check ran).
     */
    protected void invisibilityOf(By locator, int time) {
        wait = new WebDriverWait(driver, Duration.ofSeconds(time));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public String getToasterContent() {
        return getText(toasterContent, 5);
    }

    /**
     * Waits for toaster to appear first, then waits for it to disappear
     * This replaces Thread.sleep() with dynamic waiting
     */
    public void waitForToasterToAppearAndDisappear() {
        try {
            // Step 1: Wait for toaster to appear (1-2 seconds max)
            wait = new WebDriverWait(driver, Duration.ofSeconds(2));
            wait.until(ExpectedConditions.visibilityOfElementLocated(toaster));
            System.out.println("✅ Toaster appeared");
            highlightToast();

            // Step 2: Wait for toaster to disappear (up to 5 seconds)
            wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            wait.until(ExpectedConditions.invisibilityOfElementLocated(toaster));
            System.out.println("✅ Toaster disappeared");

        } catch (Exception e) {
            // If toaster doesn't appear within 2 seconds, that's fine - just continue
            System.out.println("ℹ️ Toaster didn't appear within 2 seconds, continuing...");
        }
    }

    public void insertTimeMovement(String timeToSet, By locator) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String script =
                "var el = arguments[0]; " +
                        "var val = arguments[1]; " +
                        "var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set; " +
                        "setter.call(el, val); " +
                        "el.dispatchEvent(new Event('input', { bubbles: true })); " +
                        "el.dispatchEvent(new Event('change', { bubbles: true }));";

        highlight(locator, "type");
        js.executeScript(script, webElement(locator), timeToSet);
//        setStep();
    }

    /**
     * Alternative method: Wait for toaster with custom timeouts
     *
     * @param appearTimeout    - seconds to wait for toaster to appear
     * @param disappearTimeout - seconds to wait for toaster to disappear
     */
    protected void waitForToaster(int appearTimeout, int disappearTimeout) {
        try {
            // Wait for toaster to appear
            wait = new WebDriverWait(driver, Duration.ofSeconds(appearTimeout));
            wait.until(ExpectedConditions.visibilityOfElementLocated(toaster));
            System.out.println("✅ Toaster appeared");
            highlightToast();

            // Wait for toaster to disappear
            wait = new WebDriverWait(driver, Duration.ofSeconds(disappearTimeout));
            wait.until(ExpectedConditions.invisibilityOfElementLocated(toaster));
            System.out.println("✅ Toaster disappeared");

        } catch (Exception e) {
            System.out.println("ℹ️ Toaster handling completed");
        }
    }

    protected void waitAlert(int time) {
        wait = new WebDriverWait(driver, Duration.ofSeconds(time));
        wait.until(ExpectedConditions.alertIsPresent());
    }

    private static String getMethodName() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (stackTraceElements.length >= 2) {
            if (stackTraceElements.length >= 4)
                return stackTraceElements[4].getMethodName();
            return stackTraceElements[2].getMethodName();
        } else {
            return "Unknown";
        }
    }

    public void selectByVisibleText(By locator, String visibleText, int time) {
        explicitWait(locator, time);
        highlight(locator, "select");
        select = new Select(webElement(locator));
        select.selectByVisibleText(visibleText);
    }

    public void selectByValue(By locator, String value, int time) {
        explicitWait(locator, time);
        highlight(locator, "select");
        select = new Select(webElement(locator));
        select.selectByValue(value);
    }

    public void selectByIndex(By locator, int index, int time) {
        explicitWait(locator, time);
        highlight(locator, "select");
        select = new Select(webElement(locator));
        select.selectByIndex(index);
    }

    protected void setStep() {
        log(getMethodName());
    }

    /** Into the running test's report entry; nothing while none is open (between tests, class setup). */
    private static void log(Object line) {
        if (test != null) {
            test.info(line.toString());
        }
    }

    private static void logLabel(String text) {
        if (test != null) {
            test.info(MarkupHelper.createLabel(text, ExtentColor.BROWN));
        }
    }

    protected void acceptAlert(int time) {
        waitAlert(time);
        driver.switchTo().alert().accept();
    }

    protected void dismissAlert(int time) {
        waitAlert(time);
        driver.switchTo().alert().dismiss();
    }

    protected void sendKeysAlert(String text, int time) {
        waitAlert(time);
        driver.switchTo().alert().sendKeys(text);
    }

    protected String getTextAlert(int time) {
        waitAlert(time);
        return driver.switchTo().alert().getText();
    }


    protected void clickWithActions(By locator, int time) {
        actions = new Actions(driver);
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                actions.click(webElement(locator)).build().perform();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();

    }


    /**
     * Simplified version of robust click - good for most cases
     * Combines Actions and JavaScript click strategies
     */
    protected void smartClick(By locator, int time) {
        boolean clicked = false;

        try {
            // First try: Actions click with enhanced waiting
            wait = new WebDriverWait(driver, Duration.ofSeconds(time));
            WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));

            // Scroll and highlight
            scrollIntoElement(element);
            addBorderToElement(driver, element);

            // Actions click
            actions = new Actions(driver);
            actions.moveToElement(element).pause(Duration.ofMillis(200)).click().build().perform();

            clicked = true;
            System.out.println("✅ Smart click successful (Actions)");

        } catch (Exception e) {
            try {
                // Fallback: JavaScript click
                System.out.println("🔄 Smart click fallback to JavaScript: " + e.getClass().getSimpleName());
                WebElement element = driver.findElement(locator);
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                clicked = true;
                System.out.println("✅ Smart click successful (JavaScript)");

            } catch (Exception e2) {
                System.out.println("❌ Smart click failed: " + e2.getClass().getSimpleName());
                throw new RuntimeException("Smart click failed for locator: " + locator.toString(), e2);
            }
        }

        setStep();
    }

    protected void doubleClick(By locator, int time) {
        actions = new Actions(driver);
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                actions.doubleClick(webElement(locator)).build().perform();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();
    }

    protected void contextClick(By locator, int time) {
        actions = new Actions(driver);
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                actions.contextClick(webElement(locator)).build().perform();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();

    }

    protected void clickAndHold(By locator, int time) {
        actions = new Actions(driver);

        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                actions.clickAndHold(webElement(locator)).build().perform();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }

        setStep();

    }

    protected void escapeButton() {
        actions = new Actions(driver);
        actions.sendKeys(Keys.ESCAPE).build().perform();
    }

    protected void sendKeysWithEnter(By locator, String text) {
        highlight(locator, "type");
        actions = new Actions(driver);
        actions.sendKeys(webElement(locator), text)
                .perform();
        actions.sendKeys(webElement(locator),Keys.ENTER)
                .perform();
    }
    protected void sendKeysWithEnterAndEscape(By locator, String text) {
        highlight(locator, "type");
        actions = new Actions(driver);
        actions.sendKeys(webElement(locator), text)
                .perform();
        actions.sendKeys(webElement(locator),Keys.ENTER)
                .perform();
        actions.sendKeys(webElement(locator),Keys.ESCAPE)
                .perform();
    }

    protected void moveToElement(By locator, int time) {
        actions = new Actions(driver);
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                actions.moveToElement(webElement(locator)).build().perform();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();
    }

    protected void dragAndDrop(By src, By target, int time) {
        actions = new Actions(driver);
        explicitWait(src, time);
        explicitWait(target, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(src));
                addBorderToElement(driver, webElement(src));
                scrollIntoElement(webElement(target));
                addBorderToElement(driver, webElement(target));
                actions.dragAndDrop(webElement(src), webElement(target)).build().perform();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();

    }

    protected void release(By locator, int time) {
        actions = new Actions(driver);
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();

    }

    protected void release(int time) {
        actions = new Actions(driver);
        actions.release().build().perform();
        setStep();

    }
    protected void scrollToElementInModal(By locator) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Executes JavaScript to scroll the inner container instead of the main window
        js.executeScript(
                "arguments[0].scrollIntoView({" +
                        "  behavior: 'smooth'," +
                        "  block: 'center'," +
                        "  inline: 'center'" +
                        "});",
                webElement(locator)
        );
    }

    protected void click(By locator, int time) {
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                webElement(locator).click();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }

        setStep();

    }

    protected void submit(By locator, int time) {
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                webElement(locator).submit();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();
    }

    protected String getText(By locator, int time) {
        String text = null;
        explicitWait(locator, time);

        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                text = webElement(locator).getText();
                break;
            } catch (StaleElementReferenceException e) {
                // Retry loop
            }
        }
        setStep();
        return text;
    }

    protected String getTextUsingHorizontalScroll(By locator) {
        String text = null;

        for (int i = 0; i < 5; i++) {
            try {
                // 1. Perform RTL-aware horizontal scroll
                scrollIntoView(locator);

                WebElement targetElement = driver.findElement(locator);
                addBorderToElement(driver, targetElement);

                // 2. Extract text (falls back to textContent if hidden by off-screen rendering)
                text = targetElement.getText();
                if (text == null || text.trim().isEmpty()) {
                    text = (String) ((JavascriptExecutor) driver).executeScript(
                            "return (arguments[0].innerText || arguments[0].textContent || '').replace(/\\s+/g, ' ').trim();",
                            targetElement
                    );
                }

                if (text != null && !text.trim().isEmpty()) {
                    break;
                }
            } catch (StaleElementReferenceException e) {
                // Retry on Angular DOM refresh
            }
        }

        setStep();
        return text;
    }

    protected String getTextFromDOM(By locator, int time) {
        String text = null;
//        explicitWait(locator, time);

        for (int i = 0; i < 5; i++) {
            try {
                // Find the element directly in the DOM tree
                WebElement targetElement = driver.findElement(locator);

                // Execute JS to grab the raw textContent directly from DOM memory
                text = (String) ((JavascriptExecutor) driver).executeScript(
                        "var elem = arguments[0];" +
                                "if (!elem) return '';" +
                                "return elem.textContent || elem.innerText || '';",
                        targetElement
                );

                if (text != null && !text.trim().isEmpty()) {
                    // Clean up extra spaces, non-breaking spaces (\u00a0), and line breaks
                    text = text.replaceAll("[\\s\\u00a0]+", " ").trim();

                    // the same numbered step box as every other read (it was a plain red border)
                    addBorderToElement(driver, targetElement, "check");
                    break;
                }
            } catch (StaleElementReferenceException e) {
                // Retry if Angular re-renders the DOM row
            } catch (Exception e) {
                // Catch potential JS exceptions during DOM evaluation
            }
        }

        setStep();
        return text;
    }

    protected String getTextViaJS(By locator, int time) {
        explicitWait(locator, time);

        WebElement element = driver.findElement(locator);

        // JS reads clean inner text directly from Angular's DOM tree
        String script =
                "var elem = arguments[0];" +
                        "elem.scrollIntoView({block: 'nearest', inline: 'center'});" +
                        "return elem.innerText.replace(/\\s+/g, ' ').trim();";

        String text = (String) ((JavascriptExecutor) driver).executeScript(script, element);
        highlight(element, "check");
        return text;
    }

    protected boolean isSelected(By locator, int time) {
        boolean flag = false;
        explicitWait(locator, time);

        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                flag = webElement(locator).isSelected();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();
        return flag;
    }

    protected boolean isDisplayed(By locator, int time) {
        boolean flag;
        explicitWait(locator, time);
        scrollIntoElement(webElement(locator));
        flag = webElement(locator).isDisplayed();

        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                flag = webElement(locator).isDisplayed();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();
        return flag;
    }

    protected boolean isEnabled(By locator, int time) {
        boolean flag;
        explicitWait(locator, time);
        scrollIntoElement(webElement(locator));
        flag = webElement(locator).isDisplayed();

        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                flag = webElement(locator).isEnabled();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();
        return flag;

    }

    protected void clear(By locator, int time) {
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                webElement(locator).clear();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }

        setStep();

    }

    protected void sendKeys(By locator, int time, String text) {
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
                scrollIntoElement(webElement(locator));
                addBorderToElement(driver, webElement(locator));
                WebElement element = webElement(locator);
                element.sendKeys(text);

                // Verify if keys were sent correctly
                String currentValue = element.getAttribute("value");
                if (currentValue == null || currentValue.trim().isEmpty() || !currentValue.contains(text)) {
                    // If field is empty or doesn't contain expected text, clear and retry
                    element.clear();
                    element.sendKeys(text);
                    System.out.println("⚠️ SendKeys verification failed, cleared and re-inserted text");
                }
                break;
            } catch (StaleElementReferenceException e) {

            }
        }

        setStep();
    }

    @SneakyThrows
    public void sendKeysWithDelay(By locator, String textToType) {
        Thread.sleep(500);

        // 1. Find the element (assumes 'driver' is accessible globally)
        WebElement element = driver.findElement(locator);
        highlight(element, "type");

        // 2. Clear existing text
        element.clear();

        // 3. Build the action sequence
        Actions actions = new Actions(driver);
        actions.moveToElement(element).click(); // Focus on the field

        for (int i = 0; i < textToType.length(); i++) {
            CharSequence character = String.valueOf(textToType.charAt(i));

            // Pausing BEFORE sendKeys ensures the 10ms delay applies to the 1st char too
            actions.pause(Duration.ofMillis(100))
                    .sendKeys(character);
        }

        // 4. Perform the sequence
        actions.perform();
    }


    /** Numbers the highlighted steps of the running test; reset when a recording starts. */
    private static volatile int stepNumber;
    /** Runs right after a step is highlighted - the recorder hooks in here to take that frame. */
    private static Runnable onStep;
    /** Runs before the previous step's box is removed - the recorder makes sure that box was recorded. */
    private static Runnable beforeStep;
    /** Gets every step and assertion with its locator - the report's Timeline and the live view hook in here. */
    private static volatile Consumer<Step> stepListener;

    /**
     * One step of the running test, as the Timeline shows it: "5 click xpath: //button[...] button "Save"
     * LoginPage.clickSave". An assertion is a step too (action "assert", {@code passed} set, number = the
     * step it came after). {@code at} is the wall clock, so network calls can be put under their step.
     */
    public record Step(int number, String action, String locator, String element, String caller, long at,
                       Boolean passed, String detail) {
    }

    public static void resetStepNumber() {
        stepNumber = 0;
    }

    /** The number of the step boxed last - the recorder notes it on every frame. */
    public static int currentStepNumber() {
        return stepNumber;
    }

    public static void setOnStep(Runnable callback) {
        onStep = callback;
    }

    public static void setBeforeStep(Runnable callback) {
        beforeStep = callback;
    }

    public static void setStepListener(Consumer<Step> listener) {
        stepListener = listener;
    }

    private static void tellListener(Step step) {
        Consumer<Step> listener = stepListener;
        if (listener != null) {
            try {
                listener.accept(step);
            } catch (Exception ignored) {
                // the timeline is a report aid - it must never fail a test
            }
        }
    }

    /**
     * The locator an element was found with, from Selenium's own description of it
     * ({@code [[ChromeDriver: chrome on windows (id)] -> xpath: //button[.='Save']]} gives
     * {@code xpath: //button[.='Save']}); for an element found inside another, the inner locator.
     * Empty for an element that came back from a script.
     */
    static String locatorOf(WebElement element) {
        String text = String.valueOf(element);
        int arrow = text.lastIndexOf("] -> ");
        if (arrow < 0) {
            return "";
        }
        String locator = text.substring(arrow + 5);
        return locator.endsWith("]") ? locator.substring(0, locator.length() - 1) : locator;
    }

    /** The page object method (or test) that asked for the step: {@code LoginPage.clickSave}. */
    private static String caller() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(f -> !f.getClassName().equals(MethodHandlesWeb.class.getName())
                        && !f.getClassName().startsWith("java.") && !f.getClassName().startsWith("jdk."))
                .findFirst()
                .map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "." + f.getMethodName())
                .orElse(""));
    }

    private static void assertion(boolean passed, String detail) {
        tellListener(new Step(stepNumber, "assert", "", "", caller(), System.currentTimeMillis(), passed, detail));
    }

    /**
     * Highlights the element the next step acts on: a box around it with the step number and the
     * action ("3 click", "4 type"). It is an overlay - the element's own style and the layout are
     * untouched, clicks pass through it (pointer-events: none), and the number and label are CSS
     * generated content, so no XPath text() ever matches them.
     *
     * <p>Only the current step is boxed: the next step removes the previous box (once the recorder has
     * it on a frame), and a box left alone removes itself after 1.5 s. While it is up it follows its
     * element when the page or a panel scrolls, so it never floats over the wrong thing.
     */
    private static void addBorderToElement(WebDriver driver, WebElement element) {
        addBorderToElement(driver, element, actionLabel(Thread.currentThread().getStackTrace()[2].getMethodName()));
    }

    /**
     * The same numbered box, for a step a page object does without the wrappers above (a JS click, an
     * option picked inside a retry, a toast read with its own wait). Never throws: if the element is gone,
     * there is simply no box.
     */
    protected void highlight(WebElement element, String action) {
        addBorderToElement(driver, element, action);
    }

    protected void highlight(By locator, String action) {
        try {
            addBorderToElement(driver, webElement(locator), action);
        } catch (Exception ignored) {
            // cosmetic only
        }
    }

    /** The toast that just showed - boxed so the recording shows which message a step waited for or read. */
    private void highlightToast() {
        highlight(toasterContent, "toast");
    }

    private static void addBorderToElement(WebDriver driver, WebElement element, String action) {
        if (beforeStep != null) {
            beforeStep.run();
        }
        Object label;
        try {
            label = ((JavascriptExecutor) driver).executeScript(
                    "var el = arguments[0], d = document;" +
                            // what the Timeline calls the element: tag + its text, or an input's placeholder /
                            // label - never an input's value, which can be a password
                            "var field = /^(INPUT|TEXTAREA)$/.test(el.tagName);" +
                            "var t = (field ? (el.getAttribute('placeholder') || el.getAttribute('aria-label') ||" +
                            "  el.getAttribute('formcontrolname') || el.name || '') :" +
                            "  (el.textContent || el.getAttribute('aria-label') || '')).replace(/\\s+/g, ' ').trim();" +
                            "var label = el.tagName.toLowerCase() + (t ? ' \"' + (t.length > 40 ? t.slice(0, 40) + '...' : t) + '\"' : '');" +
                            "var r = el.getBoundingClientRect();" +
                            "if (!r.width && !r.height) return label;" +
                            "if (!d.getElementById('__aq_style')) {" +
                            "  var s = d.createElement('style'); s.id = '__aq_style';" +
                            "  s.textContent = '.__aq_box{position:fixed;z-index:2147483647;pointer-events:none;box-sizing:border-box;" +
                            "border:3px solid #ff2d55;border-radius:6px;background:rgba(255,45,85,.12);box-shadow:0 0 0 4px rgba(255,45,85,.25)}" +
                            ".__aq_box::before{content:attr(data-n);position:absolute;top:-18px;left:-18px;min-width:32px;height:32px;padding:0 8px;" +
                            "box-sizing:border-box;border-radius:16px;background:#ff2d55;color:#fff;font:bold 18px/32px Arial,sans-serif;text-align:center}" +
                            ".__aq_box::after{content:attr(data-l);position:absolute;top:-15px;left:20px;padding:2px 8px;border-radius:4px;" +
                            "background:#1f2937;color:#fff;font:bold 15px/20px Arial,sans-serif;white-space:nowrap}';" +
                            "  d.head.appendChild(s);" +
                            // boxes follow their element on any scroll (capture = inner panels too) or resize
                            "  var follow = function () { d.querySelectorAll('.__aq_box').forEach(function (x) {" +
                            "    if (!x.__el) return; var q = x.__el.getBoundingClientRect();" +
                            "    x.style.left = (q.left - 5) + 'px'; x.style.top = (q.top - 5) + 'px';" +
                            "    x.style.width = (q.width + 10) + 'px'; x.style.height = (q.height + 10) + 'px'; }); };" +
                            "  d.addEventListener('scroll', follow, true); window.addEventListener('resize', follow);" +
                            "}" +
                            "d.querySelectorAll('.__aq_box').forEach(function (x) { x.remove(); });" +
                            "var b = d.createElement('div'); b.className = '__aq_box'; b.__el = el;" +
                            "b.setAttribute('data-n', arguments[1]); b.setAttribute('data-l', arguments[2]);" +
                            "b.style.left = (r.left - 5) + 'px'; b.style.top = (r.top - 5) + 'px';" +
                            "b.style.width = (r.width + 10) + 'px'; b.style.height = (r.height + 10) + 'px';" +
                            "d.body.appendChild(b);" +
                            "setTimeout(function () { b.remove(); }, 1500);" +
                            "return label;",
                    element, String.valueOf(stepNumber + 1), action);
        } catch (Exception ignored) {
            return; // highlighting is cosmetic - it must never fail a test
        }
        // counted once the box is up, so a frame that notes this number really shows this step's box
        stepNumber++;
        tellListener(new Step(stepNumber, action, locatorOf(element), label == null ? "" : label.toString(),
                caller(), System.currentTimeMillis(), null, null));
        if (onStep != null) {
            onStep.run();
        }
    }

    private static String actionLabel(String helper) {
        String h = helper.toLowerCase();
        if (h.startsWith("sendkeys")) return "type";
        if (h.startsWith("clear")) return "clear";
        if (h.startsWith("submit")) return "submit";
        if (h.startsWith("gettext") || h.startsWith("is")) return "check";
        if (h.startsWith("movetoelement")) return "hover";
        if (h.startsWith("draganddrop")) return "drag";
        if (h.startsWith("release")) return "release";
        if (h.startsWith("doubleclick")) return "double click";
        if (h.startsWith("contextclick")) return "right click";
        if (h.startsWith("clickandhold")) return "hold";
        return "click";
    }

    public void scrollIntoView(By locator) {
        WebElement targetCell = driver.findElement(locator);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        js.executeScript(
                "var elem = arguments[0];" +
                        "if (!elem) return;" +

                        "// 1. Check if Angular NgZone / Component APIs are accessible on the element" +
                        "var ngContainer = elem.closest('.table-responsive') || elem.parentElement;" +

                        "// 2. Use modern Angular element scrollIntoView with inline center options" +
                        "elem.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });" +

                        "// 3. Dispatch Angular CDK scroll event so ChangeDetectorRef picks up the move" +
                        "var event = new Event('scroll', { bubbles: true });" +
                        "elem.dispatchEvent(event);" +
                        "if (ngContainer) ngContainer.dispatchEvent(event);",
                targetCell
        );
    }

    protected void scrollIntoElement(WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript(
                "var elem = arguments[0];" +
                        "var rect = elem.getBoundingClientRect();" +
                        // Calculate absolute position on the page relative to window scroll
                        "var elementAbsoluteTop = rect.top + window.pageYOffset;" +
                        "var elementAbsoluteLeft = rect.left + window.pageXOffset;" +
                        // Calculate center offsets subtracting half of viewport size and adding half of element size
                        "var targetY = elementAbsoluteTop - (window.innerHeight / 2) + (rect.height / 2);" +
                        "var targetX = elementAbsoluteLeft - (window.innerWidth / 2) + (rect.width / 2);" +
                        // Scroll the window directly to the centered coordinates
                        "window.scrollTo({ top: targetY, left: targetX, behavior: 'instant' });",
                element
        );
    }


    public static void myAssertEquals(Object actualResult, Object expectedResult) {
        logLabel("------------------- Actual Result -------------------");
        log(actualResult);

        logLabel("------------------- Expected Result -------------------");
        log(expectedResult);

        try {
            assertEquals(actualResult, expectedResult);
        } catch (AssertionError e) {
            assertion(false, "expected \"" + expectedResult + "\" but got \"" + actualResult + "\"");
            throw e;
        }
        assertion(true, "\"" + actualResult + "\"");
    }

    public static void myAssertTrue(boolean condition) {
        logLabel("------------------- Condition -------------------");
        log(condition);

        assertion(condition, "expected true, got " + condition);
        assertTrue(condition);
    }

    public static void myAssertFalse(boolean condition) {
        logLabel("------------------- Condition -------------------");
        log(condition);

        assertion(!condition, "expected false, got " + condition);
        assertFalse(condition);
    }
    ////////////////////////////////////////////////////////////////////////////////////////////

    /// ///////////////////////////////// confirmation dialog ///////////////////////////////////

    private final By confirmationDialogTitle = By.className("swal2-title");
    private final By acceptButton = By.xpath("//button[@class='swal2-confirm swal2-styled']");

    private final By rejectButton = By.xpath("//button[@class='swal2-cancel swal2-styled']");

    private void waitForConfirmationDialog() {
        isDisplayed(confirmationDialogTitle, 10);
    }

    public void clickAccept() {
        waitForConfirmationDialog();
        click(acceptButton, 10);
    }

    public void clickReject() {
        waitForConfirmationDialog();
        click(rejectButton, 10);
    }

    /// /////////////////////////////////////////////////////////////////////////////////////////

    public void hideElement(By locator) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].style.display = 'none';", webElement(locator));
        } catch (Exception e) {
            System.err.println("Failed to hide element: " + e.getMessage());
        }
    }
}
