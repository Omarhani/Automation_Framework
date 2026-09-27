package data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape of {@code data/testData.json} (or any file given with {@code -DdataFile} / the suite's
 * {@code dataFile} parameter). One section per test type, plus the environments every section runs against:
 *
 * <pre>
 * ENVIRONMENTS  Test / Stage / ... : the web URL and the API hosts of each env (the suite's server parameter picks one)
 * UI            web: extra page paths, named users, fixed codes
 * API           API: extra headers, named users
 * MOBILE        phone + app: device, app package / activity / APK, named users
 * EMAIL         the mailbox EmailReader polls (IMAP host, sender)
 * ACCOUNTS      admins per account / tenant code, for suites that work on several
 * </pre>
 */
public class DataModel {

    public Map<String, Environment> ENVIRONMENTS = new LinkedHashMap<>();

    public UI UI = new UI();
    public API API = new API();
    public MOBILE MOBILE = new MOBILE();
    public EMAIL EMAIL = new EMAIL();

    // admins of the account / tenant codes a suite can work on (optional)
    public List<Account> ACCOUNTS = new java.util.ArrayList<>();

    /** The environment the running suite is on ({@link Env#current()}); an empty one when the file has none. */
    public Environment env() {
        Environment env = ENVIRONMENTS == null ? null : ENVIRONMENTS.get(Env.current());
        if (env == null && ENVIRONMENTS != null) {
            // "Test" / "test" / "TEST" are the same env
            env = ENVIRONMENTS.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(Env.current()))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
        }
        return env == null ? new Environment() : env;
    }

    /** The admin row of {@code code} on the running env (a row with an empty ENV fits every env), or null. */
    public Account account(String code) {
        if (ACCOUNTS == null || code == null) {
            return null;
        }
        return ACCOUNTS.stream()
                .filter(a -> code.equalsIgnoreCase(a.CODE))
                .filter(a -> a.ENV == null || a.ENV.isBlank() || a.ENV.equalsIgnoreCase(Env.current()))
                .findFirst().orElse(null);
    }
}
