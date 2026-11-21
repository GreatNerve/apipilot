package dev.apipilot.spec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Downloads a spec document from a URL the user supplied. */
@Component
public class SpecFetcher {

  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  private final HttpClient followingRedirects = client(HttpClient.Redirect.NORMAL);
  // With credentials attached, a redirect could hand them to a different server.
  private final HttpClient withoutRedirects = client(HttpClient.Redirect.NEVER);

  public String fetch(String url) {
    return fetch(url, null, null);
  }

  /**
   * Fetches the spec, sending the user name and password as HTTP Basic authentication when a user
   * name is given. The credentials are used for this one request and never stored or logged.
   */
  public String fetch(String url, @Nullable String username, @Nullable String password) {
    boolean authenticated = username != null && !username.isBlank();
    HttpResponse<String> response;
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(TIMEOUT)
              .header("Accept", "application/json, application/yaml, */*")
              .GET();
      if (authenticated) {
        String pair = username + ":" + (password == null ? "" : password);
        request.header(
            "Authorization",
            "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8)));
      }
      response =
          (authenticated ? withoutRedirects : followingRedirects)
              .send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IllegalArgumentException | IOException e) {
      throw new SpecImportException("The spec could not be fetched from " + url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SpecImportException("The spec could not be fetched from " + url, e);
    }
    int status = response.statusCode();
    if (status == 200) {
      return response.body();
    }
    if (status == 401 || status == 403) {
      throw new SpecImportException(
          authenticated
              ? "The user name and password were rejected by " + url + " (HTTP " + status + ")"
              : url + " needs a user name and password (HTTP " + status + ")");
    }
    if (authenticated && status >= 300 && status < 400) {
      throw new SpecImportException(
          url
              + " redirects to "
              + response.headers().firstValue("Location").orElse("another address")
              + ". Use that address directly so the credentials are only sent where you intend.");
    }
    throw new SpecImportException("The spec could not be fetched from " + url + ": HTTP " + status);
  }

  private static HttpClient client(HttpClient.Redirect redirects) {
    return HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(redirects).build();
  }
}
