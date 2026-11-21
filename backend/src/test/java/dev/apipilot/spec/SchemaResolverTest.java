package dev.apipilot.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SchemaResolverTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final ApiModel model =
      new SpecImporter().importSpec(SpecImporterTest.fixture("edge-cases.yaml"));
  private final SchemaResolver resolver = new SchemaResolver(model.schemas());

  @Test
  void followsAReferenceToItsSchema() {
    JsonNode resolved = resolver.resolve(ref("Named"));

    assertThat(resolver.properties(resolved)).containsOnlyKeys("name");
    assertThat(resolver.required(resolved)).containsExactly("name");
  }

  @Test
  void mergesAllOfMembersIntoOneObject() {
    JsonNode circle = resolver.resolve(ref("Circle"));

    assertThat(resolver.properties(circle)).containsOnlyKeys("name", "radius");
    assertThat(resolver.required(circle)).containsExactlyInAnyOrder("name", "radius");
  }

  @Test
  void usesTheFirstVariantOfAOneOf() {
    assertThat(resolver.properties(resolver.resolve(ref("Shape"))))
        .containsOnlyKeys("name", "radius");
  }

  @Test
  void resolvesASelfReferencingSchemaOneLevelDeep() {
    JsonNode node = resolver.resolve(ref("Node"));

    assertThat(resolver.properties(node)).containsOnlyKeys("label", "parent", "children");
    assertThat(resolver.properties(node).get("parent").has("$ref")).isTrue();
  }

  @Test
  void unknownReferenceResolvesToASchemaWithoutProperties() {
    assertThat(resolver.properties(resolver.resolve(ref("Missing")))).isEmpty();
  }

  @Test
  void nullSchemaHasNoProperties() {
    assertThat(resolver.properties(resolver.resolve(null))).isEmpty();
  }

  private static JsonNode ref(String name) {
    return JSON.readTree("{\"$ref\":\"#/components/schemas/" + name + "\"}");
  }
}
