package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Picks the adb connection to the phone: the USB cable when it is plugged in, wireless adb otherwise.
 *
 * <p>Wireless adb made every Appium command 5-8x slower (same suite, phone and server:
 * 179 commands took 254 s over Wi-Fi, 143 took 31 s over USB). The recording's frame stream shares the
 * same link. So USB comes first, and Wi-Fi is only the fallback for when no cable is in.
 * {@code -Dudid=<serial or ip:port>} still forces one.
 */
public final class PhoneConnection {

    private PhoneConnection() {}

    /**
     * The phone on wireless adb ({@code adb tcpip 5555} once over USB, then {@code adb connect}):
     * testData.json MOBILE.WIFI_UDID, e.g. 192.168.0.10:5555. Empty = USB only.
     */
    private static String wifiUdid() {
        String wifi = reader.ReadDataFromJson.dataModel().MOBILE.WIFI_UDID;
        return wifi == null ? "" : wifi.trim();
    }

    public static String udid() {
        String forced = System.getProperty("udid");
        if (forced != null && !forced.isBlank() && !forced.startsWith("${")) {
            System.out.println("📱 Phone: " + forced + " (-Dudid)");
            return forced.trim();
        }
        List<String> ready = readyDevices();
        for (String serial : ready) {
            if (!isWifi(serial)) {
                System.out.println("📱 Phone: " + serial + " over USB");
                return serial;
            }
        }
        String wifi = wifiUdid();
        if (!wifi.isEmpty()) {
            adb("connect", wifi);
            System.out.println("📱 Phone: no USB cable - " + wifi + " over Wi-Fi (slower)");
            return wifi;
        }
        if (!ready.isEmpty()) {
            System.out.println("📱 Phone: " + ready.get(0) + " over Wi-Fi (slower)");
            return ready.get(0);
        }
        throw new IllegalStateException("No phone: plug one in over USB (adb devices), start an emulator, "
                + "or set MOBILE.WIFI_UDID in the data file / -Dudid");
    }

    /** Wireless adb serials are ip:port; USB serials have no colon. */
    public static boolean isWifi(String udid) {
        return udid.contains(":");
    }

    /** Serials {@code adb devices} lists as ready ("device" - not "offline" or "unauthorized"). */
    private static List<String> readyDevices() {
        List<String> ready = new ArrayList<>();
        for (String line : adb("devices")) {
            String[] cols = line.trim().split("\\s+");
            if (cols.length >= 2 && cols[1].equals("device")) {
                ready.add(cols[0]);
            }
        }
        return ready;
    }

    private static List<String> adb(String... args) {
        List<String> command = new ArrayList<>();
        command.add(adbPath());
        command.addAll(List.of(args));
        List<String> lines = new ArrayList<>();
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = out.readLine()) != null) {
                    lines.add(line);
                }
            }
            process.waitFor(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("⚠️ adb " + String.join(" ", args) + " failed: " + e.getMessage());
        }
        return lines;
    }

    private static String adbPath() {
        for (String home : new String[]{System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT")}) {
            if (home != null) {
                File adb = new File(home, "platform-tools/adb" + (File.separatorChar == '\\' ? ".exe" : ""));
                if (adb.isFile()) {
                    return adb.getPath();
                }
            }
        }
        return "adb";
    }
}
