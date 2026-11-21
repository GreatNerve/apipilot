package dev.apipilot.sample;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response shapes of the sample API. */
public final class Model {

  private Model() {}

  public enum Role {
    VIEWER,
    EDITOR,
    ADMIN
  }

  public enum DocumentStatus {
    DRAFT,
    PUBLISHED
  }

  public record Credentials(
      @NotBlank @Size(min = 3, max = 64) String username,
      @NotBlank @Size(min = 8, max = 128) String password) {}

  public record User(UUID id, String username) {}

  public record Token(String accessToken, String tokenType) {}

  public record Category(UUID id, String name) {}

  public record WorkspaceRequest(
      @NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}

  public record Workspace(UUID id, String name, String description, Instant createdAt) {}

  public record MemberRequest(@NotBlank @Email String email, @NotNull Role role) {}

  public record Member(UUID id, UUID workspaceId, String email, Role role) {}

  public record BillingProfileRequest(
      @NotBlank @Size(max = 120) String companyName,
      @Pattern(regexp = "[A-Z]{2}[0-9]{8}") String taxId) {}

  public record BillingProfile(UUID id, UUID workspaceId, String companyName, String taxId) {}

  public record DocumentRequest(
      @NotNull UUID workspaceId,
      @NotNull UUID categoryId,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 10000) String body) {}

  public record DocumentPatch(
      @Size(min = 1, max = 200) String title, @Size(max = 10000) String body) {}

  public record Document(
      UUID id,
      UUID workspaceId,
      UUID categoryId,
      String title,
      String body,
      DocumentStatus status,
      Instant createdAt) {}
}
