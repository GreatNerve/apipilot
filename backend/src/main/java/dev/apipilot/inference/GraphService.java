package dev.apipilot.inference;

import dev.apipilot.project.ProjectService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class GraphService {

  private final ProjectService projects;
  private final DependencyAnalyzer analyzer;
  private final JdbcClient jdbc;
  private final JsonMapper json;

  GraphService(
      ProjectService projects, DependencyAnalyzer analyzer, JdbcClient jdbc, JsonMapper json) {
    this.projects = projects;
    this.analyzer = analyzer;
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Empty when the project does not exist. */
  public Optional<DependencyGraph> graph(UUID projectId) {
    return projects
        .find(projectId)
        .map(project -> analyzer.analyze(project.api(), overrides(projectId)));
  }

  public GraphOverrides overrides(UUID projectId) {
    return jdbc.sql("select overrides from graph_overrides where project_id = ?")
        .param(projectId)
        .query(String.class)
        .optional()
        .map(stored -> json.readValue(stored, GraphOverrides.class))
        .orElse(GraphOverrides.NONE);
  }

  public void saveOverrides(UUID projectId, GraphOverrides overrides) {
    jdbc.sql(
            "insert into graph_overrides (project_id, overrides) values (?, ?::jsonb)"
                + " on conflict (project_id) do update set overrides = excluded.overrides")
        .params(projectId, json.writeValueAsString(overrides))
        .update();
  }
}
