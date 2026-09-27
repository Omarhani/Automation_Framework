package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * What a run is doing while it runs, for the Live Runner page on Jenkins ({@code jenkins/liveRunner.html}).
 *
 * <p>Each event is one line in the build's console: {@code @@live {json}} - the suite starting (with how many
 * tests it has), each test starting and ending, each step with its element and locator, each check, each API
 * call (the browser's, from {@link NetworkRecorder}, and the API client's, from {@link ApiReport}), console
 * errors. The page reads the console as it grows (Jenkins' progressive log), so nothing but the console is
 * needed for the steps. The lines are ASCII only (Arabic as {@code \\uXXXX}), so any console encoding keeps them.
 *
 * <p>The browser's (or phone's) picture: recording frames are also written as {@code frame-000001.jpg ...} and
 * announced as {@code frame} events; the {@code run} event tells the page the address Jenkins serves them at.
 * In a Jenkins build this needs no setting: they go to {@code live/<BUILD_NUMBER>/} in the job's workspace <b>on
 * every agent of the machine</b> ({@link #jenkinsFolders}), because the job's workspace link
 * ({@code /job/<job>/ws/}) serves the workspace of the job's newest build only - with Test and Stage running at
 * once on two agents, the older build's pictures are still found. Elsewhere {@code -DliveDir} / {@code -DliveUrl}
 * set the folder and its address.
 *
 * <p>Every frame of the run stays while it runs, so the page can go back to any step ("time travel": click a step,
 * see its picture). Only the files this run wrote are ever deleted: when the JVM ends its own frames go and its
 * folders are removed if empty. After that the page takes the step's picture from the published report instead,
 * using the {@code shots} event of each web test (its recording folder and the step each frame shows).
 *
 * <p>On by default in a Jenkins build ({@code BUILD_NUMBER} is set), off elsewhere; {@code -Dlive=on|off} decides.
 */
public final class LiveFeed {

    public static final boolean ON = System.getProperty("live", System.getenv("BUILD_NUMBER") != null ? "on" : "off")
            .trim().matches("(?i)on|true|yes");
    /** Shortest time between two live pictures. */
    private static final long FRAME_GAP_MS = 600;

    /** Where the live pictures go (the first is this build's own folder), and the address the page reads them from. */
    private static final List<File> DIRS = new ArrayList<>();
    private static final String URL;

    static {
        String dir = System.getProperty("liveDir", "").trim();
        String workspace = System.getenv("WORKSPACE"), job = System.getenv("JOB_NAME"), build = System.getenv("BUILD_NUMBER");
        if (!dir.isEmpty()) {
            DIRS.add(new File(dir));
            URL = System.getProperty("liveUrl", "").trim();
        } else if (workspace != null && job != null && build != null) {
            DIRS.addAll(jenkinsFolders(new File(workspace), build));
            StringBuilder url = new StringBuilder();
            for (String part : job.split("/")) {
                url.append("/job/").append(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
            }
            URL = url + "/ws/live/" + build + "/";
        } else {
            URL = "";
        }
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "live-frames");
        t.setDaemon(true);
        return t;
    });
    /** Names of the frames this run wrote that are still on disk, oldest first. */
    private static final Deque<String> frames = new ArrayDeque<>();
    /** The folders this run created itself - the only ones it removes (and only when empty). */
    private static final List<File> created = new ArrayList<>();
    private static volatile long lastFrameAt;
    private static int frameNumber;
    private static boolean folderReady;

    private LiveFeed() {
    }

    /**
     * The folders of a Jenkins build's live pictures: {@code live/<build>} in this workspace, and in the same job's
     * workspace of each sibling agent - for {@code C:\jenkins-agent-2\workspace\Web\webSmoke} that is also
     * {@code C:\jenkins-agent\...} and {@code C:\jenkins-agent-3\...}: the agent roots next to this one, of the same
     * name, that have a {@code workspace} folder.
     */
    static List<File> jenkinsFolders(File workspace, String build) {
        List<File> folders = new ArrayList<>();
        String live = "live" + File.separator + build;
        folders.add(new File(workspace, live));
        String path = workspace.getAbsolutePath();
        String marker = File.separator + "workspace" + File.separator;
        int cut = path.toLowerCase().indexOf(marker);
        if (cut < 0) {
            return folders;
        }
        File root = new File(path.substring(0, cut));
        String job = path.substring(cut + marker.length()).replaceFirst("@\\d+$", "");
        String family = root.getName().replaceFirst("-\\d+$", "");
        File[] siblings = root.getParentFile() == null ? null : root.getParentFile().listFiles(File::isDirectory);
        if (siblings != null) {
            for (File agent : siblings) {
                if (!agent.equals(root) && agent.getName().matches(Pattern.quote(family) + "(-\\d+)?")
                        && new File(agent, "workspace").isDirectory()) {
                    folders.add(new File(new File(agent, "workspace" + File.separator + job), live));
                }
            }
        }
        return folders;
    }

    /** A suite starts: its name, env, browser and how many tests it will run. */
    public static void run(String suite, String env, int tests) {
        emit("run", "suite", suite, "env", env, "browser", System.getProperty("browserType", ""), "tests", tests,
                "frames", DIRS.isEmpty() ? "" : URL);
    }

    public static void testStarted(int number, String method, String block, String type) {
        emit("test", "n", number, "name", method, "block", block == null ? "" : block, "type", type);
    }

    /** pass / fail / skip, and the first line of the error. */
    public static void testFinished(String status, String message) {
        emit("end", "status", status, "msg", message == null ? "" : cap(message, 400));
    }

    /**
     * A web test's recording is written: its frames folder next to the report ({@code 3_verifyThat..._frames/}) and,
     * per frame, the number of the step whose box it shows - so after the run the page can still show any step,
     * from the published report.
     */
    static void recording(String framesFolder, List<Integer> frameSteps) {
        if (!frameSteps.isEmpty()) {
            emit("shots", "dir", framesFolder, "steps", frameSteps);
        }
    }

    public static void step(MethodHandlesWeb.Step s) {
        if ("assert".equals(s.action())) {
            emit("check", "ok", Boolean.TRUE.equals(s.passed()), "d", cap(s.detail(), 300), "c", s.caller());
        } else {
            emit("step", "n", s.number(), "a", s.action(), "e", s.element(), "l", TestTimeline.shortLocator(s.locator()),
                    "c", s.caller());
        }
    }

    /** Longest body sent to the page for a call that went fine, and for one that failed or was refused. */
    private static final int BODY_OK = 1000, BODY_FAILED = 4000;

    /**
     * A call or console error the browser made ({@link NetworkRecorder}'s listener). A call carries everything the
     * page's Network view shows when it is clicked: full URL (tokens masked), when it was sent, HTTP status, the
     * server's Status and Message, time, and the request and response (masked, shortened).
     */
    static void network(Object callOrLine) {
        if (callOrLine instanceof NetworkRecorder.Call c) {
            if (c.isPageLoad()) {
                emit("open", "p", TestTimeline.path(c.url), "url", TestTimeline.maskUrl(c.url), "sent", c.at, "s", c.status,
                        "ms", Math.round(c.ms), "bad", c.failed(), "err", c.error == null ? "" : c.error);
            } else {
                int max = c.failed() || c.refused() ? BODY_FAILED : BODY_OK;
                emit("call", "m", c.method, "p", TestTimeline.path(c.url), "url", TestTimeline.maskUrl(c.url), "sent", c.at,
                        "s", c.status, "ms", Math.round(c.ms), "bad", c.failed(), "warn", c.refused(), "ss", c.serverStatus,
                        "msg", c.serverMessage == null ? "" : cap(c.serverMessage, 300), "err", c.error == null ? "" : c.error,
                        "done", c.done, "req", body(c.requestBody, max), "res", body(c.responseBody, max));
            }
        } else if (callOrLine instanceof NetworkRecorder.ConsoleLine line) {
            emit("console", "text", cap(line.text(), 300));
        }
    }

    /** A call the test's API client made ({@link ApiReport}), with the same details as a browser call. */
    static void apiCall(String method, String endpoint, String url, long sent, int status, long ms, Boolean serverStatus,
                        String message, String request, String response) {
        boolean bad = status >= 400, warn = !bad && Boolean.FALSE.equals(serverStatus);
        int max = bad || warn ? BODY_FAILED : BODY_OK;
        emit("call", "m", method, "p", endpoint, "url", url, "sent", sent, "s", status, "ms", ms, "bad", bad, "warn", warn,
                "ss", serverStatus, "msg", message == null ? "" : cap(message, 300), "done", true,
                "req", body(request == null ? null : ApiReport.mask(request), max),
                "res", body(response == null ? null : ApiReport.mask(response), max), "api", true);
    }

    /** A body for the page: as sent (the page indents JSON itself), without line breaks' extra room, shortened. */
    private static String body(String text, int max) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String compact = text;
        try {
            compact = GSON.toJson(com.google.gson.JsonParser.parseString(text));
        } catch (Exception ignored) {
            // not JSON - as it is
        }
        return compact.length() <= max ? compact : compact.substring(0, max) + " ... (" + (compact.length() - max) + " more characters)";
    }

    /** The browser's current picture (a recording frame, already scaled), written in the background. */
    static void frame(BufferedImage image, int step) {
        if (takeFrame()) {
            WRITER.submit(() -> writeFrame(file -> ImageIO.write(image, "jpg", file), step));
        }
    }

    /** The phone's current picture, as the JPEG its recording stream gave. */
    static void jpeg(byte[] jpeg, int step) {
        if (takeFrame()) {
            WRITER.submit(() -> writeFrame(file -> Files.write(file.toPath(), jpeg), step));
        }
    }

    /** At most one picture every {@link #FRAME_GAP_MS}. */
    private static boolean takeFrame() {
        if (!ON || DIRS.isEmpty() || System.currentTimeMillis() - lastFrameAt < FRAME_GAP_MS) {
            return false;
        }
        lastFrameAt = System.currentTimeMillis();
        return true;
    }

    private interface FrameWriter {
        void write(File file) throws Exception;
    }

    private static synchronized void writeFrame(FrameWriter writer, int step) {
        try {
            if (!folderReady) {
                folderReady = true;
                for (File folder : DIRS) {
                    if (!folder.isDirectory() && folder.mkdirs()) {
                        created.add(folder);
                    }
                }
                Runtime.getRuntime().addShutdownHook(new Thread(LiveFeed::removeOwnFrames, "live-frames-cleanup"));
            }
            String name = String.format("frame-%06d.jpg", ++frameNumber);
            File first = new File(DIRS.get(0), name);
            writer.write(first);
            for (int i = 1; i < DIRS.size(); i++) {
                try {
                    Files.copy(first.toPath(), new File(DIRS.get(i), name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                    // that agent's copy is missing - the page reads whichever workspace is the job's newest
                }
            }
            frames.addLast(name); // kept until the run ends, for going back to a step
            emit("frame", "f", name, "step", step);
        } catch (Exception e) {
            // the live picture is an extra - the run goes on without it
        }
    }

    private static void deleteFrame(String name) {
        for (File folder : DIRS) {
            new File(folder, name).delete();
        }
    }

    /** When the run ends: its own frames, then the folders it created - each only if nothing else is in it. */
    private static synchronized void removeOwnFrames() {
        frames.forEach(LiveFeed::deleteFrame);
        frames.clear();
        for (File folder : created) {
            if (folder.delete()) { // File.delete removes a folder only when it is empty
                folder.getParentFile().delete(); // "live", again only when empty
            }
        }
    }

    private static void emit(String type, Object... fields) {
        if (!ON) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("t", type);
        event.put("at", System.currentTimeMillis());
        for (int i = 0; i + 1 < fields.length; i += 2) {
            event.put((String) fields[i], fields[i + 1]);
        }
        System.out.println("@@live " + ascii(GSON.toJson(event)));
    }

    /** Non-ASCII as JSON \\uXXXX escapes - Arabic survives whatever encoding the console is read in. */
    private static String ascii(String json) {
        StringBuilder out = new StringBuilder(json.length());
        for (char ch : json.toCharArray()) {
            if (ch < 128) {
                out.append(ch);
            } else {
                out.append(String.format("\\u%04x", (int) ch));
            }
        }
        return out.toString();
    }

    private static String cap(String text, int max) {
        if (text == null) {
            return "";
        }
        String line = text.replace('\r', ' ').replace('\n', ' ');
        return line.length() <= max ? line : line.substring(0, max) + "...";
    }
}
