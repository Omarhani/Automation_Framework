package ui.tools;

import base.BaseTests;
import com.google.gson.GsonBuilder;
import data.Login;
import data.User;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static reader.ReadDataFromJson.dataModel;
import static utils.MethodHandlesWeb.myAssertTrue;

/**
 * web/webOpenLoggedIn: opens the web app on a DevTools port, logs in through the login page as a UI.USERS user
 * (the locators come from testData.json UI.LOGIN, the password from the user's row - the code types it, never a
 * person or Claude), optionally opens a page, and leaves the browser open. {@code target/open-browser.json} names the
 * port, env and page only, so {@code tools/browser.js} can drive that same logged-in session afterwards.
 */
public class OpenLoggedInTests extends BaseTests {

    @Parameters({"user", "page"})
    @Test(groups = "Web")
    public void openTheAppLoggedIn(@Optional("") String user, @Optional("") String page) throws IOException {
        String userKey = given(System.getProperty("user", user));
        if (userKey == null) {
            userKey = "MAIN_USER";
        }
        Login login = dataModel().UI.LOGIN;
        login.requireFilled();
        User account = dataModel().UI.user(userKey);
        if (account.PASSWORD == null || account.PASSWORD.isBlank()) {
            throw new IllegalStateException("UI.USERS." + userKey + ".PASSWORD is empty in " + reader.ReadDataFromJson.dataFile()
                    + " - the tester fills it in the file (never in chat)");
        }

        String base = getBaseURL().replaceAll("/+$", "");
        if (given(login.PATH) != null) {
            driver.get(base + (login.PATH.startsWith("/") ? "" : "/") + login.PATH);
        }
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        type(wait, login.USERNAME_FIELD, account.login());
        type(wait, login.PASSWORD_FIELD, account.PASSWORD);
        wait.until(ExpectedConditions.elementToBeClickable(Login.by(login.SUBMIT))).click();
        boolean loggedIn;
        try {
            loggedIn = wait.until(ExpectedConditions.visibilityOfElementLocated(Login.by(login.LOGGED_IN_CHECK))) != null;
        } catch (org.openqa.selenium.TimeoutException notShown) {
            loggedIn = false;
        }
        myAssertTrue("Logged in as " + userKey + " (UI.LOGIN.LOGGED_IN_CHECK shown)", loggedIn);

        if (given(page) != null) {
            String path = given(page);
            driver.get(path.startsWith("http") ? path : base + (path.startsWith("/") ? "" : "/") + path);
        }

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("port", debugPort);
        session.put("debuggerAddress", "127.0.0.1:" + debugPort);
        session.put("env", getServerType());
        session.put("baseUrl", base);
        session.put("user", userKey);
        session.put("url", driver.getCurrentUrl());
        session.put("keptOpen", keepBrowserOpen);
        session.put("openedAt", LocalDateTime.now().withNano(0).toString());
        Path file = Path.of("target", "open-browser.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(session), StandardCharsets.UTF_8);
        System.out.println("Logged in as " + userKey + " (" + getServerType() + ") - " + file + ": 127.0.0.1:" + debugPort);
    }

    /** Plain WebDriver typing: the value never goes into the report's step list. */
    private void type(WebDriverWait wait, String locator, String text) {
        WebElement field = wait.until(ExpectedConditions.visibilityOfElementLocated(Login.by(locator)));
        field.clear();
        field.sendKeys(text);
    }

    /** null for an empty or unresolved ({@code ${...}}) value. */
    private static String given(String value) {
        return value == null || value.isBlank() || value.trim().startsWith("${") ? null : value.trim();
    }
}
