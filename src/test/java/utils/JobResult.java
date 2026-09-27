package utils;

import com.google.gson.Gson;

import java.util.Map;

/**
 * The result of a "data on demand" job (make a user / an order and leave it in place) for the Run Job page
 * (jenkins/jobForm.html): one console line {@code @@result {json}}. The page merges every such line into the
 * run's result card, and the value of its {@code id} key is what the page's Undo button passes to the undo job
 * as RESULT_ID.
 *
 * <pre>
 * JobResult.print(Map.of("id", createdId, "user", userName, "env", data.Env.current()));
 * </pre>
 */
public final class JobResult {

    private JobResult() {
    }

    public static void print(Map<String, ?> result) {
        System.out.println("@@result " + new Gson().toJson(result));
    }
}
