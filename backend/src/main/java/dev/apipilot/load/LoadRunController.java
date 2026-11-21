package dev.apipilot.load;

import dev.apipilot.project.ProjectService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects/{projectId}/load-runs")
class LoadRunController {

  private final LoadRunRepository runs;
  private final ProjectService projects;

  LoadRunController(LoadRunRepository runs, ProjectService projects) {
    this.runs = runs;
    this.projects = projects;
  }

  @GetMapping
  List<LoadRun> list(@PathVariable UUID projectId) {
    if (!projects.exists(projectId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No project with id " + projectId);
    }
    return runs.findByProjectNewestFirst(projectId);
  }
}
