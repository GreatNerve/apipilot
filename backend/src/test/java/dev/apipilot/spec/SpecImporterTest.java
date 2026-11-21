package dev.apipilot.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SpecImporterTest {

  private final SpecImporter importer = new SpecImporter();

  @Test
  void importsOpenApi3Operations() {
    ApiModel model = importer.importSpec(fixture("petstore.yaml"));

    assertThat(model.title()).isEqualTo("Swagger Petstore");
    assertThat(model.version()).isEqualTo("1.0.0");
    assertThat(model.servers()).containsExactly("http://petstore.swagger.io/v1");
    assertThat(model.operations())
        .extracting(Operation::id)
        .containsExactly("listPets", "createPets", "showPetById");
  }

  @Test
  void keepsParameterConstraintsAsJsonSchema() {
    Operation listPets = operation(importer.importSpec(fixture("petstore.yaml")), "listPets");

    Parameter limit = listPets.parameters().getFirst();
    assertThat(limit.name()).isEqualTo("limit");
    assertThat(limit.in()).isEqualTo("query");
    assertThat(limit.required()).isFalse();
    assertThat(limit.schema().get("maximum").asInt()).isEqualTo(100);
  }

  @Test
  void keepsSchemaReferencesAndComponentSchemas() {
    ApiModel model = importer.importSpec(fixture("petstore.yaml"));

    Operation createPets = operation(model, "createPets");
    assertThat(createPets.requestBody().contentType()).isEqualTo("application/json");
    assertThat(createPets.requestBody().required()).isTrue();
    assertThat(createPets.requestBody().schema().get("$ref").asString())
        .isEqualTo("#/components/schemas/Pet");
    assertThat(model.schemas().get("Pet").at("/properties/id/format").asString())
        .isEqualTo("int64");
  }

  @Test
  void listsResponsesWithTheirSchemas() {
    Operation listPets = operation(importer.importSpec(fixture("petstore.yaml")), "listPets");

    assertThat(listPets.responses()).extracting(Response::status).containsExactly("200", "default");
    assertThat(listPets.responses().getFirst().schema().get("$ref").asString())
        .isEqualTo("#/components/schemas/Pets");
  }

  @Test
  void convertsSwagger2() {
    ApiModel model = importer.importSpec(fixture("swagger2.json"));

    assertThat(model.servers()).containsExactly("https://notes.example.com/api");
    Operation createNote = operation(model, "createNote");
    assertThat(createNote.requestBody().schema().get("$ref").asString())
        .isEqualTo("#/components/schemas/NoteInput");
    assertThat(createNote.security()).containsExactly("apiKey");
    SecurityScheme apiKey = model.securitySchemes().get("apiKey");
    assertThat(apiKey.type()).isEqualTo("apiKey");
    assertThat(apiKey.in()).isEqualTo("header");
    assertThat(apiKey.parameterName()).isEqualTo("X-Api-Key");
  }

  @Test
  void importsSelfReferencingSchemasWithoutExpandingThem() {
    ApiModel model = importer.importSpec(fixture("edge-cases.yaml"));

    assertThat(model.schemas().get("Node").at("/properties/children/items/$ref").asString())
        .isEqualTo("#/components/schemas/Node");
  }

  @Test
  void keepsSchemaComposition() {
    ApiModel model = importer.importSpec(fixture("edge-cases.yaml"));

    assertThat(model.schemas().get("Shape").get("oneOf").size()).isEqualTo(2);
    assertThat(model.schemas().get("Circle").get("allOf").size()).isEqualTo(2);
  }

  @Test
  void mergesPathLevelParametersIntoEachOperation() {
    ApiModel model = importer.importSpec(fixture("edge-cases.yaml"));

    Operation get = operation(model, "GET /nodes/{nodeId}");
    assertThat(get.parameters()).extracting(Parameter::name).containsExactly("nodeId", "X-Trace");
    assertThat(get.parameters().get(1).required()).isFalse();

    Operation put = operation(model, "replaceNode");
    assertThat(put.parameters()).extracting(Parameter::name).containsExactly("nodeId", "X-Trace");
    assertThat(put.parameters().get(1).required()).isTrue();
  }

  @Test
  void namesOperationsThatHaveNoOperationId() {
    ApiModel model = importer.importSpec(fixture("edge-cases.yaml"));

    assertThat(model.operations())
        .extracting(Operation::id)
        .containsExactly("GET /nodes/{nodeId}", "replaceNode", "getStatus");
  }

  @Test
  void prefersJsonRequestBodyWhenSeveralContentTypesAreOffered() {
    Operation put = operation(importer.importSpec(fixture("edge-cases.yaml")), "replaceNode");

    assertThat(put.requestBody().contentType()).isEqualTo("application/json");
  }

  @Test
  void appliesGlobalSecurityUnlessAnOperationOptsOut() {
    ApiModel model = importer.importSpec(fixture("edge-cases.yaml"));

    assertThat(operation(model, "replaceNode").security()).containsExactly("bearer");
    assertThat(operation(model, "getStatus").security()).isEmpty();
    assertThat(model.securitySchemes().get("bearer").scheme()).isEqualTo("bearer");
  }

  @Test
  void keepsOpenApi31NullableTypeArrays() {
    ApiModel model = importer.importSpec(fixture("edge-cases.yaml"));

    assertThat(model.schemas().get("Node").at("/properties/label/type").isArray()).isTrue();
  }

  @Test
  void rejectsContentThatIsNotAnApiDescription() {
    assertThatThrownBy(() -> importer.importSpec("this is not a spec"))
        .isInstanceOf(SpecImportException.class);
  }

  @Test
  void rejectsSpecWithoutOperations() {
    String empty = "openapi: 3.0.3\ninfo: {title: Empty, version: '1'}\npaths: {}\n";

    assertThatThrownBy(() -> importer.importSpec(empty))
        .isInstanceOf(SpecImportException.class)
        .hasMessageContaining("no operations");
  }

  private static Operation operation(ApiModel model, String id) {
    return model.operations().stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
  }

  static String fixture(String name) {
    try (InputStream in = SpecImporterTest.class.getResourceAsStream("/specs/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
