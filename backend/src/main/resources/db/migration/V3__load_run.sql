create table load_run (
  id uuid primary key,
  project_id uuid not null references project (id) on delete cascade,
  started_at timestamptz not null,
  run jsonb not null
);

create index load_run_project_started on load_run (project_id, started_at desc);
