package apis.services;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import utils.ApiReport;

import static reader.ReadDataFromJson.dataModel;

/**
 * The request spec every API client starts from: the running env's API host (ENVIRONMENTS.&lt;env&gt;.API_BASE_URI),
 * JSON in and out, the data file's API.HEADERS on every call, and {@link ApiReport#FILTER} - so every call lands in
 * the running test's report entry and in the Live Runner feed, with its secrets masked.
 */
public final class SpecFactory {

    private SpecFactory() {
    }

    /** The web API of the running env, under {@code resource} (e.g. "booking"; "" for the host itself). */
    public static RequestSpecification getSpec(String resource) {
        return spec(dataModel().API.baseUri(), resource);
    }

    /** The mobile app's API of the running env (MOBILE_API_BASE_URI, or API_BASE_URI when that is empty). */
    public static RequestSpecification getMobileSpec(String resource) {
        return spec(dataModel().API.mobileBaseUri(), resource);
    }

    private static RequestSpecification spec(String baseUri, String resource) {
        if (baseUri == null || baseUri.isBlank()) {
            throw new IllegalStateException("No API host for env \"" + data.Env.current() + "\": set ENVIRONMENTS."
                    + data.Env.current() + ".API_BASE_URI in " + reader.ReadDataFromJson.dataFile());
        }
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setBasePath(resource == null ? "" : resource)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(ApiReport.FILTER);
        dataModel().API.HEADERS.forEach(builder::addHeader);
        return builder.build();
    }
}
