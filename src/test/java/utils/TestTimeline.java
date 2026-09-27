package utils;

import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The web test's Timeline, like Cypress's command log: every step with the element and locator it used, every
 * API call and page load the browser made (from {@link NetworkRecorder}), the assertions, console errors and
 * the failure - in the order they happened. It is a tab of the test's report entry, next to Steps and
 * Attachments: the list on the left, the recording frame on the right. Hovering a row shows its frame (the
 * step's box, or the page right after a call), clicking keeps it, and clicking a call opens its request and
 * response.
 *
 * <p>BaseTests starts it with the recording ({@link #start}) and ends it before the attachments ({@link #stop});
 * {@link UtilsTests#addTimeline} writes the tab.
 */
public final class TestTimeline {

    private static final List<MethodHandlesWeb.Step> steps = Collections.synchronizedList(new ArrayList<>());
    private static volatile long startedAt;
    private static volatile NetworkRecorder.Capture network = NetworkRecorder.Capture.EMPTY;
    /** The element label the step box script returns: {@code button "Save"}, or just {@code mat-select}. */
    private static final Pattern LABEL = Pattern.compile("^(\\S+)(?: \"(.*)\")?$", Pattern.DOTALL);

    private TestTimeline() {
    }

    public static void start(WebDriver driver) {
        steps.clear();
        network = NetworkRecorder.Capture.EMPTY;
        startedAt = System.currentTimeMillis();
        // the same steps and calls go to the live view as they happen
        MethodHandlesWeb.setStepListener(step -> {
            steps.add(step);
            LiveFeed.step(step);
        });
        NetworkRecorder.setListener(LiveFeed::network);
        NetworkRecorder.start(driver);
    }

    public static void stop() {
        MethodHandlesWeb.setStepListener(null);
        network = NetworkRecorder.stop();
    }

    private record Row(long at, String html) {
    }

    /**
     * The Timeline tab of the entry, or null when the test had no steps and no calls (nothing recorded).
     * {@code framesBase} is the recording's folder, {@code frameSteps} / {@code frameTimes} what each of its
     * frames shows, as {@link BrowserGifRecorder} noted it.
     */
    static String html(String framesBase, List<Integer> frameSteps, List<Long> frameTimes, ITestResult result) {
        List<MethodHandlesWeb.Step> stepList;
        synchronized (steps) {
            stepList = new ArrayList<>(steps);
        }
        NetworkRecorder.Capture net = network;
        if (stepList.isEmpty() && net.calls().isEmpty()) {
            return null;
        }
        boolean frames = !frameSteps.isEmpty() && frameSteps.size() == frameTimes.size();
        long end = System.currentTimeMillis();

        List<Row> rows = new ArrayList<>();
        int stepCount = 0, failedAsserts = 0;
        for (MethodHandlesWeb.Step s : stepList) {
            int frame = frames ? frameOfStep(frameSteps, s.number()) : -1;
            if ("assert".equals(s.action())) {
                boolean ok = Boolean.TRUE.equals(s.passed());
                failedAsserts += ok ? 0 : 1;
                rows.add(new Row(s.at(), row("aqt-as" + (ok ? "" : " bad"), frame,
                        (ok ? "Check passed: " : "Check failed: ") + s.detail(), ok ? "&#10003;" : "&#10007;", "assert",
                        "<bdi>" + esc(s.detail()) + "</bdi>", caller(s.caller()), "", s.at())));
            } else {
                stepCount++;
                String locator = shortLocator(s.locator());
                rows.add(new Row(s.at(), row("aqt-s", frame,
                        "Step " + s.number() + " · " + s.action() + " · " + s.element(),
                        String.valueOf(s.number()), s.action(), element(s.element()),
                        (locator.isEmpty() ? "" : "<bdi class='aqt-code' title='" + esc(s.locator()) + "'>" + esc(locator) + "</bdi>")
                                + caller(s.caller()), "", s.at())));
            }
        }

        int calls = 0, failed = 0, refused = 0;
        for (NetworkRecorder.Call c : net.calls()) {
            int frame = frames ? frameAfter(frameTimes, c.endAt()) : -1;
            String path = path(c.url);
            String code = "<bdi class='aqt-code'>" + esc(path) + "</bdi>";
            if (c.isPageLoad()) {
                rows.add(new Row(c.at, row("aqt-n aqt-doc" + (c.failed() ? " bad" : ""), frame,
                        "Page load " + path, "", "open", code, "", status(c), c.at)));
                continue;
            }
            calls++;
            failed += c.failed() ? 1 : 0;
            refused += c.refused() ? 1 : 0;
            String cls = "aqt-n" + (c.failed() ? " bad" : c.refused() ? " warn" : "");
            String message = c.serverMessage != null && !c.serverMessage.isEmpty() && (c.failed() || c.refused())
                    ? "<bdi>" + esc(c.serverMessage) + "</bdi>" : "";
            rows.add(new Row(c.at, row(cls, frame, c.method + " " + path + " → " + statusText(c) + " (the page right after)",
                    "", c.method, code, message, status(c) + " <span class='aqt-ms'>" + ms(c) + "</span>", c.at)
                    + details(c)));
        }

        for (NetworkRecorder.ConsoleLine line : net.console()) {
            int frame = frames ? frameAfter(frameTimes, line.at()) : -1;
            rows.add(new Row(line.at(), row("aqt-con bad", frame, "Console error", "!", "console",
                    "<bdi class='aqt-code'>" + esc(line.text()) + "</bdi>", "", "", line.at())));
        }

        if (result != null && result.getStatus() != ITestResult.SUCCESS) {
            Throwable error = result.getThrowable();
            String why = result.getStatus() == ITestResult.SKIP ? "skipped" : "failed";
            String message = error == null ? "" : String.valueOf(error).split("\n")[0];
            rows.add(new Row(end, row("aqt-fail bad", frames ? frameSteps.size() - 1 : -1, "Test " + why + ": " + message,
                    "&#10007;", why, "<bdi>" + esc(message) + "</bdi>", "", "", end)));
        }

        rows.sort(Comparator.comparingLong(Row::at)); // stable: a step stays before the calls it started

        StringBuilder summary = new StringBuilder()
                .append(stepCount).append(stepCount == 1 ? " step" : " steps")
                .append(" &middot; ").append(calls).append(calls == 1 ? " API call" : " API calls");
        if (failed > 0) {
            summary.append(" &middot; <b class='aqt-bad'>").append(failed).append(" failed</b>");
        }
        if (refused > 0) {
            summary.append(" &middot; <b class='aqt-warn'>").append(refused).append(" refused (Status false)</b>");
        }
        if (!net.console().isEmpty()) {
            summary.append(" &middot; <b class='aqt-bad'>").append(net.console().size()).append(" console error")
                    .append(net.console().size() == 1 ? "" : "s").append("</b>");
        }
        if (failedAsserts > 0) {
            summary.append(" &middot; <b class='aqt-bad'>").append(failedAsserts).append(" failed check")
                    .append(failedAsserts == 1 ? "" : "s").append("</b>");
        }
        summary.append(" &middot; ").append(seconds(end - startedAt));
        if (!NetworkRecorder.ENABLED) {
            summary.append(" &middot; network off (-Dnetwork=off)");
        }

        StringBuilder html = new StringBuilder()
                .append("<div class='aqp-media aqt-wrap' data-tab='tl' data-n='").append(stepCount).append("'>")
                .append("<div class='aqt' data-flt='all' data-base='").append(esc(framesBase)).append("'>")
                .append("<div class='aqt-h'><span class='aqt-sum'>").append(summary).append("</span>")
                .append("<span class='aqt-fs'>")
                .append("<button type='button' data-flt='all' class='on'>All</button>")
                .append("<button type='button' data-flt='steps'>Steps</button>")
                .append("<button type='button' data-flt='net'>Network</button>")
                .append("<button type='button' data-flt='err'>Problems</button>")
                .append("</span></div><div class='aqt-b'><div class='aqt-list'>");
        rows.forEach(r -> html.append(r.html()));
        html.append("</div>");
        if (frames) {
            html.append("<div class='aqt-v'><img class='aqt-img' src='").append(esc(framesBase)).append("000.jpg'>")
                    .append("<div class='aqt-cap'>Hover a row to see the page at that moment, click to keep it</div></div>");
        }
        return html.append("</div></div></div>").toString();
    }

    /**
     * One row: number, action, then two lines - what it acted on, and under it the locator / page method (a
     * step), the server's message (a refused call) or nothing - and on the right the status and the time.
     */
    private static String row(String cls, int frame, String caption, String number, String action, String line1,
                              String line2, String right, long at) {
        return "<div class='aqt-r " + cls + "'" + (frame >= 0 ? " data-f='" + frame + "'" : "")
                + " data-cap='" + esc(caption) + "'>"
                + "<span class='aqt-no'>" + number + "</span>"
                + "<span class='aqt-a'>" + esc(action) + "</span>"
                + "<span class='aqt-m'><span class='aqt-l1'>" + line1 + "</span>"
                + (line2.isEmpty() ? "" : "<span class='aqt-l2'>" + line2 + "</span>") + "</span>"
                + (right.isEmpty() ? "" : "<span class='aqt-rt'>" + right + "</span>")
                + "<span class='aqt-t'>" + seconds(at - startedAt) + "</span></div>";
    }

    /** {@code button "Save"} → the tag, then its text in a box of its own (Arabic text keeps its order). */
    private static String element(String label) {
        Matcher m = LABEL.matcher(label == null ? "" : label.trim());
        if (!m.matches()) {
            return "<bdi>" + esc(label) + "</bdi>";
        }
        return "<span class='aqt-tag'>" + esc(m.group(1)) + "</span>"
                + (m.group(2) == null ? "" : " <bdi class='aqt-txt'>" + esc(m.group(2)) + "</bdi>");
    }

    private static String caller(String caller) {
        return caller == null || caller.isEmpty() ? "" : " <span class='aqt-c'>" + esc(caller) + "</span>";
    }

    /** Request and response of a call, opened by clicking its row. */
    private static String details(NetworkRecorder.Call c) {
        StringBuilder d = new StringBuilder("<div class='aqt-d' style='display:none'>")
                .append("<div><b>").append(esc(c.method)).append("</b> <bdi class='aqt-code'>").append(esc(maskUrl(c.url)))
                .append("</bdi></div><div>HTTP ").append(esc(statusText(c))).append(" &middot; ").append(ms(c));
        if (c.serverMessage != null && !c.serverMessage.isEmpty()) {
            d.append(" &middot; <bdi>").append(esc(c.serverMessage)).append("</bdi>");
        }
        d.append("</div>");
        int max = c.failed() || c.refused() ? BODY_FAILED : BODY_OK;
        if (c.requestBody != null && !c.requestBody.isEmpty()) {
            d.append("<div class='aqt-k'>Request</div><pre>").append(esc(cap(pretty(c.requestBody), max))).append("</pre>");
        }
        if (c.responseBody != null && !c.responseBody.isEmpty()) {
            d.append("<div class='aqt-k'>Response</div><pre>").append(esc(cap(pretty(c.responseBody), max))).append("</pre>");
        } else if (c.done && !c.failed()) {
            d.append("<div class='aqt-k'>Response</div><pre>(no text body, or the page had moved on before it was read)</pre>");
        }
        return d.append("</div>").toString();
    }

    /** The frame that shows step n's box: its own, else the last one before it. */
    static int frameOfStep(List<Integer> frameSteps, int n) {
        int before = 0;
        for (int i = 0; i < frameSteps.size(); i++) {
            if (frameSteps.get(i) == n) {
                return i;
            }
            if (frameSteps.get(i) < n) {
                before = i;
            }
        }
        return before;
    }

    /** The first frame taken at or after {@code at}: the page right after a call or message. */
    static int frameAfter(List<Long> frameTimes, long at) {
        for (int i = 0; i < frameTimes.size(); i++) {
            if (frameTimes.get(i) >= at) {
                return i;
            }
        }
        return frameTimes.size() - 1;
    }

    /** {@code xpath: //button[...]} → {@code //button[...]}, {@code css selector: .x} → {@code css .x}. */
    static String shortLocator(String locator) {
        if (locator == null) {
            return "";
        }
        return locator.replaceFirst("^xpath: ", "").replaceFirst("^css selector: ", "css ");
    }

    private static String status(NetworkRecorder.Call c) {
        String cls = c.failed() ? "aqt-st bad" : c.refused() ? "aqt-st warn" : !c.done || c.canceled ? "aqt-st off" : "aqt-st";
        return "<span class='" + cls + "'>" + esc(statusText(c)) + "</span>";
    }

    private static String statusText(NetworkRecorder.Call c) {
        if (c.canceled) {
            return "canceled";
        }
        if (c.error != null) {
            return c.error.replaceFirst("^net::", "");
        }
        if (!c.done) {
            return "no answer yet"; // the page reloaded, or the test ended, before it answered
        }
        // every JSON answer shows the server's own Status too: 200 · Status true / 200 · Status false
        return c.serverStatus != null ? c.status + " · Status " + c.serverStatus : String.valueOf(c.status);
    }

    /** Longest body shown in the report for a call that went fine, and for one that failed or was refused. */
    private static final int BODY_OK = 1500, BODY_FAILED = 6000;
    private static final com.google.gson.Gson PRETTY = new com.google.gson.GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    /** JSON indented for reading; anything else (or JSON cut short at {@link NetworkRecorder#BODY_MAX}) as it is. */
    static String pretty(String body) {
        try {
            return PRETTY.toJson(com.google.gson.JsonParser.parseString(body));
        } catch (Exception e) {
            return body;
        }
    }

    static String cap(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "\n... (" + (text.length() - max) + " more characters)";
    }

    private static String ms(NetworkRecorder.Call c) {
        return c.ms < 0 ? "" : Math.round(c.ms) + " ms";
    }

    /** Path and query of a URL, without the host. */
    static String path(String url) {
        String p = maskUrl(url).replaceFirst("^https?://[^/]+", "");
        return p.isEmpty() ? "/" : p;
    }

    static String maskUrl(String url) {
        return url == null ? "" : url.replaceAll("(?i)((?:token|password|verificationcode)=)[^&]*", "$1***");
    }

    private static String seconds(long ms) {
        return String.format(Locale.ROOT, "%.1f s", Math.max(ms, 0) / 1000.0);
    }

    private static String esc(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }

    /**
     * The Timeline's look and behaviour, inside the report's one player script (it runs once for all entries;
     * Extent writes each log twice, so everything is delegated from document and kept in data attributes).
     */
    static final String SCRIPT = ""
            + "var tst=document.createElement('style');tst.textContent='"
            + ".aqt-wrap{display:block!important}"
            + ".aqt-h{display:flex;flex-wrap:wrap;align-items:center;gap:10px;margin-bottom:10px;font-size:12px}"
            + ".aqt-sum{opacity:.9}.aqt-bad{color:#e5534b}.aqt-warn{color:#d4a72c}"
            + ".aqt-fs{margin-left:auto;display:flex;gap:4px}"
            + ".aqt-fs button{background:rgba(128,128,128,.12);color:inherit;border:1px solid rgba(128,128,128,.25);border-radius:4px;padding:3px 10px;font-size:12px;cursor:pointer}"
            + ".aqt-fs button.on{background:rgba(79,140,255,.18);border-color:#4f8cff;color:#4f8cff}"
            + ".aqt-b{display:flex;gap:14px;align-items:flex-start}"
            + ".aqt-list{flex:1 1 58%;min-width:0;max-height:680px;overflow-y:auto;overflow-x:hidden;"
            + "border:1px solid rgba(128,128,128,.2);border-radius:8px;padding:4px;font-size:12.5px}"
            + ".aqt-v{flex:1 1 42%;min-width:0;position:sticky;top:8px}"
            + ".aqt-img{width:100%;display:block;border:1px solid rgba(128,128,128,.3);border-radius:6px}"
            + ".aqt-cap{font-size:12px;opacity:.8;margin-top:6px;word-break:break-word}"
            + ".aqt-r{display:flex;align-items:flex-start;gap:8px;padding:5px 6px;border-radius:5px;cursor:pointer;line-height:1.45}"
            + ".aqt-r:hover{background:rgba(128,128,128,.13)}.aqt-r.sel{box-shadow:inset 3px 0 0 #4f8cff;background:rgba(79,140,255,.14)}"
            + ".aqt-no{min-width:22px;text-align:right;opacity:.6;font-variant-numeric:tabular-nums}"
            + ".aqt-a{min-width:50px;font-weight:600;color:#ff5c7a}"
            + ".aqt-m{flex:1;min-width:0;display:flex;flex-direction:column}"
            + ".aqt-l1{word-break:break-word}"
            + ".aqt-l2{font-size:11px;opacity:.65;margin-top:1px;word-break:break-all}"
            + ".aqt-code{font-family:Consolas,monospace;font-size:11.5px;direction:ltr;unicode-bidi:isolate;word-break:break-all}"
            + ".aqt-tag{font-family:Consolas,monospace;opacity:.6}.aqt-txt{font-weight:600}"
            + ".aqt-c{opacity:.8;margin-left:6px;white-space:nowrap}"
            + ".aqt-rt{white-space:nowrap;text-align:right}.aqt-ms{opacity:.6}"
            + ".aqt-t{opacity:.45;white-space:nowrap;font-size:11px;min-width:40px;text-align:right;font-variant-numeric:tabular-nums}"
            + ".aqt-n{padding-left:34px}.aqt-n .aqt-a{color:#4f8cff;min-width:40px}"
            + ".aqt-st{font-weight:600;color:#3fb950}.aqt-st.bad{color:#e5534b}.aqt-st.warn{color:#d4a72c}.aqt-st.off{color:inherit;opacity:.5;font-weight:400}"
            + ".aqt-r.bad{background:rgba(229,83,75,.12)}.aqt-r.warn{background:rgba(212,167,44,.12)}"
            + ".aqt-n.bad .aqt-a,.aqt-con .aqt-a,.aqt-fail .aqt-a,.aqt-as.bad .aqt-a{color:#e5534b}"
            + ".aqt-doc .aqt-a{color:#a371f7}.aqt-as .aqt-a{color:#3fb950}"
            + ".aqt-fail{background:rgba(229,83,75,.22)!important}"
            + ".aqt-d{margin:2px 0 6px 34px;padding:8px 10px;border-radius:6px;background:rgba(128,128,128,.09);font-size:12px;word-break:break-all}"
            + ".aqt-k{margin-top:8px;font-weight:600;opacity:.8}"
            + ".aqt-d pre{white-space:pre-wrap;word-break:break-all;max-height:320px;overflow:auto;margin:4px 0 0;font-size:11.5px}"
            + ".aqt[data-flt=steps] .aqt-n,.aqt[data-flt=steps] .aqt-con,.aqt[data-flt=steps] .aqt-d{display:none}"
            + ".aqt[data-flt=net] .aqt-s,.aqt[data-flt=net] .aqt-as{display:none}"
            + ".aqt[data-flt=err] .aqt-r:not(.bad):not(.warn),.aqt[data-flt=err] .aqt-d{display:none}"
            + "';document.head.appendChild(tst);"
            + "function tlPick(r,keep){var t=r.closest('.aqt');if(!t)return;var f=r.dataset.f,img=q(t,'.aqt-img');"
            + "if(img&&f!==undefined){img.src=t.dataset.base+('00'+f).slice(-3)+'.jpg';q(t,'.aqt-cap').textContent=r.dataset.cap||'';}"
            + "if(keep){t.querySelectorAll('.aqt-r.sel').forEach(function(x){x.classList.remove('sel');});r.classList.add('sel');}}"
            + "document.addEventListener('click',function(e){var t=e.target;if(!t.closest)return;"
            + "var fb=t.closest('.aqt-fs button');if(fb){var a=fb.closest('.aqt');a.dataset.flt=fb.dataset.flt;"
            + "a.querySelectorAll('.aqt-fs button').forEach(function(b){b.classList.toggle('on',b===fb);});return;}"
            + "var r=t.closest('.aqt-r');if(!r)return;tlPick(r,true);"
            + "var d=r.nextElementSibling;if(d&&d.classList.contains('aqt-d'))d.style.display=d.style.display==='none'?'':'none';});"
            + "document.addEventListener('mouseover',function(e){var r=e.target.closest&&e.target.closest('.aqt-r');if(r)tlPick(r,false);});"
            // leaving the list shows the kept row's frame again
            + "document.addEventListener('mouseout',function(e){var l=e.target.closest&&e.target.closest('.aqt-list');"
            + "if(!l||(e.relatedTarget&&l.contains(e.relatedTarget)))return;var s=q(l,'.aqt-r.sel');if(s)tlPick(s,false);});";
}
