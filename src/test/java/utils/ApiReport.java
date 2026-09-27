package utils;

import com.aventstack.extentreports.Status;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.regex.Pattern;

import static utils.MethodHandlesWeb.test;

/**
 * Report logging for API calls - the API twin of the UI / mobile step logging.
 * <ul>
 *   <li>As a RestAssured filter ({@link #FILTER}, added to every spec by {@code apis.services.SpecFactory}) it writes
 *   every call into the running test's report entry: method, endpoint, HTTP status and time, the server's
 *   Status / Message when its JSON has them, and the request and response bodies in collapsible blocks.
 *   Passwords, tokens, secrets and verification codes are masked. API calls made inside a UI test (e.g. creating
 *   its data by API) land in that test's entry.</li>
 *   <li>{@link #step(String)} logs a readable business step ("Created booking 12"), like setStep does for pages.</li>
 * </ul>
 * Nothing is written while no report entry is open, so the clients also work outside a suite.
 */
public final class ApiReport implements Filter {

    public static final ApiReport FILTER = new ApiReport();

    // any key that names a secret -> "***": "Password", "CurrentPassword", "NewPassword", "ConfirmPassword",
    // "AccessToken", "RefreshToken", "VerificationCode", "ClientSecret" ... A key that only equalled "password" let the
    // three password fields of a change-password form through.
    private static final Pattern SECRET = Pattern.compile(
            "(\"(?i:[a-z_]*(?:password|token|verificationcode|secret)[a-z_]*)\"\\s*:\\s*)(\"[^\"]*\"|\\d+)");
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final int MAX_BODY = 6000;

    private ApiReport() {
    }

    /** Passwords, tokens and verification codes replaced by ***, as in every API step - also for the browser's calls. */
    static String mask(String json) {
        return SECRET.matcher(json).replaceAll("$1\"***\"");
    }

    /** A readable step in the running test's report entry. */
    public static void step(String text) {
        if (test != null) {
            test.info(escape(text));
        }
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        long started = System.currentTimeMillis();
        Response response = context.next(request, responseSpec);
        if (test == null) {
            return response;
        }
        try {
            long ms = System.currentTimeMillis() - started;
            // the path without the host (and without a leading /api/), so the step reads "GET booking/12"
            String endpoint = request.getURI().replaceFirst("^https?://[^/]+/(api/)?", "");
            String responseBody = response.asString();
            Boolean serverStatus = serverStatus(responseBody);
            boolean failed = response.statusCode() >= 400 || Boolean.FALSE.equals(serverStatus);
            LiveFeed.apiCall(request.getMethod(), endpoint, TestTimeline.maskUrl(request.getURI()), started, response.statusCode(),
                    ms, serverStatus, serverMessage(responseBody), requestBody(request), responseBody);

            StringBuilder html = new StringBuilder()
                    .append("<span style='color:").append(failed ? "#e5534b" : "#3fb950").append(";font-weight:600'>")
                    .append(request.getMethod()).append(' ').append(escape(endpoint))
                    .append(" &rarr; ").append(response.statusCode()).append("</span>")
                    .append(" <small style='opacity:.7'>").append(ms).append(" ms");
            if (serverStatus != null) {
                html.append(" &middot; Status ").append(serverStatus);
                String message = serverMessage(responseBody);
                if (message != null && !message.isEmpty()) {
                    html.append(" &middot; ").append(escape(message));
                }
            }
            html.append("</small>");

            String requestBody = requestBody(request);
            if (!requestBody.isEmpty()) {
                html.append(details("Request", requestBody));
            }
            if (!responseBody.isEmpty()) {
                html.append(details("Response", responseBody));
            }
            test.log(Status.INFO, html.toString());
        } catch (Exception ignored) {
            // logging must never break the call
        }
        return response;
    }

    private static String requestBody(FilterableRequestSpecification request) {
        Object body = request.getBody();
        if (body == null) {
            return "";
        }
        return body instanceof String ? (String) body : new Gson().toJson(body);
    }

    private static Boolean serverStatus(String body) {
        try {
            JsonElement json = JsonParser.parseString(body);
            if (json.isJsonObject() && json.getAsJsonObject().has("Status")
                    && json.getAsJsonObject().get("Status").isJsonPrimitive()) {
                return json.getAsJsonObject().get("Status").getAsBoolean();
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return null;
    }

    private static String serverMessage(String body) {
        try {
            JsonElement message = JsonParser.parseString(body).getAsJsonObject().get("Message");
            return message == null || message.isJsonNull() ? null : message.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Collapsible, pretty printed, masked, capped block. */
    private static String details(String title, String body) {
        String pretty;
        try {
            pretty = PRETTY.toJson(JsonParser.parseString(body));
        } catch (Exception e) {
            pretty = body;
        }
        pretty = SECRET.matcher(pretty).replaceAll("$1\"***\"");
        if (pretty.length() > MAX_BODY) {
            pretty = pretty.substring(0, MAX_BODY) + "\n... (" + (pretty.length() - MAX_BODY) + " more characters)";
        }
        return "<details style='margin-top:4px'><summary style='cursor:pointer'>" + title + "</summary>"
                + "<pre style='white-space:pre-wrap;font-size:12px;max-height:360px;overflow:auto'>"
                + escape(pretty) + "</pre></details>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
