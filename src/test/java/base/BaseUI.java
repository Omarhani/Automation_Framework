package base;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.testng.ITestResult;
import org.testng.annotations.*;
import pages.HomePage;
import utils.ScreenRecorderUtil;
import utils.UtilsTests;

import java.lang.reflect.Method;

import static reader.ReadDataFromJson.dataModel;

public class BaseUI {

    protected WebDriver driver;
    ChromeOptions chromeOptions;
    EdgeOptions edgeOptions;
    protected HomePage homePage;
    UtilsTests utilsTests;

    @Parameters("browser")
    public void setUpBrowser(String browser) {

        if (browser.equalsIgnoreCase("Chrome")) {
            driver = new ChromeDriver();
        } else if (browser.equalsIgnoreCase("edge")) {
            driver = new EdgeDriver();
        } else if (browser.equalsIgnoreCase("headlessChrome")) {
            chromeOptions = new ChromeOptions();
            chromeOptions.addArguments("--headless");
            driver = new ChromeDriver(chromeOptions);
        } else if (browser.equalsIgnoreCase("headlessedge")) {
            edgeOptions = new EdgeOptions();
            edgeOptions.addArguments("--headless");
            driver = new EdgeDriver(edgeOptions);
        }
    }


    @Parameters("browser")
    @BeforeClass
    public void setUp(@Optional("chrome") String browser) {
        setUpBrowser(browser);
        driver.manage().window().maximize();
        homePage = new HomePage(driver);
    }
    @AfterMethod
    public void afterMethod(Method method, ITestResult result) throws Exception {
        utilsTests = new UtilsTests(driver);
        utilsTests.takeScreenShot(method);
        ScreenRecorderUtil.stopRecord();
        utilsTests.endsOfSteps();
        utilsTests.setStatus(result);
        utilsTests.addAttachment(method);
    }

    @AfterClass
    public void tearDown() {
        driver.quit();
    }

    @BeforeMethod
    public void goHome(Method method) throws Exception {
        utilsTests = new UtilsTests(driver);
        ScreenRecorderUtil.startRecord(method.getName());
        driver.get(dataModel().UI.BASE_URL);
        utilsTests.createTestCaseInReport(method);

    }

    @BeforeSuite
    public void beforeSuite(){
        utilsTests = new UtilsTests(driver);
        utilsTests.createReport();
    }
    @AfterSuite
    public void afterSuite(){
        utilsTests = new UtilsTests(driver);
        utilsTests.flushReport();
    }
}
