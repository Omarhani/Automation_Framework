package apis.services;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.io.FileNotFoundException;

import static reader.ReadDataFromJson.dataModel;

public class SpecFactory {

    public static RequestSpecification getSpec(String resource) throws FileNotFoundException {
        return new RequestSpecBuilder()
                .setBaseUri(dataModel().API.BASE_URI)
                .setBasePath(resource)
                .setContentType(ContentType.JSON)
                .build();
    }
}
