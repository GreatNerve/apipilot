package dev.apipilot.data;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apipilot.spec.ApiModel;
import dev.apipilot.spec.SpecImporter;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SchemaValidatorTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final String SPEC =
      """
      openapi: %s
      info: {title: T, version: "1"}
      paths:
        /things:
          get:
            responses:
              "200": {description: ok}
      components:
        schemas:
          Thing:
            type: object
            required: [id]
            properties:
              id: {type: integer}
              note: %s
              owner: {$ref: "#/components/schemas/Owner"}
          Owner:
            type: object
            required: [email]
            properties:
              email: {type: string}
      """;

  private final JsonNode thing = JSON.readTree("{\"$ref\":\"#/components/schemas/Thing\"}");

  @Test
  void acceptsAValueThatMatches() {
    assertThat(validate("3.0.3", "{type: string}", "{\"id\":1,\"note\":\"x\"}")).isEmpty();
  }

  @Test
  void reportsWhereAValueBreaksTheSchema() {
    List<String> problems = validate("3.0.3", "{type: string}", "{\"id\":\"one\"}");

    assertThat(problems).hasSize(1);
    assertThat(problems.getFirst()).contains("/id").contains("integer");
  }

  @Test
  void followsReferencesToOtherSchemas() {
    assertThat(validate("3.0.3", "{type: string}", "{\"id\":1,\"owner\":{}}"))
        .anyMatch(problem -> problem.contains("email"));
  }

  @Test
  void openApi30NullableAllowsNull() {
    assertThat(validate("3.0.3", "{type: string, nullable: true}", "{\"id\":1,\"note\":null}"))
        .isEmpty();
    assertThat(validate("3.0.3", "{type: string}", "{\"id\":1,\"note\":null}")).isNotEmpty();
  }

  @Test
  void openApi31TypeArrayAllowsNull() {
    assertThat(validate("3.1.0", "{type: [string, \"null\"]}", "{\"id\":1,\"note\":null}"))
        .isEmpty();
  }

  private List<String> validate(String version, String noteSchema, String value) {
    ApiModel model = new SpecImporter().importSpec(SPEC.formatted(version, noteSchema));
    return new SchemaValidator(model).validate(thing, JSON.readTree(value));
  }
}
