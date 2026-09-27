package data;

/**
 * A named user of testData.json (UI.USERS, API.USERS, MOBILE.USERS). Fill only the fields a test needs;
 * the rest stay empty.
 */
public class User {

    public String EMAIL = "";
    public String USERNAME = "";
    public String PASSWORD = "";
    public String NEW_PASSWORD = "";
    public String WRONG_PASSWORD = "";
    public String FIRST_NAME = "";
    public String LAST_NAME = "";

    /** The login name: USERNAME, or EMAIL when the app logs in by email. */
    public String login() {
        return USERNAME == null || USERNAME.isBlank() ? EMAIL : USERNAME;
    }
}
