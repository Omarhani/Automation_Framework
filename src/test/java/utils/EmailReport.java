package utils;

import com.aventstack.extentreports.Status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import static utils.MethodHandlesWeb.test;

/**
 * Shows the email a test waited for ({@link EmailReader}) in the running test's report entry, the way the user
 * sees it: a details table (from, to, subject, sent / received, and the values the test took out of it), the
 * email itself rendered in a frame (its own styles can't leak into the report; inline logos come along as
 * data: URIs), and a link to the same email saved beside the report as {@code <n>_<method>_email.html}.
 * Values are shown in full - they belong to the throwaway user the run just made; don't log real secrets.
 */
public final class EmailReport {

    private EmailReport() {
    }

    /**
     * Logs the email {@link EmailReader} found last, with the user name / password / code it carries when it has
     * them; nothing when it found none or no entry is open.
     */
    public static void logLastEmail() {
        EmailReader.Email email = EmailReader.lastEmail();
        if (email == null) {
            return;
        }
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("User name", email.value(EmailReader.USERNAME));
        values.put("Password", email.value(EmailReader.PASSWORD));
        if (values.get("Password") == null) {
            values.put("Code", email.value(EmailReader.CODE));
        }
        values.values().removeIf(java.util.Objects::isNull);
        logLastEmail("Email", values);
    }

    /** Logs the email {@link EmailReader} found last under {@code title}, with the values the test read from it. */
    public static void logLastEmail(String title, java.util.Map<String, String> values) {
        EmailReader.Email email = EmailReader.lastEmail();
        if (email == null || test == null) {
            return;
        }
        String file = UtilsTests.getTestCaseFileName() + "_email.html";
        boolean saved = save(file, email);

        String cell = "padding:4px 10px;border-bottom:1px solid rgba(128,128,128,.2);text-align:left;vertical-align:top";
        StringBuilder html = new StringBuilder()
                .append("<div style='margin:4px 0'><b>").append(escape(title)).append("</b>")
                .append(saved ? " &middot; <a href='" + file + "' target='_blank'>open full size</a>" : "")
                .append("</div><table style='border-collapse:collapse;margin-bottom:8px;font-size:13px'>");
        row(html, cell, "From", email.from);
        row(html, cell, "To", email.to);
        row(html, cell, "Subject", email.subject);
        row(html, cell, "Sent", format(email.sent));
        row(html, cell, "Received", format(email.received));
        values.forEach((name, value) -> html.append("<tr><th style='").append(cell).append("'>").append(escape(name))
                .append("</th><td style='").append(cell)
                .append("'><code style='font-size:15px;font-weight:700;color:#3fb950'>")
                .append(escape(value == null ? "" : value)).append("</code></td></tr>"));
        html.append("</table><small style='opacity:.7'>The email itself is in the Attachments tab.</small>");
        test.log(Status.INFO, html.toString());

        // the email as a card in the Attachments tab, like a UI test's screenshot and recording
        StringBuilder card = new StringBuilder()
                .append("<div class='aqp-media'><figure class='aqp-card'>")
                .append("<div class='aqp-card-h'><span class='aqp-dot'></span>").append(escape(title)).append("<small>")
                .append(saved ? "<a href='" + file + "' target='_blank'>open full size</a>" : escape(email.subject))
                .append("</small></div>")
                // srcdoc keeps the email's own CSS inside the frame; white like a mail client, dark theme or not
                .append("<iframe sandbox='allow-popups' title='").append(escape(title)).append("' srcdoc=\"")
                .append(escape(email.html).replace("\"", "&quot;"))
                .append("\" style='width:100%;height:640px;border:1px solid rgba(128,128,128,.35);"
                        + "border-radius:6px;background:#fff;display:block'></iframe>")
                .append("</figure></div>")
                .append(UtilsTests.attachmentsScript());
        test.log(Status.INFO, card.toString());
    }

    private static boolean save(String file, EmailReader.Email email) {
        try {
            String page = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>" + escape(email.subject)
                    + "</title></head><body style='margin:0;background:#fff'>" + email.html + "</body></html>";
            Files.write(Paths.get(UtilsTests.getReportDir(), file), page.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            System.out.println("Could not save the email next to the report: " + e.getMessage());
            return false;
        }
    }

    private static void row(StringBuilder html, String cell, String name, String value) {
        html.append("<tr><th style='").append(cell).append("'>").append(name).append("</th><td style='")
                .append(cell).append("'>").append(escape(value == null ? "" : value)).append("</td></tr>");
    }

    private static String format(Date date) {
        return date == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
