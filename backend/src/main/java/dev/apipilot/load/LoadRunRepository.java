package dev.apipilot.load;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class LoadRunRepository {

  private final JdbcClient jdbc;
  private final JsonMapper json;

  LoadRunRepository(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Inserts the run, or replaces it when a run with the same ID exists. */
  public void save(LoadRun run) {
    jdbc.sql(
            "insert into load_run (id, project_id, started_at, run) values (?, ?, ?, ?::jsonb)"
                + " on conflict (id) do update set run = excluded.run")
        .params(
            run.id(),
            run.projectId(),
            run.startedAt().atOffset(ZoneOffset.UTC),
            json.writeValueAsString(run))
        .update();
  }

  public List<LoadRun> findByProjectNewestFirst(UUID projectId) {
    return jdbc
        .sql("select run from load_run where project_id = ? order by started_at desc")
        .param(projectId)
        .query(String.class)
        .list()
        .stream()
        .map(stored -> json.readValue(stored, LoadRun.class))
        .toList();
  }
}
