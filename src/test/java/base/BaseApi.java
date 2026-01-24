package base;

import io.restassured.RestAssured;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

public class BaseApi {



    @BeforeClass
    public void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterClass
    public void cleanUp() {
        RestAssured.reset();
    }
}
