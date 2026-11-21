package dev.apipilot.project;

import static dev.apipilot.HttpTestSupport.get;
import static dev.apipilot.HttpTestSupport.json;
import static dev.apipilot.HttpTestSupport.send;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ProjectApiTest {

  @Value("${local.server.port}")
  int port;

  @Test
  void importedSpecBecomesAProjectNamedAfterItsTitle() throws Exception {
    HttpResponse<String> created = send(port, "POST", "/api/projects", Map.of("spec", petstore()));

    assertThat(created.statusCode()).isEqualTo(201);
    JsonNode project = json(created);
    assertThat(project.get("name").asString()).isEqualTo("Swagger Petstore");
    assertThat(project.get("operationCount").asInt()).isEqualTo(3);
    assertThat(project.get("id").asString()).isNotBlank();
  }

  @Test
  void projectCanBeGivenItsOwnName() throws Exception {
    JsonNode project =
        json(send(port, "POST", "/api/projects", Map.of("name", "Pets", "spec", petstore())));

    assertThat(project.get("name").asString()).isEqualTo("Pets");
  }

  @Test
  void projectDetailContainsTheParsedOperations() throws Exception {
    String id = createPetstore();

    JsonNode detail = json(get(port, "/api/projects/" + id));

    assertThat(detail.at("/api/operations").size()).isEqualTo(3);
    assertThat(detail.at("/api/operations/0/id").asString()).isEqualTo("listPets");
    assertThat(detail.at("/api/schemas/Pet/properties/name/type").asString()).isEqualTo("string");
  }

  @Test
  void projectsAreListedNewestFirst() throws Exception {
    createPetstore();
    String newest = createPetstore();

    JsonNode list = json(get(port, "/api/projects"));

    assertThat(list.get(0).get("id").asString()).isEqualTo(newest);
    assertThat(list.get(0).has("api")).isFalse();
  }

  @Test
  void deletedProjectIsGone() throws Exception {
    String id = createPetstore();

    assertThat(send(port, "DELETE", "/api/projects/" + id, null).statusCode()).isEqualTo(204);
    assertThat(get(port, "/api/projects/" + id).statusCode()).isEqualTo(404);
  }

  @Test
  void unreadableSpecIsRejectedWithAReason() throws Exception {
    HttpResponse<String> response =
        send(port, "POST", "/api/projects", Map.of("spec", "not a spec"));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(json(response).get("detail").asString()).contains("not an OpenAPI");
  }

  @Test
  void requestWithoutSpecOrUrlIsRejected() throws Exception {
    assertThat(send(port, "POST", "/api/projects", Map.of("name", "x")).statusCode())
        .isEqualTo(400);
  }

  @Test
  void specCanBeFetchedFromAUrl() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] body = petstore().getBytes(StandardCharsets.UTF_8);
    server.createContext(
        "/openapi.yaml",
        exchange -> {
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/openapi.yaml";

      HttpResponse<String> created = send(port, "POST", "/api/projects", Map.of("specUrl", url));

      assertThat(created.statusCode()).isEqualTo(201);
      assertThat(json(created).get("operationCount").asInt()).isEqualTo(3);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void unreachableSpecUrlIsRejectedWithAReason() throws Exception {
    HttpResponse<String> response =
        send(port, "POST", "/api/projects", Map.of("specUrl", "http://127.0.0.1:1/openapi.json"));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(json(response).get("detail").asString()).contains("could not be fetched");
  }

  @Test
  void specBehindBasicAuthIsFetchedWithTheGivenCredentials() throws Exception {
    HttpServer server = passwordProtectedSpecServer();
    try {
      HttpResponse<String> created =
          send(
              port,
              "POST",
              "/api/projects",
              Map.of("specUrl", url(server), "specUsername", "docs", "specPassword", "s3cret"));

      assertThat(created.statusCode()).isEqualTo(201);
      assertThat(created.body()).doesNotContain("s3cret");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void protectedSpecWithoutCredentialsAsksForThem() throws Exception {
    HttpServer server = passwordProtectedSpecServer();
    try {
      HttpResponse<String> response =
          send(port, "POST", "/api/projects", Map.of("specUrl", url(server)));

      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(json(response).get("detail").asString())
          .contains("needs a user name and password");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void wrongCredentialsAreReportedWithoutEchoingThePassword() throws Exception {
    HttpServer server = passwordProtectedSpecServer();
    try {
      HttpResponse<String> response =
          send(
              port,
              "POST",
              "/api/projects",
              Map.of("specUrl", url(server), "specUsername", "docs", "specPassword", "wrong-pw"));

      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(json(response).get("detail").asString()).contains("were rejected");
      assertThat(response.body()).doesNotContain("wrong-pw");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void credentialsAreNotSentOnToAnotherAddressAfterARedirect() throws Exception {
    HttpServer target = passwordProtectedSpecServer();
    HttpServer redirector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    redirector.createContext(
        "/openapi.yaml",
        exchange -> {
          exchange.getResponseHeaders().add("Location", url(target));
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    redirector.start();
    try {
      HttpResponse<String> response =
          send(
              port,
              "POST",
              "/api/projects",
              Map.of("specUrl", url(redirector), "specUsername", "docs", "specPassword", "s3cret"));

      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(json(response).get("detail").asString()).contains("redirects");
    } finally {
      redirector.stop(0);
      target.stop(0);
    }
  }

  private static String url(HttpServer server) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/openapi.yaml";
  }

  /** Serves the Petstore spec only to user "docs" with password "s3cret". */
  private static HttpServer passwordProtectedSpecServer() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] body = petstore().getBytes(StandardCharsets.UTF_8);
    String expected =
        "Basic "
            + java.util.Base64.getEncoder()
                .encodeToString("docs:s3cret".getBytes(StandardCharsets.UTF_8));
    server.createContext(
        "/openapi.yaml",
        exchange -> {
          if (expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          } else {
            exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"docs\"");
            exchange.sendResponseHeaders(401, -1);
          }
          exchange.close();
        });
    server.start();
    return server;
  }

  private String createPetstore() throws Exception {
    return json(send(port, "POST", "/api/projects", Map.of("spec", petstore())))
        .get("id")
        .asString();
  }

  private static String petstore() throws Exception {
    try (var in = ProjectApiTest.class.getResourceAsStream("/specs/petstore.yaml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
