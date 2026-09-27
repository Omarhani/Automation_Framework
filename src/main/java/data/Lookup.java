package data;

import java.util.Map;

/** Named entries of testData.json, with a message that says which key is missing and where. */
final class Lookup {

    private Lookup() {
    }

    static <T> T require(Map<String, T> map, String name, String where) {
        T value = map == null ? null : map.get(name);
        if (value == null) {
            throw new IllegalArgumentException("No \"" + name + "\" in " + where + " of " + reader.ReadDataFromJson.dataFile()
                    + (map == null || map.isEmpty() ? " (it is empty)" : " - it has " + map.keySet()));
        }
        return value;
    }
}
