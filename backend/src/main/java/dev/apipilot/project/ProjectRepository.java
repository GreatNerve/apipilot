package dev.apipilot.project;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProjectRepository {

  private static final String SUMMARY_COLUMNS =
      "id, name, title, version, operation_count, created_at";

  private final JdbcClient jdbc;

  ProjectRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void insert(ProjectSummary project, String spec) {
    jdbc.sql(
            "insert into project (id, name, title, version, operation_count, spec, created_at)"
                + " values (?, ?, ?, ?, ?, ?, ?)")
        .params(
            project.id(),
            project.name(),
            project.title(),
            project.version(),
            project.operationCount(),
            spec,
            project.createdAt().atOffset(ZoneOffset.UTC))
        .update();
  }

  List<ProjectSummary> findAllNewestFirst() {
    return jdbc.sql("select " + SUMMARY_COLUMNS + " from project order by created_at desc")
        .query((rs, row) -> summary(rs))
        .list();
  }

  Optional<ProjectSummary> find(UUID id) {
    return jdbc.sql("select " + SUMMARY_COLUMNS + " from project where id = ?")
        .param(id)
        .query((rs, row) -> summary(rs))
        .optional();
  }

  Optional<String> findSpec(UUID id) {
    return jdbc.sql("select spec from project where id = ?")
        .param(id)
        .query(String.class)
        .optional();
  }

  boolean delete(UUID id) {
    return jdbc.sql("delete from project where id = ?").param(id).update() > 0;
  }

  private static ProjectSummary summary(ResultSet rs) throws SQLException {
    return new ProjectSummary(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("title"),
        rs.getString("version"),
        rs.getInt("operation_count"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }
}
