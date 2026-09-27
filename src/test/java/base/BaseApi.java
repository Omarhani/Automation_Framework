package base;

import io.restassured.RestAssured;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.*;
import utils.ReportListener;
import utils.UtilsTests;

import java.lang.reflect.Method;

/**
 * Base for API tests - the same report lifecycle as BaseTests gives the UI and mobile tests:
 * one numbered report entry per test (xml test name + parameter table), every API call logged as a step by
 * {@link utils.ApiReport}, then "Ends of Steps" and pass / fail. In a hybrid suite the API blocks land in the same
 * report as the web and phone blocks; an API-only suite (e.g. api/apiBooking) gets its own.
 */
@Listeners(ReportListener.class)
public class BaseApi {

    @Parameters({"server", "dataFile"})
    @BeforeSuite(alwaysRun = true)
    public void beforeSuiteApi(@Optional("Test") String server, @Optional("") String dataFile, ITestContext context) {
        reader.ReadDataFromJson.useSuiteDataFile(dataFile);
        // report/testEnv|stageEnv/<suite xml name>/<suite xml name>.html - kept if the web / mobile hooks made it.
        // Also sets the env (data.Env): API clients call that env's hosts from ENVIRONMENTS in the data file
        UtilsTests.setReportFolder(context.getSuite(), server);
        new UtilsTests(null).createReport();
    }

    @BeforeClass(alwaysRun = true)
    public void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeMethod(alwaysRun = true)
    public void startApiTest(Method method, ITestContext context, Object[] parameters) {
        new UtilsTests(null).createTestCaseInReport(method, context, parameters, UtilsTests.API, null);
    }

    @AfterMethod(alwaysRun = true)
    public void endApiTest(ITestResult result) {
        // failure (message + stack trace) or skip reason, Ends of Steps, pass / fail / skip
        new UtilsTests(null).finishTestCase(result);
    }

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        RestAssured.reset();
    }

    @AfterSuite(alwaysRun = true)
    public void afterSuiteApi() {
        new UtilsTests(null).flushReport();
    }
}
