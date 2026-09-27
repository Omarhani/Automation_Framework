package reader;

import com.google.gson.Gson;
import data.DataModel;
import lombok.SneakyThrows;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Reads the test data file: {@code data/testData.json} unless the run says otherwise -
 * {@code -DdataFile=data/other.json} wins, then the suite's {@code <parameter name="dataFile" .../>}
 * (the example suites point at {@code data/examples.json} that way), then the default.
 * Read fresh on every call, so a test that edits the file sees its change.
 */
public class ReadDataFromJson {

    public static final String DEFAULT_FILE = "data/testData.json";

    /** Set from the suite's dataFile parameter by BaseTests / BaseApi; -DdataFile still wins. */
    private static volatile String suiteDataFile;

    public static void useSuiteDataFile(String file) {
        suiteDataFile = file == null || file.isBlank() || file.startsWith("${") ? null : file.trim();
    }

    /** The file {@link #dataModel()} reads. */
    public static String dataFile() {
        String forced = System.getProperty("dataFile");
        if (forced != null && !forced.isBlank() && !forced.startsWith("${")) {
            return forced.trim();
        }
        return suiteDataFile != null ? suiteDataFile : DEFAULT_FILE;
    }

    @SneakyThrows
    public DataModel readJsonFile() {
        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(Paths.get(dataFile())), StandardCharsets.UTF_8)) {
            DataModel dataModel = new Gson().fromJson(reader, DataModel.class);
            return dataModel == null ? new DataModel() : dataModel;
        }
    }

    public static DataModel dataModel() {
        ReadDataFromJson readDataFromJson = new ReadDataFromJson();
        return readDataFromJson.readJsonFile();
    }
}
