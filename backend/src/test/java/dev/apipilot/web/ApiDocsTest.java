package dev.apipilot.web;

import static dev.apipilot.HttpTestSupport.get;
import static dev.apipilot.HttpTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import tools.jackson.databind.JsonNode;

/** The frontend's TypeScript types are generated from this document, so its shape matters. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ApiDocsTest {

  @Value("${local.server.port}")
  int port;

  @Test
  void nonNullableRecordFieldsAreRequiredAndNullableOnesAreNot() throws Exception {
    JsonNode summary = schemas().get("ProjectSummary");

    assertThat(required(summary))
        .containsExactlyInAnyOrder("id", "name", "operationCount", "createdAt");
  }

  @Test
  void requestWithOnlyOptionalFieldsHasNoRequiredList() throws Exception {
    assertThat(schemas().get("CreateProjectRequest").has("required")).isFalse();
  }

  @Test
  void jsonSchemaFieldsAreDocumentedAsFreeFormObjects() throws Exception {
    JsonNode schemaField = schemas().at("/Body/properties/schema");

    assertThat(schemaField.get("type").asString()).isEqualTo("object");
    assertThat(schemaField.has("properties")).isFalse();
  }

  private JsonNode schemas() throws Exception {
    return json(get(port, "/api/openapi")).at("/components/schemas");
  }

  private static List<String> required(JsonNode schema) {
    return StreamSupport.stream(schema.get("required").spliterator(), false)
        .map(JsonNode::asString)
        .toList();
  }
}
