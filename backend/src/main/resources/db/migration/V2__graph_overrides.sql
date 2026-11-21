create table graph_overrides (
  project_id uuid primary key references project (id) on delete cascade,
  overrides jsonb not null
);
