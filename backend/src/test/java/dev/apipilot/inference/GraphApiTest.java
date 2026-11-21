package dev.apipilot.inference;

import static dev.apipilot.HttpTestSupport.get;
import static dev.apipilot.HttpTestSupport.json;
import static dev.apipilot.HttpTestSupport.send;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GraphApiTest {

  @Value("${local.server.port}")
  int port;

  @Test
  void returnsTheDependencyGraphOfAProject() throws Exception {
    JsonNode graph = json(get(port, "/api/projects/" + importSample() + "/graph"));

    assertThat(graph.get("resources").size()).isEqualTo(5);
    assertThat(graph.at("/levels/0").size()).isEqualTo(2);
    assertThat(graph.at("/auth/0/tokenOperationId").asString()).isEqualTo("token");
    assertThat(graph.at("/resources/0/operations").has("READ")).isTrue();
  }

  @Test
  void savedOverridesChangeTheGraphAndAreReturned() throws Exception {
    String base = "/api/projects/" + importSample() + "/graph";
    Map<String, Object> overrides =
        Map.of(
            "removed",
            List.of(Map.of("from", "/documents", "to", "/categories", "input", "body:/categoryId")),
            "added",
            List.of());

    assertThat(send(port, "PUT", base + "/overrides", overrides).statusCode()).isEqualTo(200);

    JsonNode saved = json(get(port, base + "/overrides"));
    assertThat(saved.at("/removed/0/to").asString()).isEqualTo("/categories");
    JsonNode graph = json(get(port, base));
    assertThat(graph.get("dependencies").size()).isEqualTo(3);
  }

  @Test
  void projectWithoutSavedOverridesHasEmptyOverrides() throws Exception {
    JsonNode overrides = json(get(port, "/api/projects/" + importSample() + "/graph/overrides"));

    assertThat(overrides.get("removed").isEmpty()).isTrue();
    assertThat(overrides.get("added").isEmpty()).isTrue();
  }

  @Test
  void unknownProjectHasNoGraph() throws Exception {
    assertThat(get(port, "/api/projects/" + UUID.randomUUID() + "/graph").statusCode())
        .isEqualTo(404);
  }

  private String importSample() throws Exception {
    try (var in = GraphApiTest.class.getResourceAsStream("/specs/sample-api.json")) {
      String spec = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return json(send(port, "POST", "/api/projects", Map.of("spec", spec))).get("id").asString();
    }
  }
}
