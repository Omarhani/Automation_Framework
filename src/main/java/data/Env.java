package data;

/**
 * The environment the running suite is on - set once per suite from its {@code server} parameter
 * ({@code <parameter name="server" value="${serverType}"/>}, i.e. {@code -DserverType=Stage}) by BaseTests / BaseApi.
 * Its name is a key of testData.json ENVIRONMENTS. An unresolved {@code ${serverType}} (no -DserverType) means
 * {@link #DEFAULT}.
 */
public final class Env {

    public static final String DEFAULT = "Test";

    private static volatile String current = DEFAULT;

    private Env() {
    }

    public static String current() {
        return current;
    }

    /** Sets the env from a suite parameter; empty or {@code ${...}} falls back to {@link #DEFAULT}. */
    public static String set(String server) {
        current = server == null || server.isBlank() || server.startsWith("${") ? DEFAULT : server.trim();
        return current;
    }

    public static boolean is(String name) {
        return current.equalsIgnoreCase(name);
    }

    /** The report folder of this env: Test -> testEnv, Stage -> stageEnv, Prod -> prodEnv. */
    public static String reportFolder() {
        return Character.toLowerCase(current.charAt(0)) + current.substring(1) + "Env";
    }
}
