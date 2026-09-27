package data;

import java.util.LinkedHashMap;
import java.util.Map;

/** testData.json MOBILE: the phone, the app under test and the mobile users. */
public class MOBILE {

    /** Shown in the report entry; Appium does not need it to match. */
    public String DEVICE_NAME = "";

    /** The phone on wireless adb (ip:port), used when no USB cable is in. Empty = USB only. */
    public String WIFI_UDID = "";

    public String APP_PACKAGE = "";

    /** The launcher activity, full name (com.example.app.MainActivity). */
    public String APP_ACTIVITY = "";

    /**
     * The APK installed when the app is not on the phone yet. Relative paths start at the user's home folder
     * (e.g. "Downloads/app-debug.apk"); absolute paths are taken as they are.
     */
    public String APK_PATH = "";

    /**
     * The app's login file inside its data folder, for the saved phone login ({@code utils.MobileSession}), e.g.
     * "shared_prefs/FlutterSharedPreferences.xml". Needs a debuggable build. Empty = always log in on the UI.
     */
    public String SESSION_FILE = "";

    /** Named mobile users. */
    public Map<String, User> USERS = new LinkedHashMap<>();

    /** Fixed codes the app accepts on its Test env. */
    public Map<String, String> VERIFICATION_CODES = new LinkedHashMap<>();

    public User user(String name) {
        return Lookup.require(USERS, name, "MOBILE.USERS");
    }

    public String apkPath() {
        if (APK_PATH == null || APK_PATH.isBlank()) {
            return "";
        }
        java.io.File apk = new java.io.File(APK_PATH);
        return apk.isAbsolute() ? apk.getPath() : new java.io.File(System.getProperty("user.home"), APK_PATH).getPath();
    }
}
