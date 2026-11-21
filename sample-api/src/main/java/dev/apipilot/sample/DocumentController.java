package dev.apipilot.sample;

import dev.apipilot.sample.Model.Document;
import dev.apipilot.sample.Model.DocumentPatch;
import dev.apipilot.sample.Model.DocumentRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Top-level resource that references a workspace and a category by ID in the request body. */
@RestController
@RequestMapping("/documents")
class DocumentController {

  private final Store store;

  DocumentController(Store store) {
    this.store = store;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Document create(@Valid @RequestBody DocumentRequest request) {
    return store.createDocument(request);
  }

  @GetMapping
  List<Document> list(@RequestParam(required = false) UUID workspaceId) {
    return store.documents(workspaceId);
  }

  @GetMapping("/{documentId}")
  Document get(@PathVariable UUID documentId) {
    return store.document(documentId);
  }

  @PatchMapping("/{documentId}")
  Document patch(@PathVariable UUID documentId, @Valid @RequestBody DocumentPatch patch) {
    return store.patchDocument(documentId, patch);
  }

  @PostMapping("/{documentId}/publish")
  Document publish(@PathVariable UUID documentId) {
    return store.publishDocument(documentId);
  }

  @DeleteMapping("/{documentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID documentId) {
    store.deleteDocument(documentId);
  }
}
