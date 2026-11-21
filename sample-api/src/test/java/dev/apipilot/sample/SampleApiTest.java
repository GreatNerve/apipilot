package dev.apipilot.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SampleApiTest {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Value("${local.server.port}")
  int port;

  String token;

  @BeforeEach
  void logIn() throws Exception {
    String username = unique("user");
    String credentials = "{\"username\":\"%s\",\"password\":\"secret-123\"}".formatted(username);
    assertThat(call("POST", "/auth/register", credentials, null).statusCode()).isEqualTo(201);
    token = json(call("POST", "/auth/token", credentials, null)).get("accessToken").asString();
  }

  @Test
  void rejectsRequestsWithoutToken() throws Exception {
    assertThat(call("GET", "/workspaces", null, null).statusCode()).isEqualTo(401);
  }

  @Test
  void rejectsDuplicateUsername() throws Exception {
    String credentials =
        "{\"username\":\"%s\",\"password\":\"secret-123\"}".formatted(unique("dup"));
    call("POST", "/auth/register", credentials, null);

    assertThat(call("POST", "/auth/register", credentials, null).statusCode()).isEqualTo(409);
  }

  @Test
  void listsSeededReadOnlyCategories() throws Exception {
    JsonNode categories = json(call("GET", "/categories", null, token));

    assertThat(categories.size()).isEqualTo(3);
    String id = categories.get(0).get("id").asString();
    assertThat(call("GET", "/categories/" + id, null, token).statusCode()).isEqualTo(200);
    assertThat(call("DELETE", "/categories/" + id, null, token).statusCode()).isEqualTo(405);
  }

  @Test
  void workspaceGoesThroughFullLifecycle() throws Exception {
    String name = unique("ws");
    HttpResponse<String> created =
        call("POST", "/workspaces", "{\"name\":\"%s\"}".formatted(name), token);
    assertThat(created.statusCode()).isEqualTo(201);
    String path = "/workspaces/" + json(created).get("id").asString();

    assertThat(json(call("GET", path, null, token)).get("name").asString()).isEqualTo(name);

    String renamed = unique("ws");
    assertThat(call("PUT", path, "{\"name\":\"%s\"}".formatted(renamed), token).statusCode())
        .isEqualTo(200);
    assertThat(json(call("GET", path, null, token)).get("name").asString()).isEqualTo(renamed);

    assertThat(call("DELETE", path, null, token).statusCode()).isEqualTo(204);
    assertThat(call("GET", path, null, token).statusCode()).isEqualTo(404);
  }

  @Test
  void rejectsDuplicateWorkspaceName() throws Exception {
    String body = "{\"name\":\"%s\"}".formatted(unique("ws"));
    call("POST", "/workspaces", body, token);

    assertThat(call("POST", "/workspaces", body, token).statusCode()).isEqualTo(409);
  }

  @Test
  void rejectsInvalidPayload() throws Exception {
    assertThat(call("POST", "/workspaces", "{\"name\":\"\"}", token).statusCode()).isEqualTo(400);
  }

  @Test
  void documentNeedsExistingWorkspaceAndCategory() throws Exception {
    String body =
        "{\"workspaceId\":\"%s\",\"categoryId\":\"%s\",\"title\":\"t\"}"
            .formatted(UUID.randomUUID(), UUID.randomUUID());

    assertThat(call("POST", "/documents", body, token).statusCode()).isEqualTo(422);
  }

  @Test
  void documentCanBeEditedAndPublished() throws Exception {
    String workspaceId = createWorkspace();
    String categoryId = json(call("GET", "/categories", null, token)).get(0).get("id").asString();
    String body =
        "{\"workspaceId\":\"%s\",\"categoryId\":\"%s\",\"title\":\"draft\"}"
            .formatted(workspaceId, categoryId);
    JsonNode document = json(call("POST", "/documents", body, token));
    String path = "/documents/" + document.get("id").asString();
    assertThat(document.get("status").asString()).isEqualTo("DRAFT");

    assertThat(json(call("PATCH", path, "{\"title\":\"final\"}", token)).get("title").asString())
        .isEqualTo("final");
    assertThat(json(call("POST", path + "/publish", null, token)).get("status").asString())
        .isEqualTo("PUBLISHED");
    assertThat(json(call("GET", "/documents?workspaceId=" + workspaceId, null, token)).size())
        .isEqualTo(1);
  }

  @Test
  void allowsOnlyOneBillingProfilePerWorkspace() throws Exception {
    String path = "/workspaces/" + createWorkspace() + "/billing-profiles";
    String body = "{\"companyName\":\"Acme\"}";

    assertThat(call("POST", path, body, token).statusCode()).isEqualTo(201);
    assertThat(call("POST", path, body, token).statusCode()).isEqualTo(409);
  }

  @Test
  void rejectsDuplicateMemberEmailInSameWorkspace() throws Exception {
    String path = "/workspaces/" + createWorkspace() + "/members";
    String body = "{\"email\":\"a@example.com\",\"role\":\"EDITOR\"}";

    assertThat(call("POST", path, body, token).statusCode()).isEqualTo(201);
    assertThat(call("POST", path, body, token).statusCode()).isEqualTo(409);
  }

  @Test
  void workspaceWithChildrenCannotBeDeleted() throws Exception {
    String workspacePath = "/workspaces/" + createWorkspace();
    JsonNode member =
        json(
            call(
                "POST",
                workspacePath + "/members",
                "{\"email\":\"a@example.com\",\"role\":\"VIEWER\"}",
                token));

    assertThat(call("DELETE", workspacePath, null, token).statusCode()).isEqualTo(409);

    String memberPath = workspacePath + "/members/" + member.get("id").asString();
    assertThat(call("DELETE", memberPath, null, token).statusCode()).isEqualTo(204);
    assertThat(call("DELETE", workspacePath, null, token).statusCode()).isEqualTo(204);
  }

  @Test
  void publishesOpenApiDescriptionWithBearerAuth() throws Exception {
    JsonNode spec = json(call("GET", "/v3/api-docs", null, null));

    assertThat(spec.get("paths").has("/workspaces/{workspaceId}/members/{memberId}")).isTrue();
    assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asString())
        .isEqualTo("bearer");
    JsonNode tokenSecurity = spec.at("/paths/~1auth~1token/post/security");
    assertThat(tokenSecurity.isArray() && tokenSecurity.isEmpty()).isTrue();
    assertThat(spec.at("/paths/~1workspaces/post/responses").has("201")).isTrue();
  }

  private String createWorkspace() throws Exception {
    String body = "{\"name\":\"%s\"}".formatted(unique("ws"));
    return json(call("POST", "/workspaces", body, token)).get("id").asString();
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }

  private static JsonNode json(HttpResponse<String> response) {
    return JSON.readTree(response.body());
  }

  private HttpResponse<String> call(String method, String path, String body, String bearer)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
