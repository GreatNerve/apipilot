package dev.apipilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WebShellTest {

  @Value("${local.server.port}")
  int port;

  @Test
  void healthIsServedUnderApi() throws Exception {
    HttpResponse<String> response = HttpTestSupport.get(port, "/api/health");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  @Test
  void exportedPageIsServedWithoutHtmlExtension() throws Exception {
    HttpResponse<String> response = HttpTestSupport.get(port, "/fixture-page");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("fixture page");
  }

  @Test
  void unknownApiPathIsNotMaskedByStaticPages() throws Exception {
    HttpResponse<String> response = HttpTestSupport.get(port, "/api/does-not-exist");

    assertThat(response.statusCode()).isEqualTo(404);
  }
}
