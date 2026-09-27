package utils;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.Rectangle;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Records the phone's screen for the report - the mobile twin of {@link BrowserGifRecorder}, with
 * the same output, so {@link UtilsTests#addAttachment} shows it in the same player:
 * <ul>
 *   <li>{@code <methodName>_frames/000.jpg, 001.jpg ...} plus their durations;</li>
 *   <li>{@code <methodName>.gif}, built in the background after the test.</li>
 * </ul>
 *
 * <p>Frames come from UiAutomator2's MJPEG stream (capability {@code appium:mjpegServerPort} =
 * {@link #MJPEG_PORT}): the phone pushes ~8 JPEGs a second at half size, on its own connection, so
 * recording never queues behind the test's Appium commands. Frames are saved as they arrive - no
 * decoding - except the one a step lands on.
 *
 * <p>A phone screen can't carry an overlay the way a web page does, so the step's numbered box
 * ("3 click", "4 type" - from {@link MethodHandlesMobile}) is drawn onto the frame on screen when
 * the step starts, i.e. the screen the step acts on. Two steps on one frame each get their own copy.
 */
public class MobileScreenRecorder {

    /** Local port Appium forwards the phone's MJPEG stream to. */
    public static final int MJPEG_PORT = 7810;

    /** Shortest time between two saved frames; the stream sends ~8 a second. */
    private static final long MIN_GAP_MS = 250;
    /** A step's frame stays at least this long in the player, so its box can be read. */
    private static final int MIN_STEP_FRAME_MS = 400;
    /** How long the last frame stays on screen before the recording loops. */
    private static final int LAST_FRAME_MS = 2000;

    private static volatile MobileScreenRecorder current;

    private final File framesDir;
    private final File gifFile;
    /** Phone screen width in the pixels element rects use, to scale boxes to the smaller frames. */
    private final int screenWidth;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mobile-frame-writer");
        t.setDaemon(true);
        return t;
    });
    private Thread reader;
    private volatile boolean running;
    private volatile HttpURLConnection connection;

    // guarded by this: the newest frame, written once the next one arrives (only then is its duration known)
    private byte[] pending;
    private long pendingAt;
    private Step pendingStep;
    private int frameCount;
    private final List<Integer> frameDurations = new ArrayList<>();

    private record Step(Rectangle rect, int number, String action) {
    }

    private MobileScreenRecorder(File framesDir, File gifFile, int screenWidth) {
        this.framesDir = framesDir;
        this.gifFile = gifFile;
        this.screenWidth = screenWidth;
    }

    /** Starts recording the phone for {@code methodName}. Recording is best effort - it never fails the test. */
    public static synchronized void startRecord(AndroidDriver driver, String methodName) {
        stopRecord();
        File folder = new File(UtilsTests.getReportDir());
        File framesDir = new File(folder, BrowserGifRecorder.framesFolderName(methodName));
        BrowserGifRecorder.deleteFolder(framesDir); // a re-run of the same method must not mix in the old frames
        framesDir.mkdirs();
        BrowserGifRecorder.setLastRecording(framesDir, List.of(), null);

        int screenWidth;
        try {
            screenWidth = driver.manage().window().getSize().getWidth();
        } catch (Exception e) {
            screenWidth = 0; // no boxes, frames still recorded
        }
        current = new MobileScreenRecorder(framesDir, new File(folder, methodName + ".gif"), screenWidth);
        current.start();

        MethodHandlesMobile.resetStepNumber();
        MethodHandlesMobile.setOnStep((rect, number, action) -> {
            MobileScreenRecorder recorder = current;
            if (recorder != null) {
                recorder.onStep(new Step(rect, number, action));
            }
        });
    }

    /** Stops the running recording and hands it to the report. Safe to call when nothing is recording. */
    public static synchronized void stopRecord() {
        MethodHandlesMobile.setOnStep(null);
        MobileScreenRecorder recorder = current;
        if (recorder == null) {
            return;
        }
        current = null;
        recorder.stop();
        BrowserGifRecorder.setLastRecording(recorder.framesDir, recorder.frameDurations, recorder.gifFile);
    }

    private void start() {
        running = true;
        reader = new Thread(this::readStream, "mobile-recorder");
        reader.setDaemon(true);
        reader.start();
    }

    private void stop() {
        running = false;
        HttpURLConnection c = connection;
        if (c != null) {
            c.disconnect(); // unblocks the reader
        }
        try {
            reader.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            if (pending != null) {
                queueWrite(pending, pendingStep, LAST_FRAME_MS);
                pending = null;
            }
        }
        writer.shutdown();
        try {
            writer.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- reader thread ----------

    /** Reads the multipart MJPEG stream; reconnects if it drops while the test is still running. */
    private void readStream() {
        boolean warned = false;
        while (running) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + MJPEG_PORT + "/").openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(5000);
                connection = c;
                try (InputStream in = new BufferedInputStream(c.getInputStream())) {
                    int length = -1;
                    String line;
                    while (running && (line = readLine(in)) != null) {
                        if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                            length = Integer.parseInt(line.substring(15).trim());
                        } else if (line.isEmpty() && length > 0) {
                            byte[] jpeg = in.readNBytes(length);
                            if (jpeg.length == length) {
                                onFrame(jpeg);
                            }
                            length = -1;
                        }
                    }
                }
            } catch (Exception e) {
                if (running && !warned) {
                    warned = true;
                    System.out.println("⚠️ Phone recording: no MJPEG stream on port " + MJPEG_PORT
                            + " (is appium:mjpegServerPort set?) - " + e.getMessage());
                }
                if (running) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
        }
        return b == -1 && line.size() == 0 ? null : line.toString(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private synchronized void onFrame(byte[] jpeg) {
        long now = System.currentTimeMillis();
        if (pending != null) {
            if (now - pendingAt < MIN_GAP_MS || Arrays.equals(jpeg, pending)) {
                return; // too soon, or nothing changed - the previous frame just lasts longer
            }
            queueWrite(pending, pendingStep, (int) (now - pendingAt));
        }
        pending = jpeg;
        pendingAt = now;
        pendingStep = null;
    }

    // ---------- step (test thread) ----------

    private synchronized void onStep(Step step) {
        if (pending == null || screenWidth <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (pendingStep != null) {
            // the previous step is still on this frame: keep it on its own copy
            queueWrite(pending, pendingStep, Math.max(MIN_STEP_FRAME_MS, (int) (now - pendingAt)));
            pendingAt = now;
        }
        pendingStep = step;
    }

    // ---------- writer thread ----------

    private void queueWrite(byte[] jpeg, Step step, int durationMs) {
        int index = frameCount++;
        frameDurations.add(step != null ? Math.max(MIN_STEP_FRAME_MS, durationMs) : durationMs);
        File file = new File(framesDir, String.format("%03d.jpg", index));
        writer.submit(() -> {
            try {
                LiveFeed.jpeg(jpeg, step == null ? 0 : step.number()); // the live view's picture, in a Jenkins build
                if (step == null) {
                    Files.write(file.toPath(), jpeg);
                } else {
                    BufferedImage frame = ImageIO.read(new ByteArrayInputStream(jpeg));
                    drawStep(frame, step);
                    ImageIO.write(frame, "jpg", file);
                }
            } catch (Exception e) {
                System.out.println("⚠️ Phone recording frame skipped for " + gifFile.getName() + ": " + e.getMessage());
            }
        });
    }

    /** The web step highlight, drawn on the frame: box, number badge and action label. */
    private void drawStep(BufferedImage frame, Step step) {
        double scale = frame.getWidth() / (double) screenWidth;
        int x = (int) Math.round(step.rect().getX() * scale) - 4;
        int y = (int) Math.round(step.rect().getY() * scale) - 4;
        int w = (int) Math.round(step.rect().getWidth() * scale) + 8;
        int h = (int) Math.round(step.rect().getHeight() * scale) + 8;

        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color accent = new Color(0xff, 0x2d, 0x55);

        g.setColor(new Color(0xff, 0x2d, 0x55, 30));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(accent);
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(x, y, w, h, 10, 10);

        g.setFont(new Font("Arial", Font.BOLD, 15));
        FontMetrics fm = g.getFontMetrics();
        String number = String.valueOf(step.number());
        int badge = Math.max(26, fm.stringWidth(number) + 12);
        int bx = Math.max(0, x - 12);
        int by = Math.max(0, y - 14);
        g.fillRoundRect(bx, by, badge, 26, 26, 26);
        g.setColor(Color.WHITE);
        g.drawString(number, bx + (badge - fm.stringWidth(number)) / 2, by + 18);

        g.setFont(new Font("Arial", Font.BOLD, 13));
        fm = g.getFontMetrics();
        int lx = bx + badge + 4;
        int lw = fm.stringWidth(step.action()) + 12;
        if (lx + lw > frame.getWidth()) {
            lx = Math.max(0, frame.getWidth() - lw);
        }
        g.setColor(new Color(0x1f, 0x29, 0x37));
        g.fillRoundRect(lx, by + 2, lw, 22, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(step.action(), lx + 6, by + 18);
        g.dispose();
    }
}
