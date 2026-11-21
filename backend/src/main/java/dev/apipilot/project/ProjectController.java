package dev.apipilot.project;

import dev.apipilot.spec.SpecImportException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects")
class ProjectController {

  private final ProjectService projects;

  ProjectController(ProjectService projects) {
    this.projects = projects;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ProjectSummary create(@RequestBody CreateProjectRequest request) {
    return projects.create(request);
  }

  @GetMapping
  List<ProjectSummary> list() {
    return projects.list();
  }

  @GetMapping("/{id}")
  ProjectDetail get(@PathVariable UUID id) {
    return projects.find(id).orElseThrow(() -> notFound(id));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    if (!projects.delete(id)) {
      throw notFound(id);
    }
  }

  @ExceptionHandler(SpecImportException.class)
  ProblemDetail invalidSpec(SpecImportException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  private static ResponseStatusException notFound(UUID id) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with id " + id);
  }
}
