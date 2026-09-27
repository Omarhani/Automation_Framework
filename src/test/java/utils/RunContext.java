package utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What one {@code <test>} block of a suite hands to the next: TestNG makes a new test class instance per block,
 * so a value made in block 1 (the user created by API, a booking id) is lost unless it is kept here.
 *
 * <pre>
 * &lt;test name="Create booking by API"&gt;   -> RunContext.put("bookingId", id)
 * &lt;test name="Check it on the web"&gt;      -> RunContext.get("bookingId")
 * &lt;test name="Clean up - delete it"&gt;     -> RunContext.remove("bookingId")
 * </pre>
 *
 * The "current user" is the one a suite made for itself (e.g. a user signed up by API): the phone's saved login
 * ({@link MobileSession}) is kept per server and user, so BaseTests asks here which user the run is on.
 * Everything is cleared when a new suite starts.
 */
public final class RunContext {

    private static final Map<String, Object> VALUES = new ConcurrentHashMap<>();
    private static volatile String currentUser;

    private RunContext() {
    }

    public static void put(String key, Object value) {
        VALUES.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        T value = (T) VALUES.get(key);
        if (value == null) {
            throw new IllegalStateException("Nothing saved as \"" + key + "\" in this run - the block that makes it"
                    + " must run first (check the suite's <test> order and whether that block failed)");
        }
        return value;
    }

    public static boolean has(String key) {
        return VALUES.containsKey(key);
    }

    public static void remove(String key) {
        VALUES.remove(key);
    }

    /** The user this run made for itself / logs in with, or null. */
    public static String currentUser() {
        return currentUser;
    }

    public static void setCurrentUser(String userName) {
        currentUser = userName;
    }

    public static void clear() {
        VALUES.clear();
        currentUser = null;
    }
}
