package dev.apipilot.sample;

import dev.apipilot.sample.Model.BillingProfile;
import dev.apipilot.sample.Model.BillingProfileRequest;
import dev.apipilot.sample.Model.Category;
import dev.apipilot.sample.Model.Credentials;
import dev.apipilot.sample.Model.Document;
import dev.apipilot.sample.Model.DocumentPatch;
import dev.apipilot.sample.Model.DocumentRequest;
import dev.apipilot.sample.Model.DocumentStatus;
import dev.apipilot.sample.Model.Member;
import dev.apipilot.sample.Model.MemberRequest;
import dev.apipilot.sample.Model.Token;
import dev.apipilot.sample.Model.User;
import dev.apipilot.sample.Model.Workspace;
import dev.apipilot.sample.Model.WorkspaceRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** In-memory state. Methods are synchronized so uniqueness checks are atomic under load. */
@Component
class Store {

  private final Map<String, String> passwordsByUsername = new HashMap<>();
  private final Set<String> tokens = new HashSet<>();
  private final Map<UUID, Category> categories = new LinkedHashMap<>();
  private final Map<UUID, Workspace> workspaces = new LinkedHashMap<>();
  private final Map<UUID, Member> members = new LinkedHashMap<>();
  private final Map<UUID, BillingProfile> billingProfiles = new LinkedHashMap<>();
  private final Map<UUID, Document> documents = new LinkedHashMap<>();

  Store() {
    for (String name : List.of("General", "Reports", "Archive")) {
      UUID id = UUID.randomUUID();
      categories.put(id, new Category(id, name));
    }
  }

  synchronized User register(Credentials credentials) {
    if (passwordsByUsername.putIfAbsent(credentials.username(), credentials.password()) != null) {
      throw conflict("Username is already taken");
    }
    return new User(UUID.randomUUID(), credentials.username());
  }

  synchronized Token issueToken(Credentials credentials) {
    if (!credentials.password().equals(passwordsByUsername.get(credentials.username()))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
    String token = UUID.randomUUID().toString();
    tokens.add(token);
    return new Token(token, "Bearer");
  }

  synchronized boolean isValidToken(String token) {
    return tokens.contains(token);
  }

  synchronized List<Category> categories() {
    return List.copyOf(categories.values());
  }

  synchronized Category category(UUID id) {
    return found(categories.get(id));
  }

  synchronized Workspace createWorkspace(WorkspaceRequest request) {
    requireUniqueWorkspaceName(request.name(), null);
    Workspace workspace =
        new Workspace(UUID.randomUUID(), request.name(), request.description(), Instant.now());
    workspaces.put(workspace.id(), workspace);
    return workspace;
  }

  synchronized List<Workspace> workspaces() {
    return List.copyOf(workspaces.values());
  }

  synchronized Workspace workspace(UUID id) {
    return found(workspaces.get(id));
  }

  synchronized Workspace updateWorkspace(UUID id, WorkspaceRequest request) {
    Workspace existing = workspace(id);
    requireUniqueWorkspaceName(request.name(), id);
    Workspace updated =
        new Workspace(id, request.name(), request.description(), existing.createdAt());
    workspaces.put(id, updated);
    return updated;
  }

  synchronized void deleteWorkspace(UUID id) {
    workspace(id);
    boolean hasChildren =
        members.values().stream().anyMatch(m -> m.workspaceId().equals(id))
            || billingProfiles.values().stream().anyMatch(b -> b.workspaceId().equals(id))
            || documents.values().stream().anyMatch(d -> d.workspaceId().equals(id));
    if (hasChildren) {
      throw conflict("Workspace still has members, billing profiles or documents");
    }
    workspaces.remove(id);
  }

  synchronized Member addMember(UUID workspaceId, MemberRequest request) {
    workspace(workspaceId);
    if (members(workspaceId).stream().anyMatch(m -> m.email().equalsIgnoreCase(request.email()))) {
      throw conflict("Email is already a member of this workspace");
    }
    Member member = new Member(UUID.randomUUID(), workspaceId, request.email(), request.role());
    members.put(member.id(), member);
    return member;
  }

  synchronized List<Member> members(UUID workspaceId) {
    workspace(workspaceId);
    return members.values().stream().filter(m -> m.workspaceId().equals(workspaceId)).toList();
  }

  synchronized Member member(UUID workspaceId, UUID id) {
    Member member = found(members.get(id));
    return member.workspaceId().equals(workspaceId) ? member : found(null);
  }

  synchronized void removeMember(UUID workspaceId, UUID id) {
    members.remove(member(workspaceId, id).id());
  }

  synchronized BillingProfile createBillingProfile(
      UUID workspaceId, BillingProfileRequest request) {
    workspace(workspaceId);
    if (billingProfiles.values().stream().anyMatch(b -> b.workspaceId().equals(workspaceId))) {
      throw conflict("Workspace already has a billing profile");
    }
    BillingProfile profile =
        new BillingProfile(UUID.randomUUID(), workspaceId, request.companyName(), request.taxId());
    billingProfiles.put(profile.id(), profile);
    return profile;
  }

  synchronized BillingProfile billingProfile(UUID workspaceId, UUID id) {
    BillingProfile profile = found(billingProfiles.get(id));
    return profile.workspaceId().equals(workspaceId) ? profile : found(null);
  }

  synchronized void deleteBillingProfile(UUID workspaceId, UUID id) {
    billingProfiles.remove(billingProfile(workspaceId, id).id());
  }

  synchronized Document createDocument(DocumentRequest request) {
    if (!workspaces.containsKey(request.workspaceId())
        || !categories.containsKey(request.categoryId())) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY, "Unknown workspaceId or categoryId");
    }
    Document document =
        new Document(
            UUID.randomUUID(),
            request.workspaceId(),
            request.categoryId(),
            request.title(),
            request.body(),
            DocumentStatus.DRAFT,
            Instant.now());
    documents.put(document.id(), document);
    return document;
  }

  synchronized List<Document> documents(UUID workspaceId) {
    return documents.values().stream()
        .filter(d -> workspaceId == null || d.workspaceId().equals(workspaceId))
        .toList();
  }

  synchronized Document document(UUID id) {
    return found(documents.get(id));
  }

  synchronized Document patchDocument(UUID id, DocumentPatch patch) {
    Document existing = document(id);
    return replace(
        existing,
        patch.title() != null ? patch.title() : existing.title(),
        patch.body() != null ? patch.body() : existing.body(),
        existing.status());
  }

  synchronized Document publishDocument(UUID id) {
    Document existing = document(id);
    return replace(existing, existing.title(), existing.body(), DocumentStatus.PUBLISHED);
  }

  synchronized void deleteDocument(UUID id) {
    documents.remove(document(id).id());
  }

  private Document replace(Document existing, String title, String body, DocumentStatus status) {
    Document updated =
        new Document(
            existing.id(),
            existing.workspaceId(),
            existing.categoryId(),
            title,
            body,
            status,
            existing.createdAt());
    documents.put(updated.id(), updated);
    return updated;
  }

  private void requireUniqueWorkspaceName(String name, UUID ownId) {
    boolean taken =
        workspaces.values().stream()
            .anyMatch(w -> w.name().equalsIgnoreCase(name) && !w.id().equals(ownId));
    if (taken) {
      throw conflict("Workspace name is already in use");
    }
  }

  private static <T> T found(T value) {
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
    return value;
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }
}
