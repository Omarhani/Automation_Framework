package apis.health;

import apis.services.SpecFactory;
import base.BaseApi;
import io.restassured.response.Response;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;
import static utils.MethodHandlesWeb.myAssertTrue;

/**
 * API smoke check - works for any API: the env's API_BASE_URI (data file ENVIRONMENTS) answers without a server
 * error. The path comes from the suite ({@code <parameter name="healthPath" value="health"/>}), empty = the root.
 * Put your API's tests beside it: a client per service in apis/services (see SpecFactory), tests per area.
 */
public class HealthApiTests extends BaseApi {

    @Parameters("healthPath")
    @Test
    public void verifyThatTheApiAnswers(@Optional("") String healthPath) {
        Response response = given(SpecFactory.getSpec(healthPath.startsWith("${") ? "" : healthPath)).get();

        myAssertTrue(response.statusCode() < 500);
    }
}
