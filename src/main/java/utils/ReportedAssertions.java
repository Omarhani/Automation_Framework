package utils;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.markuputils.Markup;

import java.util.ArrayList;
import java.util.List;

/**
 * Every assertion of a test - web, mobile and API - goes through here (MethodHandlesWeb.myAssertEquals /
 * myAssertContains / myAssertTrue / myAssertFalse delegate to it). One assertion gives:
 * <ul>
 *   <li><b>a number and a name</b>: #1, #2 … per test, and what is checked ("Order details · total"); an unnamed
 *       one is "Assertion at OrdersTests:43". A name used twice in a test becomes "name (2)", so look-alikes stay apart;</li>
 *   <li><b>where</b>: the line that made it, and for a helper (page object, private method) the test line that called
 *       it too - "OrdersPage:319 ← OrdersTests:88";</li>
 *   <li><b>the report</b>: a table row # | Assertion (+ where) | Expected | Actual | Result, and for a failed text assertion a
 *       hint where the two first differ - including invisible differences (no-break space, RTL mark, trailing space)
 *       that look identical on screen (common in RTL and bilingual apps);</li>
 *   <li><b>the log</b>: one line, "❌ ASSERTION #6 Order details · total [6. Order details …] @ OrdersTests:43 |
 *       expected "120.00" | actual "-"";</li>
 *   <li><b>the failure message</b>: "#6 Order details · total: expected [120.00] but found [-]" (the run
 *       report's "Why it failed" column);</li>
 *   <li>the Timeline / Live Runner assertion row.</li>
 * </ul>
 * {@link #softly} runs a group of assertions without stopping at the first failure and fails once at the end with
 * every failed one listed.
 */
public final class ReportedAssertions {

    private ReportedAssertions() {
    }

    /**
     * One assertion: its number in the test (#1, #2 …), its name (a repeated name gets "(2)", "(3)"), where it was
     * made ("OrdersPage:319 ← OrdersTests:88" when a helper made it for a test),
     * expected, actual, passed, and the hint for a failure. {@code named}: the caller gave the name.
     */
    record AssertionResult(int number, String name, boolean named, String where, String expected, String actual,
                           boolean passed, String hint) {
    }

    // non-null while a softly(...) block runs: its failed assertions are collected here instead of thrown
    private static List<AssertionResult> softResults;

    // numbering per test: restarts when the report opens the next test entry (an API test outside the report counts on)
    private static Object numberedTest;
    private static int number;
    private static final java.util.Map<String, Integer> namesSeen = new java.util.HashMap<>();

    public static void equals(String name, Object actual, Object expected) {
        boolean passed;
        try {
            org.testng.Assert.assertEquals(actual, expected);
            passed = true;
        } catch (AssertionError e) {
            passed = false;
        }
        record(name, show(expected), show(actual), passed, passed ? "" : hint(actual, expected));
    }

    /** {@code actual} has {@code part} in it; the report shows the whole actual text next to the expected part. */
    public static void contains(String name, String actual, String part) {
        boolean passed = actual != null && part != null && actual.contains(part);
        String hint = passed ? "" : actual == null ? "the actual value is null"
                : normalise(actual).contains(normalise(String.valueOf(part)))
                ? "it is there apart from spaces or invisible characters" : "the text does not contain it";
        record(name, "contains " + show(part), show(actual), passed, hint);
    }

    public static void isTrue(String name, boolean condition) {
        record(name, "true", String.valueOf(condition), condition, "");
    }

    public static void isFalse(String name, boolean condition) {
        record(name, "false", String.valueOf(condition), !condition, "");
    }

    /**
     * Runs {@code assertions} as one group: a failed assertion is reported and logged at once but does not stop the group,
     * so every field is checked. At the end the report gets a summary table of the group, and if any assertion failed
     * the test fails once, with every failed assertion in the message. An exception that is not an assertion (element not
     * found, timeout) still stops the group at once - after the assertions made so far are summed up.
     */
    public static void softly(String title, Runnable assertions) {
        if (softResults != null) {             // already inside a group: one group, one summary
            assertions.run();
            return;
        }
        List<AssertionResult> results = new ArrayList<>();
        softResults = results;
        RuntimeException stopped = null;
        Error stoppedError = null;
        try {
            assertions.run();
        } catch (RuntimeException e) {
            stopped = e;
        } catch (Error e) {
            stoppedError = e;
        } finally {
            softResults = null;
        }

        List<AssertionResult> failed = results.stream().filter(r -> !r.passed()).toList();
        summary(title, results, failed.size());
        if (stopped != null) throw stopped;
        if (stoppedError != null) throw stoppedError;
        if (!failed.isEmpty()) {
            StringBuilder message = new StringBuilder(title + ": " + failed.size() + " of " + results.size()
                    + " assertions failed - ");
            for (int i = 0; i < failed.size(); i++) {
                message.append(i == 0 ? "" : "; ").append(failureMessage(failed.get(i)));
            }
            throw new AssertionError(message.toString());
        }
    }

    // --- one assertion ---------------------------------------------------------------------------------------------

    private static void record(String name, String expected, String actual, boolean passed, String hint) {
        String where = where();
        boolean named = name != null && !name.isBlank();
        AssertionResult result = new AssertionResult(nextNumber(), distinct(named ? name.trim() : "Assertion at " + where),
                named, where, expected, actual, passed, hint);

        report(result);
        String block = currentBlock();
        System.out.println((passed ? "✅" : "❌") + " ASSERTION #" + result.number() + " " + result.name()
                + (block.isEmpty() ? "" : " [" + block + "]") + (named ? " @ " + where : "")
                + " | expected " + expected + " | actual " + actual + (hint.isEmpty() ? "" : " | " + hint));
        MethodHandlesWeb.assertion(passed, "#" + result.number() + " " + result.name() + (passed ? ": " + actual
                : ": expected " + expected + " but found " + actual + (hint.isEmpty() ? "" : " (" + hint + ")")));

        if (softResults != null) {
            softResults.add(result);
        } else if (!passed) {
            throw new AssertionError(failureMessage(result));
        }
    }

    /** The assertion's number in the running test - restarts with each report entry. */
    private static int nextNumber() {
        Object entry = MethodHandlesWeb.test;
        if (entry != null && entry != numberedTest) {
            numberedTest = entry;
            number = 0;
            namesSeen.clear();
        }
        return ++number;
    }

    /** The name, or "name (2)" / "name (3)" when the test already had an assertion with it - so look-alikes stay apart. */
    private static String distinct(String name) {
        int seen = namesSeen.merge(name, 1, Integer::sum);
        return seen == 1 ? name : name + " (" + seen + ")";
    }

    /** The report entry the assertion belongs to ("5. Approve the order …"), empty outside the report. */
    private static String currentBlock() {
        try {
            return MethodHandlesWeb.test == null ? "" : MethodHandlesWeb.test.getModel().getName();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** "#6 Order details · total: expected [120.00] but found [-] - hint". */
    private static String failureMessage(AssertionResult r) {
        return "#" + r.number() + " " + r.name() + ": "
                + "expected [" + unquote(r.expected()) + "] but found [" + unquote(r.actual()) + "]"
                + (r.hint().isEmpty() ? "" : " - " + r.hint());
    }

    private static String unquote(String shown) {
        return shown.length() >= 2 && shown.startsWith("\"") && shown.endsWith("\"")
                ? shown.substring(1, shown.length() - 1) : shown;
    }

    // --- the report --------------------------------------------------------------------------------------------

    private static final String TABLE_HEAD = "<table class='table table-sm aq-assertion'><tr><th>#</th><th>Assertion</th>"
            + "<th>Expected</th><th>Actual</th><th>Result</th></tr>";

    private static void report(AssertionResult r) {
        ExtentTest test = MethodHandlesWeb.test;
        if (test == null) {
            return;
        }
        String html = TABLE_HEAD + row(r, true)
                + (r.hint().isEmpty() ? "" : "<tr><td></td><td colspan='4'><small>&#128269; " + esc(r.hint())
                + "</small></td></tr>")
                + "</table>";
        Markup markup = () -> html;
        if (r.passed()) {
            test.pass(markup);
        } else {
            test.fail(markup);
        }
    }

    /** One table row: # | name (and where it was made) | expected | actual | result. */
    private static String row(AssertionResult r, boolean withResultText) {
        return "<tr" + (r.passed() ? "" : " style='background:rgba(220,53,69,.18)'") + ">"
                + "<td>" + r.number() + "</td>"
                + "<td><bdi>" + esc(r.name()) + "</bdi><br><small style='opacity:.7'>" + esc(r.where()) + "</small></td>"
                + "<td><bdi>" + esc(r.expected()) + "</bdi></td>"
                + "<td><bdi>" + esc(r.actual()) + "</bdi></td>"
                + "<td>" + (r.passed() ? "&#9989;" + (withResultText ? " PASS" : "") : "&#10060;" + (withResultText ? " FAIL" : ""))
                + "</td></tr>";
    }

    private static void summary(String title, List<AssertionResult> results, int failedCount) {
        String block = currentBlock();
        System.out.println((failedCount == 0 ? "✅" : "❌") + " ASSERTIONS " + title + (block.isEmpty() ? "" : " [" + block + "]")
                + ": " + (results.size() - failedCount) + " of " + results.size() + " passed"
                + (failedCount == 0 ? "" : " - failed: " + results.stream().filter(r -> !r.passed())
                .map(r -> "#" + r.number() + " " + r.name()).collect(java.util.stream.Collectors.joining(", "))));
        ExtentTest test = MethodHandlesWeb.test;
        if (test == null) {
            return;
        }
        StringBuilder html = new StringBuilder("<b><bdi>" + esc(title) + "</bdi></b> - " + (results.size() - failedCount)
                + " of " + results.size() + " assertions passed" + TABLE_HEAD);
        for (AssertionResult r : results) {
            html.append(row(r, false));
        }
        html.append("</table>");
        String done = html.toString();
        Markup markup = () -> done;
        if (failedCount == 0) {
            test.pass(markup);
        } else {
            test.fail(markup);
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // --- where and how ------------------------------------------------------------------------------------------

    /**
     * Where the assertion was made: the first line outside the assertion helpers ("OrdersTests:43"). When that is a
     * page object or other helper, the test line that called it too - "OrdersPage:319 ←
     * OrdersTests:88" - so the same helper assertion made from two tests (or twice in one) is told apart.
     */
    private static String where() {
        List<StackWalker.StackFrame> frames = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> s.filter(f -> !f.getClassName().equals(ReportedAssertions.class.getName())
                                && !f.getClassName().equals(MethodHandlesWeb.class.getName())
                                && !f.getClassName().startsWith("java.") && !f.getClassName().startsWith("jdk.")
                                && !f.getClassName().startsWith("sun.") && !f.getClassName().startsWith("org.testng."))
                        .toList());
        if (frames.isEmpty()) {
            return "unknown";
        }
        StackWalker.StackFrame first = frames.get(0);
        if (isTestMethod(first) || first.getMethodName().startsWith("lambda$")) {
            return line(first);                  // made in the test itself (or in its softly(...) block)
        }
        // made in a helper - a page object or a private method of the test class: add the test line that called it
        return frames.stream().skip(1).filter(ReportedAssertions::isTestMethod).findFirst()
                .map(test -> line(first) + " ← " + line(test))
                .orElse(line(first));
    }

    /** A TestNG @Test method (the frame's own method, looked up by name and parameter types). */
    private static boolean isTestMethod(StackWalker.StackFrame f) {
        try {
            return f.getDeclaringClass().getDeclaredMethod(f.getMethodName(), f.getMethodType().parameterArray())
                    .isAnnotationPresent(org.testng.annotations.Test.class);
        } catch (NoSuchMethodException | RuntimeException e) {
            return false;
        }
    }

    /** "OrdersTests:43" - a lambda (softly) or inner class is named after the class it is in. */
    private static String line(StackWalker.StackFrame f) {
        String cls = f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1);
        int inner = cls.indexOf('$');
        return (inner > 0 ? cls.substring(0, inner) : cls) + ":" + f.getLineNumber();
    }



    /**
     * A value as the report shows it: text in quotes with its invisible characters spelled out
     * ("509 212" with a no-break space → "509⟨NBSP⟩212"), everything else as is (null → null).
     */
    static String show(Object value) {
        if (value == null) {
            return "null";
        }
        if (!(value instanceof CharSequence)) {
            return String.valueOf(value);
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            String name = invisibleName(c);
            out.append(name == null ? String.valueOf(c) : "⟨" + name + "⟩");
        }
        return out.append('"').toString();
    }

    private static String invisibleName(char c) {
        return switch (c) {
            case '\u00A0' -> "NBSP";
            case '\u200B' -> "ZWSP";
            case '\u200C' -> "ZWNJ";
            case '\u200D' -> "ZWJ";
            case '\u200E' -> "LRM";
            case '\u200F' -> "RLM";
            case '\u061C' -> "ALM";
            case '\u2066' -> "LRI";
            case '\u2067' -> "RLI";
            case '\u2068' -> "FSI";
            case '\u2069' -> "PDI";
            case '\u202A', '\u202B', '\u202C', '\u202D', '\u202E' -> "BIDI";
            case '\uFEFF' -> "BOM";
            case '\t' -> "TAB";
            case '\n' -> "LF";
            case '\r' -> "CR";
            default -> null;
        };
    }

    /** Why two different values differ, in one line - empty when there is nothing more to say than the values. */
    static String hint(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == null ? "the actual value is null" : "the expected value is null";
        }
        String a = actual.toString();
        String e = expected.toString();
        if (a.equals(e)) {
            return "same text, different types: " + actual.getClass().getSimpleName() + " vs "
                    + expected.getClass().getSimpleName();
        }
        if (normalise(a).equals(normalise(e))) {
            return "same text apart from spaces or invisible characters";
        }
        if (a.equalsIgnoreCase(e)) {
            return "differs only in upper / lower case";
        }
        if (a.isEmpty()) {
            return "the actual text is empty";
        }
        if (a.trim().matches("[-—–_]+|N/?A|null")) {
            return "the screen shows the placeholder " + show(a.trim()) + " - no value";
        }
        int i = 0;
        while (i < a.length() && i < e.length() && a.charAt(i) == e.charAt(i)) {
            i++;
        }
        String at = "first difference at character " + (i + 1) + ": expected " + describe(e, i) + ", found " + describe(a, i);
        String lengths = a.length() == e.length() ? "" : " (expected " + e.length() + " characters, found " + a.length() + ")";
        String containment = a.contains(e) ? " - the actual text contains the expected one"
                : e.contains(a) ? " - the actual text is part of the expected one" : "";
        return at + lengths + containment;
    }

    private static String describe(String s, int i) {
        if (i >= s.length()) {
            return "end of text";
        }
        char c = s.charAt(i);
        String name = invisibleName(c);
        return (name != null ? "⟨" + name + "⟩" : c == ' ' ? "a space" : "'" + c + "'")
                + String.format(" (U+%04X)", (int) c);
    }

    private static String normalise(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (invisibleName(c) != null && c != '\t' && c != '\n' && c != '\r' && c != '\u00A0') {
                continue;                               // zero-width and direction marks: drop
            }
            out.append(Character.isWhitespace(c) || c == '\u00A0' ? ' ' : c);
        }
        return out.toString().trim().replaceAll(" +", " ");
    }
}
