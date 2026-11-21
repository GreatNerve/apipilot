package dev.apipilot.inference;

import dev.apipilot.project.ProjectService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects/{projectId}/graph")
class GraphController {

  private final GraphService graphs;
  private final ProjectService projects;

  GraphController(GraphService graphs, ProjectService projects) {
    this.graphs = graphs;
    this.projects = projects;
  }

  @GetMapping
  DependencyGraph graph(@PathVariable UUID projectId) {
    return graphs.graph(projectId).orElseThrow(() -> notFound(projectId));
  }

  @GetMapping("/overrides")
  GraphOverrides overrides(@PathVariable UUID projectId) {
    requireProject(projectId);
    return graphs.overrides(projectId);
  }

  @PutMapping("/overrides")
  GraphOverrides saveOverrides(
      @PathVariable UUID projectId, @RequestBody GraphOverrides overrides) {
    requireProject(projectId);
    graphs.saveOverrides(projectId, overrides);
    return overrides;
  }

  private void requireProject(UUID projectId) {
    if (!projects.exists(projectId)) {
      throw notFound(projectId);
    }
  }

  private static ResponseStatusException notFound(UUID projectId) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with id " + projectId);
  }
}
