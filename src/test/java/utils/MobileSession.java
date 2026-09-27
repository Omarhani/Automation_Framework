package utils;

import io.appium.java_client.android.AndroidDriver;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mobile side of {@link UtilsTests#loginProgrammatically(String)}: the phone's login, saved once and put back.
 *
 * <p>Many apps keep everything they know after a login - user, session and refresh tokens, account info,
 * the chosen server - in one plain file of their data folder (a Flutter app:
 * {@code shared_prefs/FlutterSharedPreferences.xml}; a native app: its own {@code shared_prefs/<name>.xml}).
 * Set that path in testData.json MOBILE.SESSION_FILE. A debuggable test build lets
 * {@code run-as <package>} read and write that file.
 * Through {@code mobile: shell}, not Appium's pullFile / pushFile: those copy via /data/local/tmp, which the
 * app's user cannot write on Android 12 ("cp: No such file or directory"). So: log in on the UI once, keep the file, and every later
 * block writes it back while the app is stopped and starts the app - it opens straight on home.
 *
 * <p>Kept in memory for the run, per server + user: each run makes its own user, so a file from an
 * earlier run would belong to a deleted user. A release build is not debuggable - there this cannot work,
 * and the caller falls back to the UI login.
 */
public final class MobileSession {

    /** testData.json MOBILE.SESSION_FILE; empty = the feature is off and tests log in on the UI. */
    private static String prefsFile() {
        String file = reader.ReadDataFromJson.dataModel().MOBILE.SESSION_FILE;
        return file == null ? "" : file.trim();
    }

    /** True when the data file names the app's login file, so a phone login can be saved and put back. */
    public static boolean enabled() {
        return !prefsFile().isEmpty();
    }

    private record Saved(String appPackage, String file, byte[] prefs) {}

    private static final Map<String, Saved> SAVED = new HashMap<>();
    /** The login the phone is on now (saved or put back), refreshed by {@link #saveAgain} at the end of a test. */
    private static String current;

    private MobileSession() {}

    /** Keeps the logged-in app's prefs file for {@code key}. Call on home, right after a UI login. */
    public static void save(AndroidDriver driver, String key) {
        if (!enabled()) {
            return;
        }
        String app = driver.getCurrentPackage();
        String file = prefsFile();
        // base64 so non-Latin names and XML quotes come back byte for byte
        String base64 = String.valueOf(driver.executeScript("mobile: shell", Map.of("command", "run-as",
                "args", List.of(app, "sh", "-c", "'base64 -w0 " + file + "'")))).trim();
        byte[] prefs = Base64.getDecoder().decode(base64);
        SAVED.put(key, new Saved(app, file, prefs));
        current = key;
        System.out.println("Saved the phone login of " + key + " (" + prefs.length + " bytes)");
    }

    /** A new test starts: only a test that logs in through loginOnPhone has its login saved again at its end. */
    public static void newTest() {
        current = null;
    }

    /**
     * Saves the phone's login again at the end of a test. An app that refreshes its tokens while it runs makes
     * the old refresh token stop working, so the copy taken right after the login is stale by the next block:
     * home opens, but its lists stay on their loading cards.
     */
    public static void saveAgain(AndroidDriver driver) {
        if (current == null) {
            return;
        }
        try {
            save(driver, current);
        } catch (Exception e) {
            System.out.println("Could not save the phone login again: " + e.getMessage());
        }
    }

    /** True when {@link #restore} has a login to put back for {@code key}. */
    public static boolean hasSaved(String key) {
        return SAVED.containsKey(key);
    }

    /**
     * Stops the app, writes the saved prefs back and starts it again. Returns false when nothing was saved
     * for {@code key} - the app is left as it was. The app is usually not running here: BaseTests starts the
     * session with autoLaunch off when it sees a saved login, so the block shows one app start, not two.
     */
    public static boolean restore(AndroidDriver driver, String key) {
        Saved saved = SAVED.get(key);
        if (saved == null) {
            return false;
        }
        // stopped first: a running app writes its own prefs back over ours when it closes
        shell(driver, "am", "force-stop", saved.appPackage());
        String base64 = Base64.getEncoder().encodeToString(saved.prefs());
        // mkdir: the session cleared the app's data (pm clear) and, with autoLaunch off, nothing ran the app
        // since, so the file's folder does not exist yet ("can't create ...: No such file or directory")
        String folder = saved.file().contains("/") ? saved.file().substring(0, saved.file().lastIndexOf('/')) : ".";
        shell(driver, "run-as", saved.appPackage(), "sh", "-c",
                "'mkdir -p " + folder + " && echo " + base64 + " | base64 -d > " + saved.file() + "'");
        launch(driver, saved.appPackage());
        current = key;
        System.out.println("Logged in on the phone from the saved session of " + key);
        return true;
    }

    /**
     * The saved login did not bring the app to home (token expired, server logged it out): drops it and
     * clears the app's data, so the UI login starts from the app's first screen.
     */
    public static void forget(AndroidDriver driver, String key) {
        Saved saved = SAVED.remove(key);
        current = null;
        String app = saved != null ? saved.appPackage() : driver.getCurrentPackage();
        shell(driver, "pm", "clear", app);
        launch(driver, app);
        System.out.println("The saved phone login of " + key + " did not open home - logging in on the UI");
    }

    /** Starts the app when it is not on screen (the session left it closed for a restore that did not happen). */
    public static void launchIfClosed(AndroidDriver driver, String app) {
        if (driver.queryAppState(app) != io.appium.java_client.appmanagement.ApplicationState.RUNNING_IN_FOREGROUND) {
            launch(driver, app);
        }
    }

    // monkey, not activateApp: activateApp left the app closed for ~70 s (MethodHandlesMobile.restartApp)
    private static void launch(AndroidDriver driver, String app) {
        shell(driver, "monkey", "-p", app, "-c", "android.intent.category.LAUNCHER", "1");
    }

    private static void shell(AndroidDriver driver, String command, String... args) {
        driver.executeScript("mobile: shell", Map.of("command", command, "args", List.of(args)));
    }
}
