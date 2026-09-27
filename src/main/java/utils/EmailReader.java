package utils;

import jakarta.mail.*;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;

import java.util.Date;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the emails an app sends (sign-up, password reset, OTP ...) from a mailbox over IMAP and pulls a value
 * out of them - the password, the code, the link.
 * <p>
 * The IMAP host, default mailbox and sender come from testData.json EMAIL. The mailbox's password never comes
 * from the repo: MAIL_APP_PASSWORD env var (Jenkins credential) or -DMAIL_APP_PASSWORD; MAIL_USER overrides the
 * mailbox. Gmail / Google Workspace needs an app password (2-step verification on).
 *
 * <pre>
 * long sentAfter = System.currentTimeMillis();
 * ... the app sends the email ...
 * EmailReader.Email email = EmailReader.waitFor(null, body -> body.contains(userName), sentAfter, 120);
 * String password = email.value(EmailReader.PASSWORD);
 * </pre>
 */
public class EmailReader {

    /** "Username: x" / "User name: x" / "اسم المستخدم: x" - the value is group 1. */
    public static final Pattern USERNAME = Pattern.compile("(?:Username|User name|اسم المستخدم)\\s*:\\s*(\\S+)");
    /** "Password: x" / "كلمة المرور: x". */
    public static final Pattern PASSWORD = Pattern.compile("(?:Password|كلمة المرور)\\s*:\\s*(\\S+)");
    /** The first 4-8 digit number, e.g. an OTP / verification code. */
    public static final Pattern CODE = Pattern.compile("\\b(\\d{4,8})\\b");
    /** The first http(s) link. */
    public static final Pattern LINK = Pattern.compile("(https?://[^\\s\"'<>]+)");

    // Clock gap between this machine and the mail server's received date (measured ~1 s). Keep it small: a wider
    // window lets a run pick the previous run's email for the same user (seen with 2 minutes)
    private static final long CLOCK_SKEW_MS = 15 * 1000;

    /** The email the last successful {@link #waitFor} call found - {@link EmailReport#logLastEmail()} shows it. */
    private static volatile Email lastEmail;

    private EmailReader() {
    }

    /**
     * An email as it was read: headers, its text and how it looked. Read while the mailbox is open, so it
     * stays usable after the connection is closed (e.g. for the test report).
     */
    public static final class Email {
        public final String from, to, subject;
        public final Date sent, received;
        /** The HTML part with its inline (cid:) images turned into data: URIs, or the text part in a <pre>. */
        public final String html;
        /** The body as plain text (HTML tags removed). */
        public final String text;

        Email(String from, String to, String subject, Date sent, Date received, String html, String text) {
            this.from = from;
            this.to = to;
            this.subject = subject;
            this.sent = sent;
            this.received = received;
            this.html = html;
            this.text = text;
        }

        /** Group 1 of the first match of {@code pattern} in the body (or the whole match without a group), or null. */
        public String value(Pattern pattern) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) {
                return null;
            }
            return m.groupCount() >= 1 ? m.group(1) : m.group();
        }
    }

    public static Email lastEmail() {
        return lastEmail;
    }

    /**
     * Polls {@code mailbox} (null = testData.json EMAIL.MAILBOX / MAIL_USER) until an email from EMAIL.SENDER,
     * received after {@code sentAfterMillis}, whose text passes {@code bodyMatches} shows up; newest first.
     * The time filter is what keeps an older email for the same user from being picked.
     */
    public static Email waitFor(String mailbox, Predicate<String> bodyMatches, long sentAfterMillis, int timeoutSeconds) {
        data.EMAIL settings = reader.ReadDataFromJson.dataModel().EMAIL;
        String user = setting("MAIL_USER", mailbox != null && !mailbox.isBlank() ? mailbox : settings.MAILBOX);
        String appPassword = setting("MAIL_APP_PASSWORD", null);
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("No mailbox: give one, or set EMAIL.MAILBOX in the data file / MAIL_USER");
        }
        if (appPassword == null || appPassword.isBlank()) {
            throw new IllegalStateException("MAIL_APP_PASSWORD is not set (env var or -DMAIL_APP_PASSWORD)");
        }

        lastEmail = null;
        Date sentAfter = new Date(sentAfterMillis - CLOCK_SKEW_MS);
        long end = System.currentTimeMillis() + timeoutSeconds * 1000L;

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        Store store = null;
        try {
            store = Session.getInstance(props).getStore("imaps");
            store.connect(settings.IMAP_HOST, user, appPassword);

            while (true) {
                Email email = find(store, settings.SENDER, bodyMatches, sentAfter);
                if (email != null) {
                    lastEmail = email;
                    return email;
                }
                if (System.currentTimeMillis() > end) break;
                Thread.sleep(5000);
            }
        } catch (MessagingException e) {
            throw new RuntimeException("Could not read mailbox " + user + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (store != null) {
                try { store.close(); } catch (MessagingException ignored) { }
            }
        }
        throw new AssertionError("No matching email in " + user + " after " + timeoutSeconds + "s");
    }

    /**
     * The password a sign-up email carries for {@code userName} ("Username: x ... Password: y") - the email whose
     * username line is that user, received after {@code sentAfterMillis}.
     */
    public static String waitForPassword(String mailbox, String userName, long sentAfterMillis, int timeoutSeconds) {
        Email email = waitFor(mailbox, body -> {
            Matcher u = USERNAME.matcher(body);
            return u.find() && u.group(1).equalsIgnoreCase(userName) && PASSWORD.matcher(body).find();
        }, sentAfterMillis, timeoutSeconds);
        return email.value(PASSWORD);
    }

    private static Email find(Store store, String sender, Predicate<String> bodyMatches, Date sentAfter) throws MessagingException {
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);   // re-opened each poll so new mail shows up
        try {
            // IMAP date search is day-precise; the exact time is checked below
            SearchTerm received = new ReceivedDateTerm(ComparisonTerm.GE, sentAfter);
            Message[] messages = inbox.search(sender == null || sender.isBlank()
                    ? received : new AndTerm(new FromStringTerm(sender), received));

            for (int i = messages.length - 1; i >= 0; i--) {   // newest first
                Message message = messages[i];
                if (message.getReceivedDate() == null || message.getReceivedDate().before(sentAfter)) continue;

                String body = textOf(message);
                if (bodyMatches == null || bodyMatches.test(body)) {
                    return capture(message, body);
                }
            }
            return null;
        } finally {
            inbox.close(false);
        }
    }

    private static String textOf(Part part) throws MessagingException {
        try {
            if (part.isMimeType("text/plain")) {
                return part.getContent().toString();
            }
            if (part.isMimeType("text/html")) {
                return part.getContent().toString()
                        .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                        .replaceAll("<[^>]+>", " ")
                        .replace("&nbsp;", " ").replace("&#43;", "+").replace("&#64;", "@").replace("&amp;", "&")
                        .replace(' ', ' ');
            }
            if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    text.append(textOf(multipart.getBodyPart(i))).append('\n');
                }
                return text.toString();
            }
            return "";
        } catch (java.io.IOException e) {
            throw new MessagingException("Could not read email body", e);
        }
    }

    private static Email capture(Message message, String text) throws MessagingException {
        java.util.Map<String, String> images = new java.util.HashMap<>();
        String html = htmlOf(message, images);
        if (html == null) {
            html = "<pre style=\"white-space:pre-wrap;font:14px sans-serif\">" + escape(text) + "</pre>";
        }
        for (java.util.Map.Entry<String, String> image : images.entrySet()) {
            html = html.replace("cid:" + image.getKey(), image.getValue());
        }
        return new Email(join(message.getFrom()), join(message.getRecipients(Message.RecipientType.TO)),
                message.getSubject(), message.getSentDate(), message.getReceivedDate(), html, text);
    }

    /** The first text/html part; inline images (Content-ID) are collected as data: URIs on the way. */
    private static String htmlOf(Part part, java.util.Map<String, String> images) throws MessagingException {
        try {
            if (part.isMimeType("text/html")) {
                return part.getContent().toString();
            }
            if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                String html = null;
                for (int i = 0; i < multipart.getCount(); i++) {
                    String found = htmlOf(multipart.getBodyPart(i), images);
                    if (html == null) html = found;
                }
                return html;
            }
            if (part.isMimeType("image/*") && part instanceof jakarta.mail.internet.MimeBodyPart) {
                String id = ((jakarta.mail.internet.MimeBodyPart) part).getContentID();
                if (id != null) {
                    byte[] bytes = part.getInputStream().readAllBytes();
                    images.put(id.replaceAll("^<|>$", ""), "data:" + part.getContentType().split(";")[0].trim()
                            + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes));
                }
            }
            return null;
        } catch (java.io.IOException e) {
            throw new MessagingException("Could not read email body", e);
        }
    }

    private static String join(Address[] addresses) {
        if (addresses == null) return "";
        StringBuilder all = new StringBuilder();
        for (Address address : addresses) {
            if (all.length() > 0) all.append(", ");
            all.append(address instanceof jakarta.mail.internet.InternetAddress
                    ? ((jakarta.mail.internet.InternetAddress) address).toUnicodeString() : address.toString());
        }
        return all.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
