package data;

import org.openqa.selenium.By;

/**
 * testData.json UI.LOGIN: how the web app's login page is filled, so a tool (ui.tools.OpenLoggedInTests) can log in
 * as any UI.USERS user without a page object written for it. Locators are strings: XPath when they start with
 * {@code /} or {@code (}, CSS otherwise. Fill it once per project:
 *
 * <pre>
 * "LOGIN": {
 *   "PATH": "/login",                                   added to the env's WEB_URL
 *   "USERNAME_FIELD": "input[name='email']",
 *   "PASSWORD_FIELD": "input[type='password']",
 *   "SUBMIT": "//button[@type='submit']",
 *   "LOGGED_IN_CHECK": "//nav"                          an element shown only after a good login
 * }
 * </pre>
 */
public class Login {

    public String PATH = "";
    public String USERNAME_FIELD = "";
    public String PASSWORD_FIELD = "";
    public String SUBMIT = "";
    public String LOGGED_IN_CHECK = "";

    /** XPath when it starts with / or (, else CSS. */
    public static By by(String locator) {
        String l = locator.trim();
        return l.startsWith("/") || l.startsWith("(") ? By.xpath(l) : By.cssSelector(l);
    }

    /** Throws naming every empty field, so the run says exactly what to fill. */
    public void requireFilled() {
        StringBuilder missing = new StringBuilder();
        if (blank(USERNAME_FIELD)) missing.append(" USERNAME_FIELD");
        if (blank(PASSWORD_FIELD)) missing.append(" PASSWORD_FIELD");
        if (blank(SUBMIT)) missing.append(" SUBMIT");
        if (blank(LOGGED_IN_CHECK)) missing.append(" LOGGED_IN_CHECK");
        if (missing.length() > 0) {
            throw new IllegalStateException("Fill UI.LOGIN" + missing + " in " + reader.ReadDataFromJson.dataFile()
                    + " (locators of the login page: XPath or CSS)");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
