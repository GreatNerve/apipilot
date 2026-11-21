package dev.apipilot.data;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apipilot.spec.ApiModel;
import dev.apipilot.spec.Operation;
import dev.apipilot.spec.SpecImporter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DataGeneratorTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final DataGenerator generator = new DataGenerator(Map.of());

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sample-api.json",
        "edge-cases.yaml",
        "tricky-dependencies.yaml",
        "public/petstore3.json",
        "public/museum.yaml",
        "public/realworld.yml"
      })
  void everyGeneratedRequestBodyIsValidAgainstItsSchema(String fixture) {
    ApiModel model = model(fixture);
    DataGenerator data = new DataGenerator(model.schemas());
    SchemaValidator validator = new SchemaValidator(model);

    for (Operation operation : model.operations()) {
      if (operation.requestBody() == null || operation.requestBody().schema() == null) {
        continue;
      }
      JsonNode schema = operation.requestBody().schema();
      for (int i = 0; i < 20; i++) {
        JsonNode body = data.generate(schema);
        assertThat(validator.validate(schema, body))
            .as("%s %s body %s", fixture, operation.id(), body)
            .isEmpty();
      }
    }
  }

  @Test
  void stringFormatsProduceValuesOfThatFormat() {
    JsonNode value =
        generate(
            """
            {"type":"object","properties":{
              "a":{"type":"string","format":"email"},
              "b":{"type":"string","format":"uuid"},
              "c":{"type":"string","format":"date"},
              "d":{"type":"string","format":"date-time"},
              "e":{"type":"string","format":"uri"}}}
            """);

    assertThat(value.get("a").asString()).matches("[^@\\s]+@[^@\\s]+\\.[a-z]+");
    assertThat(UUID.fromString(value.get("b").asString())).isNotNull();
    assertThat(value.get("c").asString()).matches("\\d{4}-\\d{2}-\\d{2}");
    assertThat(value.get("d").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    assertThat(value.get("e").asString()).startsWith("https://");
  }

  @Test
  void fieldNamesGuideValuesWhenTheSchemaSaysOnlyString() {
    JsonNode value =
        generate(
            """
            {"type":"object","properties":{
              "contactEmail":{"type":"string"},
              "phone_number":{"type":"string"},
              "password":{"type":"string"},
              "website":{"type":"string"}}}
            """);

    assertThat(value.get("contactEmail").asString()).contains("@");
    assertThat(value.get("phone_number").asString()).matches("\\+\\d{11}");
    assertThat(value.get("password").asString())
        .matches(".*[a-z].*")
        .matches(".*[A-Z].*")
        .matches(".*\\d.*")
        .matches(".*[^A-Za-z0-9].*")
        .hasSizeGreaterThanOrEqualTo(12);
    assertThat(value.get("website").asString()).startsWith("https://");
  }

  @Test
  void patternWinsOverEverythingElse() {
    JsonNode value = generate("{\"type\":\"string\",\"pattern\":\"^[A-Z]{2}[0-9]{8}$\"}");

    assertThat(value.asString()).matches("[A-Z]{2}[0-9]{8}");
  }

  @Test
  void enumValueIsOneOfTheListedValues() {
    JsonNode value = generate("{\"type\":\"string\",\"enum\":[\"VIEWER\",\"EDITOR\"]}");

    assertThat(value.asString()).isIn("VIEWER", "EDITOR");
  }

  @Test
  void stringLengthLimitsAreRespected() {
    for (int i = 0; i < 50; i++) {
      assertThat(generate("{\"type\":\"string\",\"maxLength\":5}").asString())
          .hasSizeLessThanOrEqualTo(5);
      assertThat(generate("{\"type\":\"string\",\"minLength\":40}").asString())
          .hasSizeGreaterThanOrEqualTo(40);
    }
  }

  @Test
  void numbersStayInsideTheirBounds() {
    for (int i = 0; i < 50; i++) {
      assertThat(generate("{\"type\":\"integer\",\"minimum\":5,\"maximum\":7}").asInt())
          .isBetween(5, 7);
      assertThat(generate("{\"type\":\"number\",\"exclusiveMinimum\":0,\"maximum\":1}").asDouble())
          .isGreaterThan(0)
          .isLessThanOrEqualTo(1);
      assertThat(generate("{\"type\":\"integer\",\"multipleOf\":5,\"minimum\":1}").asInt() % 5)
          .isZero();
    }
  }

  @Test
  void freeTextIsDifferentOnEveryCallSoUniqueConstraintsHold() {
    Set<String> names = new HashSet<>();
    Set<String> emails = new HashSet<>();
    for (int i = 0; i < 500; i++) {
      JsonNode value =
          generate(
              """
              {"type":"object","properties":{
                "name":{"type":"string","maxLength":100},
                "email":{"type":"string","format":"email"}}}
              """);
      names.add(value.get("name").asString());
      emails.add(value.get("email").asString());
    }

    assertThat(names).hasSize(500);
    assertThat(emails).hasSize(500);
  }

  @Test
  void readOnlyPropertiesAreLeftOutOfRequests() {
    JsonNode value =
        generate(
            """
            {"type":"object","properties":{
              "id":{"type":"string","readOnly":true},
              "name":{"type":"string"}}}
            """);

    assertThat(value.has("id")).isFalse();
    assertThat(value.has("name")).isTrue();
  }

  @Test
  void arraysHaveAtLeastTheirMinimumNumberOfItems() {
    JsonNode value =
        generate("{\"type\":\"array\",\"minItems\":3,\"items\":{\"type\":\"integer\"}}");

    assertThat(value.size()).isEqualTo(3);
  }

  @Test
  void selfReferencingSchemaProducesAFiniteValue() {
    ApiModel model = model("edge-cases.yaml");
    JsonNode node =
        new DataGenerator(model.schemas())
            .generate(JSON.readTree("{\"$ref\":\"#/components/schemas/Node\"}"));

    assertThat(node.isObject()).isTrue();
    assertThat(node.toString().length()).isLessThan(5000);
  }

  private JsonNode generate(String schema) {
    return generator.generate(JSON.readTree(schema));
  }

  private static ApiModel model(String fixture) {
    try (InputStream in = DataGeneratorTest.class.getResourceAsStream("/specs/" + fixture)) {
      return new SpecImporter().importSpec(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
