# apipilot

Generate API test suites and load tests from an OpenAPI spec.

Point apipilot at an OpenAPI 3 or Swagger 2 document. It works out, from the shape of the API alone, which operations belong to the same resource and which resources need IDs from others (for example, a document needs a workspace first). You can then browse the endpoints, check and correct the dependency graph, and view load-test reports.

> **Status:** early development (milestone M0). Importing specs, the endpoint explorer, the dependency graph and the load-run report views work. You can't start load runs from the app yet.

## Features

- **Import a spec** by pasting it or giving a URL. Password-protected URLs are supported; the credentials are sent once and never stored.
- **Endpoint explorer** that lists operations with their request and response schemas.
- **Dependency graph** that is inferred automatically. It shows which resources depend on which, and you can override edges the analyzer got wrong.
- **Test data generation** that fits each schema. Free-text values get a random token so repeated calls never hit unique constraints.
- **Load-run reports** for load, spike, soak and capacity runs, with per-step latency and throughput charts.

## Tech stack

| Part | Stack |
|---|---|
| Backend | Java 25, Spring Boot 4, Flyway, PostgreSQL 18, swagger-parser, Datafaker |
| Frontend | Next.js 16 (static export), React 19, TypeScript, Tailwind CSS 4, shadcn/ui, React Flow, Recharts |
| Load generator | k6 (bundled in the Docker image) |
| Tests | JUnit and Testcontainers, Vitest and Testing Library |

## Repository layout

```
backend/      Spring Boot app: spec import, dependency inference, data generation, REST API
frontend/     Next.js UI, exported as static files and served by the backend
sample-api/   Small demo API (workspaces, documents, categories, auth) to import and try things on
docker/       All-in-one image: app, JRE, k6 and an embedded Postgres
docker-compose.yml   Postgres for local development
```

## Quick start (Docker)

The image bundles everything, including its own Postgres:

```sh
docker build -f docker/Dockerfile -t apipilot .
docker run -p 8080:8080 -v apipilot-data:/data apipilot
```

Then open http://localhost:8080.

To use an external database instead, set `APIPILOT_DB_URL` (and `APIPILOT_DB_USER` / `APIPILOT_DB_PASSWORD`). The embedded Postgres is then not started.

## Local development

Prerequisites: JDK 25, Node.js 24+, and Docker (for Postgres and the backend tests).

1. Start Postgres:

   ```sh
   docker compose up -d
   ```

2. Run the backend on http://127.0.0.1:8080:

   ```sh
   ./mvnw -pl backend spring-boot:run
   ```

3. Run the frontend on http://localhost:3000. In dev mode it proxies `/api` to the backend:

   ```sh
   cd frontend
   npm install
   npm run dev
   ```

4. Optional: run the sample API on http://localhost:8090 and import its spec from `http://localhost:8090/v3/api-docs`:

   ```sh
   ./mvnw -pl sample-api spring-boot:run
   ```

### Useful endpoints

| Path | What it is |
|---|---|
| `/api/health` | Health check |
| `/api/openapi` | apipilot's own OpenAPI document |
| `/api/projects` | Create, list and delete projects (imported specs) |
| `/api/projects/{id}/graph` | Inferred dependency graph and its overrides |
| `/api/projects/{id}/load-runs` | Load-run results, newest first |

## Testing and code style

```sh
# Backend (Testcontainers starts Postgres, so Docker must be running)
./mvnw verify
./mvnw spotless:apply      # format to Google Java Style

# Frontend
cd frontend
npm test
npm run lint
npm run typecheck
npm run gen:api            # regenerate src/lib/api-types.ts from a running backend
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `APIPILOT_DB_URL` | `jdbc:postgresql://localhost:5432/apipilot` | JDBC URL of the database |
| `APIPILOT_DB_USER` | `apipilot` | Database user |
| `APIPILOT_DB_PASSWORD` | `apipilot` | Database password |
| `SERVER_ADDRESS` | `127.0.0.1` (`0.0.0.0` in Docker) | Interface the server listens on |

## License

[MIT](LICENSE). The public specs in `backend/src/test/resources/specs/public/` keep their own licenses; see the [README](backend/src/test/resources/specs/public/README.md) there.
