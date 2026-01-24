package login;

import base.BaseTests;
import org.testng.annotations.Test;

public class LoginTests extends BaseTests {

    @Test
    public void t1(){
        homePage.clickOnDynamicLink();
    }

    @Test
    public void t2(){
        System.out.println("Hello 2");
    }

    @Test
    public void t3(){
        System.out.println("Hello 3");

    }

}
