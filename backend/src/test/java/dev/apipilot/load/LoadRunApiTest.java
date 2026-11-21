package dev.apipilot.load;

import static dev.apipilot.HttpTestSupport.get;
import static dev.apipilot.HttpTestSupport.json;
import static dev.apipilot.HttpTestSupport.send;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class LoadRunApiTest {

  @Value("${local.server.port}")
  int port;

  @Autowired LoadRunRepository runs;

  @Test
  void storedRunsAreListedNewestFirstWithTheirMetrics() throws Exception {
    UUID projectId = importProject();
    runs.save(run(projectId, "first", Instant.parse("2026-11-17T10:00:00Z")));
    runs.save(run(projectId, "second", Instant.parse("2026-11-17T11:00:00Z")));

    JsonNode list = json(get(port, "/api/projects/" + projectId + "/load-runs"));

    assertThat(list.size()).isEqualTo(2);
    assertThat(list.at("/0/name").asString()).isEqualTo("second");
    assertThat(list.at("/0/totals/latency/p99").asDouble()).isEqualTo(180.5);
    assertThat(list.at("/0/windows/0/requests").asInt()).isEqualTo(50);
    assertThat(list.at("/0/steps/0/path").asString()).isEqualTo("/workspaces");
    assertThat(list.at("/0/status").asString()).isEqualTo("PASSED");
  }

  @Test
  void runsOfOtherProjectsAreNotListed() throws Exception {
    runs.save(run(importProject(), "other", Instant.now()));

    assertThat(json(get(port, "/api/projects/" + importProject() + "/load-runs")).isEmpty())
        .isTrue();
  }

  @Test
  void unknownProjectHasNoRuns() throws Exception {
    assertThat(get(port, "/api/projects/" + UUID.randomUUID() + "/load-runs").statusCode())
        .isEqualTo(404);
  }

  private static LoadRun run(UUID projectId, String name, Instant startedAt) {
    Latency latency = new Latency(40, 55.5, 120, 180.5, 400);
    return new LoadRun(
        UUID.randomUUID(),
        projectId,
        name,
        LoadRun.Profile.LOAD,
        LoadRun.Status.PASSED,
        50,
        60,
        startedAt,
        false,
        new Totals(3000, 12, 512, latency),
        List.of(new Window(0, 50, 0, 38, 110, 170)),
        List.of(
            new StepStats(
                "create", "Create workspace", "POST", "/workspaces", 600, 2, 256, latency)));
  }

  private UUID importProject() throws Exception {
    try (var in = LoadRunApiTest.class.getResourceAsStream("/specs/petstore.yaml")) {
      String spec = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return UUID.fromString(
          json(send(port, "POST", "/api/projects", Map.of("spec", spec))).get("id").asString());
    }
  }
}
