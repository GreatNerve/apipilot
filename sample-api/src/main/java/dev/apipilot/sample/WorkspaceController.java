package dev.apipilot.sample;

import dev.apipilot.sample.Model.BillingProfile;
import dev.apipilot.sample.Model.BillingProfileRequest;
import dev.apipilot.sample.Model.Member;
import dev.apipilot.sample.Model.MemberRequest;
import dev.apipilot.sample.Model.Workspace;
import dev.apipilot.sample.Model.WorkspaceRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Root resource plus its two nested children: members and the single billing profile. */
@RestController
@RequestMapping("/workspaces")
class WorkspaceController {

  private final Store store;

  WorkspaceController(Store store) {
    this.store = store;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Workspace create(@Valid @RequestBody WorkspaceRequest request) {
    return store.createWorkspace(request);
  }

  @GetMapping
  List<Workspace> list() {
    return store.workspaces();
  }

  @GetMapping("/{workspaceId}")
  Workspace get(@PathVariable UUID workspaceId) {
    return store.workspace(workspaceId);
  }

  @PutMapping("/{workspaceId}")
  Workspace update(@PathVariable UUID workspaceId, @Valid @RequestBody WorkspaceRequest request) {
    return store.updateWorkspace(workspaceId, request);
  }

  @DeleteMapping("/{workspaceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID workspaceId) {
    store.deleteWorkspace(workspaceId);
  }

  @PostMapping("/{workspaceId}/members")
  @ResponseStatus(HttpStatus.CREATED)
  Member addMember(@PathVariable UUID workspaceId, @Valid @RequestBody MemberRequest request) {
    return store.addMember(workspaceId, request);
  }

  @GetMapping("/{workspaceId}/members")
  List<Member> listMembers(@PathVariable UUID workspaceId) {
    return store.members(workspaceId);
  }

  @GetMapping("/{workspaceId}/members/{memberId}")
  Member getMember(@PathVariable UUID workspaceId, @PathVariable UUID memberId) {
    return store.member(workspaceId, memberId);
  }

  @DeleteMapping("/{workspaceId}/members/{memberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeMember(@PathVariable UUID workspaceId, @PathVariable UUID memberId) {
    store.removeMember(workspaceId, memberId);
  }

  @PostMapping("/{workspaceId}/billing-profiles")
  @ResponseStatus(HttpStatus.CREATED)
  BillingProfile createBillingProfile(
      @PathVariable UUID workspaceId, @Valid @RequestBody BillingProfileRequest request) {
    return store.createBillingProfile(workspaceId, request);
  }

  @GetMapping("/{workspaceId}/billing-profiles/{billingProfileId}")
  BillingProfile getBillingProfile(
      @PathVariable UUID workspaceId, @PathVariable UUID billingProfileId) {
    return store.billingProfile(workspaceId, billingProfileId);
  }

  @DeleteMapping("/{workspaceId}/billing-profiles/{billingProfileId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteBillingProfile(@PathVariable UUID workspaceId, @PathVariable UUID billingProfileId) {
    store.deleteBillingProfile(workspaceId, billingProfileId);
  }
}
