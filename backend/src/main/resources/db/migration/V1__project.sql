create table project (
  id uuid primary key,
  name varchar(200) not null,
  title varchar(500),
  version varchar(100),
  operation_count int not null,
  spec text not null,
  created_at timestamptz not null
);
