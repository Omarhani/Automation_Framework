package utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * What the browser's DevTools Network tab would show for the running test - the page's API calls (XHR /
 * fetch) and page loads, with status, time and bodies - plus the console errors. For the report's Timeline
 * and the live view.
 *
 * <p>It reads Chrome / Edge's <b>performance log</b>: {@code BaseTests.setUpBrowserWeb} asks chromedriver to
 * keep the DevTools Network events ({@code goog:loggingPrefs} / {@code ms:loggingPrefs} + perfLoggingPrefs),
 * and a background thread collects them every second. That log does not depend on the Chrome version -
 * Selenium's own DevTools classes ({@code devtools.v1xx}) do, and the agents update Chrome by themselves.
 *
 * <p>A response body is read ({@code Network.getResponseBody}) right after the call finished, while the page
 * still holds it; the server's {@code Status} / {@code Message} come out of it, so a call the server refused
 * with HTTP 200 + {@code Status false} is marked too. Passwords and tokens are masked ({@link ApiReport#mask}).
 * Bodies are kept as the server sent them (up to {@link #BODY_MAX} characters); the report's Timeline pretty-prints
 * and shortens them, the live feed shortens them more.
 *
 * <p>{@code -Dnetwork=off} turns it off: no performance log is asked for, and the Timeline shows the steps only.
 */
public final class NetworkRecorder {

    public static final boolean ENABLED = !System.getProperty("network", "on").trim().matches("(?i)off|false|no");

    /** How often the log is collected; the final calls are collected when the test ends. */
    private static final long POLL_MS = 1000;
    /** Longest body kept (masked, as sent); the report and the live view show less. */
    static final int BODY_MAX = 20000;

    /** One request, as DevTools shows it. {@code at} is the wall clock (ms) it was sent. */
    public static final class Call {
        public final String id;
        public String method, url, type, mime, error, requestBody, responseBody, serverMessage;
        public long at;
        public int status;
        public double ms = -1;
        public Boolean serverStatus;
        public boolean canceled, done;
        private double started;
        private boolean hasPostData;

        Call(String id) {
            this.id = id;
        }

        /** HTTP 4xx / 5xx, or no answer at all (not a call the page cancelled itself). */
        public boolean failed() {
            return status >= 400 || (error != null && !canceled);
        }

        /** HTTP 200, but the server said {@code Status: false}. */
        public boolean refused() {
            return !failed() && Boolean.FALSE.equals(serverStatus);
        }

        public boolean isPageLoad() {
            return "Document".equals(type);
        }

        public long endAt() {
            return at + (long) Math.max(ms, 0);
        }
    }

    public record ConsoleLine(long at, String level, String text) {
    }

    public record Capture(List<Call> calls, List<ConsoleLine> console) {
        public static final Capture EMPTY = new Capture(List.of(), List.of());
    }

    private static volatile NetworkRecorder current;
    /** Gets each call once it finished and each console error - the live view hooks in here. */
    private static volatile Consumer<Object> listener;

    private final ChromiumDriver driver;
    private final Map<String, Call> open = new HashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private final List<ConsoleLine> console = new ArrayList<>();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile boolean running;
    private boolean broken;
    private Thread poller;

    private NetworkRecorder(ChromiumDriver driver) {
        this.driver = driver;
    }

    public static void setListener(Consumer<Object> callsAndConsoleLines) {
        listener = callsAndConsoleLines;
    }

    /** Starts collecting for a new test. Events from before it (the test before, class set-up) are dropped. */
    public static synchronized void start(WebDriver driver) {
        stop();
        if (!ENABLED || !(driver instanceof ChromiumDriver)) {
            return;
        }
        NetworkRecorder recorder = new NetworkRecorder((ChromiumDriver) driver);
        try {
            driver.manage().logs().get(LogType.PERFORMANCE);
            driver.manage().logs().get(LogType.BROWSER);
        } catch (Exception e) {
            System.out.println("ℹ️ No network log from this browser - the Timeline shows the steps only: "
                    + String.valueOf(e.getMessage()).split("\n")[0]);
            return;
        }
        current = recorder;
        recorder.running = true;
        recorder.poller = new Thread(recorder::poll, "network-recorder");
        recorder.poller.setDaemon(true);
        recorder.poller.start();
    }

    /**
     * Stops collecting and returns the test's calls and console errors. The last collection runs on the
     * recorder's thread, and a browser that no longer answers gets 8 s - the report then has what came before.
     */
    public static synchronized Capture stop() {
        NetworkRecorder recorder = current;
        current = null;
        if (recorder == null) {
            return Capture.EMPTY;
        }
        recorder.running = false;
        LockSupport.unpark(recorder.poller);
        try {
            recorder.finished.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (recorder) {
            return new Capture(new ArrayList<>(recorder.calls), new ArrayList<>(recorder.console));
        }
    }

    private void poll() {
        try {
            while (running) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(POLL_MS));
                if (running) {
                    collect();
                }
            }
            collect(); // the test's last calls
        } finally {
            finished.countDown();
        }
    }

    private synchronized void collect() {
        if (broken) {
            return;
        }
        List<Call> ended = new ArrayList<>();
        try {
            for (LogEntry entry : driver.manage().logs().get(LogType.PERFORMANCE)) {
                read(entry.getMessage(), ended);
            }
            for (Call call : ended) {
                readBodies(call);
                tell(call);
            }
            for (LogEntry entry : driver.manage().logs().get(LogType.BROWSER)) {
                String text = entry.getMessage();
                // the network rows already show failed loads
                if (entry.getLevel().intValue() >= Level.SEVERE.intValue() && !text.contains("Failed to load resource")) {
                    ConsoleLine line = new ConsoleLine(entry.getTimestamp(), "error", text);
                    console.add(line);
                    tell(line);
                }
            }
        } catch (Exception e) {
            // the browser closed or hangs: keep what was collected, stop asking
            broken = true;
        }
    }

    private static void tell(Object callOrLine) {
        Consumer<Object> l = listener;
        if (l != null) {
            try {
                l.accept(callOrLine);
            } catch (Exception ignored) {
                // the live view must never break the recording
            }
        }
    }

    private void read(String message, List<Call> ended) {
        JsonObject event = JsonParser.parseString(message).getAsJsonObject().getAsJsonObject("message");
        String method = event.get("method").getAsString();
        if (!method.startsWith("Network.")) {
            return;
        }
        JsonObject p = event.getAsJsonObject("params");
        String id = text(p, "requestId");
        switch (method) {
            case "Network.requestWillBeSent" -> {
                if (p.has("redirectResponse")) {
                    Call before = open.remove(id);
                    if (before != null) {
                        before.status = p.getAsJsonObject("redirectResponse").get("status").getAsInt();
                        finish(before, p, ended);
                    }
                }
                JsonObject request = p.getAsJsonObject("request");
                String type = text(p, "type");
                String url = text(request, "url");
                String verb = text(request, "method");
                if (!wanted(type, url, verb)) {
                    return;
                }
                Call call = new Call(id);
                call.method = verb;
                call.url = url;
                call.type = type;
                call.at = (long) (p.get("wallTime").getAsDouble() * 1000);
                call.started = p.get("timestamp").getAsDouble();
                call.requestBody = text(request, "postData");
                call.hasPostData = call.requestBody == null && request.has("hasPostData")
                        && request.get("hasPostData").getAsBoolean();
                open.put(id, call);
                calls.add(call);
            }
            case "Network.responseReceived" -> {
                Call call = open.get(id);
                if (call != null) {
                    JsonObject response = p.getAsJsonObject("response");
                    call.status = response.get("status").getAsInt();
                    call.mime = text(response, "mimeType");
                }
            }
            case "Network.loadingFinished" -> {
                Call call = open.remove(id);
                if (call != null) {
                    finish(call, p, ended);
                }
            }
            case "Network.loadingFailed" -> {
                Call call = open.remove(id);
                if (call != null) {
                    call.error = text(p, "errorText");
                    call.canceled = p.has("canceled") && p.get("canceled").getAsBoolean();
                    finish(call, p, ended);
                }
            }
            default -> {
                // dataReceived, extra info ... not needed
            }
        }
    }

    private static void finish(Call call, JsonObject params, List<Call> ended) {
        call.ms = (params.get("timestamp").getAsDouble() - call.started) * 1000;
        call.done = true;
        ended.add(call);
    }

    /**
     * Hosts a web app sends monitoring to, not its own work: reCAPTCHA / analytics, LogRocket, Mixpanel, Elastic RUM.
     * Add your app's own monitoring host with {@code -DnetworkNoise=eum\.example\.com|other\.host} (a regex part).
     */
    private static final Pattern NOISE = Pattern.compile(
            "https?://[^/]*(google|gstatic|doubleclick|clarity\\.ms|hotjar|lr-ingest|logrocket|mixpanel|eum\\."
                    + extraNoise() + ")[^/]*/.*");

    private static String extraNoise() {
        String extra = System.getProperty("networkNoise", "");
        return extra.isBlank() || extra.startsWith("${") ? "" : "|" + extra;
    }

    /**
     * The page's own calls and loads: XHR / fetch and documents on http(s), without its static files, CORS
     * pre-flights and the monitoring beacons it sends ({@link #NOISE}).
     */
    private static boolean wanted(String type, String url, String method) {
        if (url == null || !url.startsWith("http") || "OPTIONS".equals(method)) {
            return false;
        }
        if (!"XHR".equals(type) && !"Fetch".equals(type) && !"Document".equals(type)) {
            return false;
        }
        String u = url.toLowerCase();
        return !u.contains("/assets/") && !u.contains("/intake/v2/rum/") && !NOISE.matcher(u).matches();
    }

    private void readBodies(Call call) {
        if (call.isPageLoad() || call.error != null) {
            return;
        }
        try {
            if (call.hasPostData) {
                Object data = driver.executeCdpCommand("Network.getRequestPostData", Map.of("requestId", call.id)).get("postData");
                call.requestBody = data == null ? null : data.toString();
            }
        } catch (Exception ignored) {
            // the page no longer has it
        }
        if (call.mime == null || !(call.mime.contains("json") || call.mime.startsWith("text/"))) {
            return;
        }
        try {
            Map<String, Object> result = driver.executeCdpCommand("Network.getResponseBody", Map.of("requestId", call.id));
            Object body = result.get("body");
            if (body != null && !Boolean.TRUE.equals(result.get("base64Encoded"))) {
                String text = body.toString();
                readServerStatus(call, text);
                call.responseBody = cap(ApiReport.mask(text), BODY_MAX);
            }
        } catch (Exception ignored) {
            // the page moved on before the body was read - status and time are still shown
        }
        if (call.requestBody != null) {
            call.requestBody = cap(ApiReport.mask(call.requestBody), BODY_MAX);
        }
    }

    private static void readServerStatus(Call call, String body) {
        try {
            JsonElement json = JsonParser.parseString(body);
            if (json.isJsonObject()) {
                JsonObject o = json.getAsJsonObject();
                if (o.has("Status") && o.get("Status").isJsonPrimitive() && o.get("Status").getAsJsonPrimitive().isBoolean()) {
                    call.serverStatus = o.get("Status").getAsBoolean();
                }
                if (o.has("Message") && o.get("Message").isJsonPrimitive()) {
                    call.serverMessage = o.get("Message").getAsString();
                }
            }
        } catch (Exception ignored) {
            // not JSON
        }
    }

    private static String cap(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "\n... (" + (text.length() - max) + " more characters)";
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }
}
