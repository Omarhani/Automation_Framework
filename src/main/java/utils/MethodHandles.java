package utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class MethodHandles {


    WebDriver driver;
    WebDriverWait wait;
    static ExtentReports extent;
    static ExtentTest test;
    Select select;
    Actions actions;

    public MethodHandles(WebDriver driver) {
        this.driver = driver;
    }

    private WebElement webElement(By locator) {
        return driver.findElement(locator);
    }

    protected List<WebElement> webElements(By locator) {
        return driver.findElements(locator);
    }

    private void explicitWait(By locator, int time) {
        wait = new WebDriverWait(driver, Duration.ofSeconds(time));
        wait.until(ExpectedConditions.and(
                ExpectedConditions.visibilityOfElementLocated(locator),
                ExpectedConditions.elementToBeClickable(locator)));
    }

    protected void invisibilityOf(By locator, int time) {
        wait = new WebDriverWait(driver, Duration.ofSeconds(time));
        wait.until(ExpectedConditions.and(
                ExpectedConditions.invisibilityOfElementLocated(locator),
                ExpectedConditions.invisibilityOf(webElement(locator))));
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
        select = new Select(webElement(locator));
        select.selectByVisibleText(visibleText);
    }

    public void selectByValue(By locator, String value, int time) {
        explicitWait(locator, time);
        select = new Select(webElement(locator));
        select.selectByValue(value);
    }

    public void selectByIndex(By locator, int index, int time) {
        explicitWait(locator, time);
        select = new Select(webElement(locator));
        select.selectByIndex(index);
    }

    private void setStep() {
        test.info(getMethodName());
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

                addBorderToElement(driver, webElement(locator));
                actions.click(webElement(locator)).build().perform();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();

    }

    protected void doubleClick(By locator, int time) {
        actions = new Actions(driver);
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {

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
                addBorderToElement(driver, webElement(locator));
                actions.clickAndHold(webElement(locator)).build().perform();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }

        setStep();

    }

    protected void moveToElement(By locator, int time) {
        actions = new Actions(driver);
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {
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
                addBorderToElement(driver, webElement(src));
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

    protected void click(By locator, int time) {
        explicitWait(locator, time);
        for (int i = 0; i < 5; i++) {
            try {

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

                addBorderToElement(driver, webElement(locator));
                text = webElement(locator).getText();
                break;
            } catch (StaleElementReferenceException e) {

            }
        }
        setStep();
        return text;
    }

    protected boolean isSelected(By locator, int time) {
        boolean flag = false;
        explicitWait(locator, time);

        for (int i = 0; i < 5; i++) {
            try {

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
        flag = webElement(locator).isDisplayed();

        for (int i = 0; i < 5; i++) {
            try {

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
        flag = webElement(locator).isDisplayed();

        for (int i = 0; i < 5; i++) {
            try {

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

                addBorderToElement(driver, webElement(locator));
                webElement(locator).sendKeys(text);
                break;
            } catch (StaleElementReferenceException e) {

            }
        }

        setStep();
    }


    private static void addBorderToElement(WebDriver driver, WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].style.border = '3px solid red'", element);
    }


    public static void myAssertEquals(Object actualResult, Object expectedResult) {
        test.info(MarkupHelper
                .createLabel("------------------- Actual Result -------------------", ExtentColor.BROWN));
        test.info(actualResult.toString());

        test.info(MarkupHelper
                .createLabel("------------------- Expected Result -------------------", ExtentColor.BROWN));
        test.info(expectedResult.toString());

        assertEquals(actualResult, expectedResult);
    }

}
