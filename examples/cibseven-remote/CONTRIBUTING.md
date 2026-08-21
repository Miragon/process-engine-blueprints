# Contributing

Thanks for your interest in the remote bike-leasing blueprint! Contributions of all kinds are
welcome — bug reports, feature ideas, docs, and code.

## Getting started

```bash
git clone git@github.com:miragon-blueprints/cibseven-remote-example.git
cd cibseven-remote-example
npm ci && npm run hooks:install                                # BPMN lint + git hooks
```

You need **JDK 21** and **Docker (or Podman)** for Postgres.

Run the whole stack locally — Postgres, then the engine host, then the worker. The worker deploys its
process into the engine at start-up, so the engine must be running first:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres (bikeleasing_engine + bikeleasing_app)
./gradlew :service:engine-service:bootRun           # engine host + Cockpit on :8081
./gradlew :service:example-service:bootRun          # worker (REST + external-task workers) on :8082
```

### Ports

| What | Port |
|---|---|
| Postgres (`bikeleasing_engine`, `bikeleasing_app`) | 5432 |
| Engine host (`/engine-rest`) | 8081 |
| CIB seven Cockpit / webapps | 8081/camunda (admin/admin) |
| Worker REST · OpenAPI spec · Swagger UI | 8082/api · 8082/v3/api-docs · 8082/swagger-ui.html |
| Worker actuator (health · liveness/readiness · prometheus) | 8082/actuator |

Under Conductor the ports are fixed and the workspace runs `nonconcurrent` (see
[ADR-0006](docs/adr/0006-fixed-ports-for-v1-portless-as-the-upgrade.md)).

### Manual smoke test

Start a case against the worker:

```bash
curl -X POST http://localhost:8082/api/bike-leasing \
  -H 'Content-Type: application/json' \
  -d '{ "customerName": "Ada", "email": "ada@example.com", "age": 35, "monthlyNetIncome": 3500, "bikeId": "BIKE-900", "bikeModel": "Gravel Explorer 900" }'
```

Watch the external-task workers auto-complete `validateApplication`, `orderBike`, … in the
`example-service` log, and inspect the running instance in the CIB seven Cockpit at
<http://localhost:8081/camunda> (admin/admin). Confirm <http://localhost:8082/swagger-ui.html> and
<http://localhost:8082/actuator/health> (status `UP`) load. To exercise incidents/retries, submit the
poison bike `BIKE-FAIL` and watch the *Order bike from dealer* task fail and raise an incident in
Cockpit.

## Run it in containers

The dev loop above runs both apps from source. You can also build OCI images with Spring buildpacks
(no Dockerfile). The rationale is in
[ADR-0011](docs/adr/0011-build-and-deployment-approach.md).

```bash
# build the worker OCI image — produces miravelo/example-service:1.0-SNAPSHOT
./gradlew :service:example-service:bootBuildImage
```

**Podman:** `bootBuildImage` needs a Docker-API socket. Expose podman's and point the build at it:

```bash
podman system service --time=0 unix:///tmp/podman.sock &
export DOCKER_HOST=unix:///tmp/podman.sock
./gradlew :service:example-service:bootBuildImage
```

**Configuration.** `application.yaml` ships dev defaults; the deploy-relevant values are read from the
environment (they win over the baked defaults):

| Env var | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | worker JDBC URL | `jdbc:postgresql://localhost:5432/bikeleasing_app` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | `admin` / `admin` |

> **Not production-hardened.** The images carry the example credentials from `application.yaml`.
> Override them (and the DB credentials) before running anywhere real. Schema is owned by Flyway and
> Hibernate only validates ([ADR-0010](docs/adr/0010-flyway-for-database-migrations.md)), so the
> Postgres volume persists across `down`/`up` — reset it with `docker compose -f
> stack/docker-compose.yml down -v`.

## Scripts

```bash
# build & test (all modules)
./gradlew build                                    # arch + unit + process + model validation + spec export
./gradlew :service:example-service:pitest          # worker mutation score >= 80

# BPMN
npm run lint:bpmn                                  # bpmnlint the .bpmn models (from the repo root)
```

## Ground rules

- **Start from an issue.** Every change traces back to one — open an issue (or pick an existing one)
  and agree on the approach *before* you write code, then reference it in the PR (`Closes #123`).
  This keeps substantial changes discussed up front and the history navigable.
- **Read [`AGENTS.md`](AGENTS.md) first.** It is the single source of guidance for humans and AI
  agents alike.
- **Conventional Commits.** Commit messages and PR titles follow
  [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`,
  `refactor:`, `test:`, `chore:`). Write everything in **English**.
- **Keep the gates green.** The architecture (ArchUnit + Konsist), contract-drift and mutation
  (≥ 80) gates run in CI on every PR. They are fitness functions, not style guides — a violation
  fails the build.
- **Add tests.** This is a TDD codebase; match the test style to the layer (see `AGENTS.md`).
  Mutation testing means a test that runs without asserting will fail CI.
- **Changing the worker's API?** The committed `openapi/openapi.json` contract is regenerated by a
  test during `./gradlew build` and drift-gated — commit the regenerated spec in the same change.
- **Changing the database schema?** Flyway owns it. Add a new forward-only migration
  `V{n}__description.sql` under `service/example-service/src/main/resources/db/migration/` in the
  same change as the entity edit — never edit an already-applied migration. Hibernate runs
  `validate`, so a mismatch fails startup. See
  [ADR-0010](docs/adr/0010-flyway-for-database-migrations.md).

## Before opening a PR

```bash
./gradlew build
git diff --exit-code openapi/openapi.json          # the API contract must not drift
./gradlew :service:example-service:pitest          # mutation score >= 80
```

All of these run in CI on every pull request (JDK 21).

## Reporting bugs / requesting features

Open an issue. For a process- or contract-related bug, attaching the relevant `.bpmn` model or the
`openapi.json` diff is the fastest path to a fix.
