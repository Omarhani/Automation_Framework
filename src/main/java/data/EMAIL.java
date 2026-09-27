package data;

/**
 * testData.json EMAIL: the mailbox {@code utils.EmailReader} polls. The mailbox password never goes here:
 * it comes from the MAIL_APP_PASSWORD env var (Jenkins credential) or -DMAIL_APP_PASSWORD.
 */
public class EMAIL {

    /** imap.gmail.com for Gmail / Google Workspace (with an app password), outlook.office365.com for Microsoft 365. */
    public String IMAP_HOST = "imap.gmail.com";

    /** The mailbox read when a test does not name one (MAIL_USER env var / -DMAIL_USER win over it). */
    public String MAILBOX = "";

    /** Only emails from this address are read, e.g. no-reply@your-app.com. Empty = any sender. */
    public String SENDER = "";
}
