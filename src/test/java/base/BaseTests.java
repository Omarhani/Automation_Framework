package base;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;
import pages.HomePage;

import java.io.FileNotFoundException;

import static reader.ReadDataFromJson.dataModel;

public class BaseTests {

    protected WebDriver driver;
    ChromeOptions chromeOptions;
    EdgeOptions edgeOptions;
    protected HomePage homePage;

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
    public void setUp(String browser) {
        setUpBrowser(browser);
        driver.manage().window().maximize();
        homePage = new HomePage(driver);
    }


    @AfterClass
    public void tearDown() {
        driver.quit();
    }

    @BeforeMethod
    public void goHome() throws FileNotFoundException {

        driver.get(dataModel().URL);
    }
}
