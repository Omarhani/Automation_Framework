package mob.home;

import base.BaseTests;
import org.testng.annotations.Test;

import static utils.MethodHandlesWeb.myAssertEquals;

/**
 * Mobile smoke check - works for any app: the app of MOBILE.APP_PACKAGE (data file) starts on the phone.
 * Put your app's phone tests beside it (mob/login, mob/orders ...).
 */
public class HomeTests extends BaseTests {

    @Test(groups = "Mob")
    public void verifyThatTheAppOpens() {
        myAssertEquals("App in front · package", androidDriver.getCurrentPackage(), mobile().APP_PACKAGE);
    }
}
