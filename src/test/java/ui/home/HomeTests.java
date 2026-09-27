package ui.home;

import base.BaseTests;
import org.testng.annotations.Test;

import static utils.MethodHandlesWeb.myAssertFalse;

/**
 * Web smoke check - works for any app: the env's WEB_URL (data file ENVIRONMENTS) opens and has a title.
 * BaseTests already opened WEB_URL before this test. Put your app's web tests beside it (ui/login, ui/orders ...).
 */
public class HomeTests extends BaseTests {

    @Test(groups = "Web")
    public void verifyThatTheHomePageOpens() {
        myAssertFalse(driver.getTitle() == null || driver.getTitle().isBlank());
    }
}
