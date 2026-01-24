package pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import utils.MethodHandles;

public class HomePage extends MethodHandles {


    WebDriver driver;

    public HomePage(WebDriver driver) {
        super(driver);
    }

    private final By dynamicLink = By.linkText("Dynamic Controls");

    public void clickOnDynamicLink(){
//        driver.findElement(dynamicLink).click();
        click(dynamicLink,5);

    }
}
