package dev.apipilot.sample;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@OpenAPIDefinition(
    info = @Info(title = "Sample API", version = "1.0"),
    security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer")
class ApiConfig implements WebMvcConfigurer {

  private static final String BEARER = "Bearer ";

  private final Store store;

  ApiConfig(Store store) {
    this.store = store;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new BearerTokenInterceptor())
        .excludePathPatterns("/auth/**", "/v3/api-docs/**", "/error");
  }

  private final class BearerTokenInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler)
        throws IOException {
      String header = request.getHeader(HttpHeaders.AUTHORIZATION);
      if (header != null
          && header.startsWith(BEARER)
          && store.isValidToken(header.substring(BEARER.length()))) {
        return true;
      }
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid bearer token");
      return false;
    }
  }
}
