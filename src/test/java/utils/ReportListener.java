package utils;

import base.BaseApi;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.Parameters;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import static utils.MethodHandlesWeb.extent;

/**
 * Keeps the report complete when a test does not run its own hooks. BaseApi and BaseTests open an entry
 * in @BeforeMethod and close it in @AfterMethod; TestNG skips both when a set-up step fails (browser or
 * phone did not start, app restart failed), so without this:
 * <ul>
 *   <li>a test skipped before its @BeforeMethod had no entry at all - it now gets the next number,
 *   its type, its test block and why it was skipped;</li>
 *   <li>an entry whose @AfterMethod never ran was left without a status (Extent shows that as passed),
 *   and the next test's API calls were logged into it - it is now closed with the real result when
 *   the next entry opens or its test block ends.</li>
 * </ul>
 * Registered on BaseApi and BaseTests. TestNG makes an instance per <test> block, and a block with both
 * kinds of class gets two, so each result is handled once whichever instance sees it.
 */
public class ReportListener implements ITestListener {

    private static final Set<ITestResult> HANDLED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /** True the first time a result is seen. */
    private static boolean firstTime(ITestResult result) {
        return HANDLED.add(result);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        UtilsTests.noteResult(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        UtilsTests.noteResult(result);
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        onTestFailure(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        if (!firstTime(result) || extent == null) {
            return;
        }
        if (UtilsTests.isOpenFor(result)) {
            // its @AfterMethod closes it if it runs; otherwise the next entry or the block's end does
            UtilsTests.noteResult(result);
            return;
        }
        // skipped before its @BeforeMethod opened an entry
        UtilsTests.closeOpenEntry();
        ITestNGMethod method = result.getMethod();
        Class<?> testClass = method.getConstructorOrMethod().getDeclaringClass();
        UtilsTests.openEntry(testClass, method.getMethodName(),
                method.getConstructorOrMethod().getMethod().getAnnotation(Parameters.class),
                result.getTestContext(), result.getParameters(), typeOf(method), null);
        MethodHandlesWeb.test.skip(com.aventstack.extentreports.markuputils.MarkupHelper.createLabel(
                "Skipped: " + UtilsTests.skipReason(result),
                com.aventstack.extentreports.markuputils.ExtentColor.ORANGE));
        MethodHandlesWeb.test.skip("Test Skipped");
        LiveFeed.testFinished("skip", UtilsTests.skipReason(result));
        MethodHandlesWeb.test = null;
    }

    @Override
    public void onFinish(ITestContext context) {
        UtilsTests.closeOpenEntry();
    }

    private static String typeOf(ITestNGMethod method) {
        if (Arrays.asList(method.getGroups()).contains("Mob")) {
            return UtilsTests.MOBILE;
        }
        if (BaseApi.class.isAssignableFrom(method.getRealClass())) {
            return UtilsTests.API;
        }
        return UtilsTests.WEB;
    }
}
