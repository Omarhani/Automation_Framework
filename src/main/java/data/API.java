package data;

import java.util.LinkedHashMap;
import java.util.Map;

/** testData.json API: the API tests' data. The hosts are per env, in ENVIRONMENTS. */
public class API {

    /** Headers sent on every call (client key, app version ...). Secrets go in env vars, not here. */
    public Map<String, String> HEADERS = new LinkedHashMap<>();

    /** Named API users, e.g. "ADMIN". */
    public Map<String, User> USERS = new LinkedHashMap<>();

    public User user(String name) {
        return Lookup.require(USERS, name, "API.USERS");
    }

    /** The API of the running env. */
    public String baseUri() {
        return reader.ReadDataFromJson.dataModel().env().API_BASE_URI;
    }

    /** The mobile app's API of the running env. */
    public String mobileBaseUri() {
        return reader.ReadDataFromJson.dataModel().env().mobileApiBaseUri();
    }
}
