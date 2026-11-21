package dev.apipilot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real HTTP calls against the app started by {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}.
 */
public final class HttpTestSupport {

  public static final JsonMapper JSON = JsonMapper.builder().build();

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private HttpTestSupport() {}

  public static HttpResponse<String> get(int port, String path)
      throws IOException, InterruptedException {
    return send(port, "GET", path, null);
  }

  public static HttpResponse<String> send(int port, String method, String path, Object body)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build();
    return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  public static JsonNode json(HttpResponse<String> response) {
    return JSON.readTree(response.body());
  }
}
