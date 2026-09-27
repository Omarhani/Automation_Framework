package data;

import java.util.LinkedHashMap;
import java.util.Map;

/** testData.json UI: the web tests' data. */
public class UI {

    /** Page paths added to the env's WEB_URL, e.g. "CART": "/cart.html". */
    public Map<String, String> URLs = new LinkedHashMap<>();

    /** Named web users, e.g. "MAIN_USER". */
    public Map<String, User> USERS = new LinkedHashMap<>();

    /** Fixed codes the app accepts on its Test env (OTP, verification codes ...). */
    public Map<String, String> VERIFICATION_CODES = new LinkedHashMap<>();

    public User user(String name) {
        return Lookup.require(USERS, name, "UI.USERS");
    }

    /** The env's WEB_URL + the path named {@code name} in UI.URLs. */
    public String url(String name) {
        return reader.ReadDataFromJson.dataModel().env().WEB_URL + Lookup.require(URLs, name, "UI.URLs");
    }
}
