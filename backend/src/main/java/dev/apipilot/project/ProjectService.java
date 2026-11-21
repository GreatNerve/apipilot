package dev.apipilot.project;

import dev.apipilot.spec.ApiModel;
import dev.apipilot.spec.SpecFetcher;
import dev.apipilot.spec.SpecImportException;
import dev.apipilot.spec.SpecImporter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {

  private static final int MAX_NAME_LENGTH = 200;

  private final ProjectRepository repository;
  private final SpecImporter importer;
  private final SpecFetcher fetcher;

  ProjectService(ProjectRepository repository, SpecImporter importer, SpecFetcher fetcher) {
    this.repository = repository;
    this.importer = importer;
    this.fetcher = fetcher;
  }

  public ProjectSummary create(CreateProjectRequest request) {
    String spec = hasText(request.spec()) ? request.spec() : fetch(request);
    ApiModel api = importer.importSpec(spec);
    String name = hasText(request.name()) ? request.name() : api.title();
    ProjectSummary project =
        new ProjectSummary(
            UUID.randomUUID(),
            displayName(name),
            api.title(),
            api.version(),
            api.operations().size(),
            Instant.now());
    repository.insert(project, spec);
    return project;
  }

  public List<ProjectSummary> list() {
    return repository.findAllNewestFirst();
  }

  public Optional<ProjectDetail> find(UUID id) {
    return repository
        .find(id)
        .map(p ->
            new ProjectDetail(
                p.id(),
                p.name(),
                p.title(),
                p.version(),
                p.operationCount(),
                p.createdAt(),
                importer.importSpec(repository.findSpec(id).orElseThrow())));
  }

  public boolean exists(UUID id) {
    return repository.find(id).isPresent();
  }

  public boolean delete(UUID id) {
    return repository.delete(id);
  }

  private String fetch(CreateProjectRequest request) {
    if (!hasText(request.specUrl())) {
      throw new SpecImportException("Provide the spec content or a URL to fetch it from");
    }
    return fetcher.fetch(request.specUrl().strip(), request.specUsername(), request.specPassword());
  }

  private static String displayName(String name) {
    if (!hasText(name)) {
      return "Untitled API";
    }
    String trimmed = name.strip();
    return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
