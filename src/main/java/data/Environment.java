package data;

/** One entry of testData.json ENVIRONMENTS: where the web app and its APIs live on that env. */
public class Environment {

    /** The web app's start page, opened before every web test. */
    public String WEB_URL = "";

    /** The API the API tests (and the API steps of web / mobile tests) call. */
    public String API_BASE_URI = "";

    /** The API the mobile app calls, when it is not the same host as {@link #API_BASE_URI}. */
    public String MOBILE_API_BASE_URI = "";

    /** The mobile API, or the web one when the file gives none. */
    public String mobileApiBaseUri() {
        return MOBILE_API_BASE_URI == null || MOBILE_API_BASE_URI.isBlank() ? API_BASE_URI : MOBILE_API_BASE_URI;
    }
}
