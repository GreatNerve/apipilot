package dev.apipilot.sample;

import dev.apipilot.sample.Model.Credentials;
import dev.apipilot.sample.Model.Token;
import dev.apipilot.sample.Model.User;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@SecurityRequirements
class AuthController {

  private final Store store;

  AuthController(Store store) {
    this.store = store;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  User register(@Valid @RequestBody Credentials credentials) {
    return store.register(credentials);
  }

  @PostMapping("/token")
  Token token(@Valid @RequestBody Credentials credentials) {
    return store.issueToken(credentials);
  }
}
