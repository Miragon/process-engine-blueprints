# Contributing

Thanks for your interest in the Operaton bike-leasing blueprint! Contributions of all kinds are
welcome — bug reports, feature ideas, docs, and code.

## Getting started

```bash
git clone git@github.com:miragon-blueprints/operaton-embedded-example.git
cd operaton-embedded-example
npm ci && npm run hooks:install                                # BPMN lint + git hooks
```

You need **JDK 21**, **Node ≥ 22.12**, and **Docker (or Podman)** for Postgres. The BPMN linting
tooling (`bpmnlint`) and the git-hook installer live at the repo **root** (`package.json`,
`.bpmnlintrc`, `.npmrc`).

Run the stack locally:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres
./gradlew :service:app:bootRun                      # backend + engine on :8080
```

### Ports

| What | Port |
|---|---|
| Postgres | 5432 |
| Backend (REST + `/engine-rest`) | 8080 |
| Operaton Cockpit / webapps | 8080/operaton (admin/admin) |
| OpenAPI spec · Swagger UI | 8080/v3/api-docs · 8080/swagger-ui.html |
| Actuator (health · liveness/readiness · prometheus) | 8080/actuator |

Under Conductor the ports are fixed and the workspace runs `nonconcurrent` (see
[ADR-0006](docs/adr/0006-fixed-ports-for-v1-portless-as-the-upgrade.md)).

### Bruno smoke test

With the stack running, drive the REST scenarios against the live app:

```bash
cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r
```

The collections exercise the domain REST endpoints (submit, sign, report-handover, withdraw,
clarify-alternative, the paged `GET /api/bike-leasing` list, `GET /api/bikes`, and the
`GET /api/tasks/clarify-alternative` inbox), while the Operaton `/engine-rest` API completes user
tasks and fires timer jobs so the whole flow runs without real 14-day waits.

Confirm <http://localhost:8080/operaton> (admin/admin), <http://localhost:8080/swagger-ui.html> and
<http://localhost:8080/actuator/health> (status `UP`) all load.

## Run it in containers

The dev loop above runs the backend from source. To run it as a container against a containerized
Postgres, build the backend OCI image (Spring buildpacks — no Dockerfile), then bring up Postgres
from the dev stack. The rationale is in
[ADR-0011](docs/adr/0011-build-and-deployment-approach.md).

```bash
# 1. build the backend OCI image (Spring buildpacks — no Dockerfile). Produces miravelo/app:1.0-SNAPSHOT
./gradlew :service:app:bootBuildImage

# 2. start Postgres
docker compose -f stack/docker-compose.yml up -d

# 3. run the image against it (dev defaults are baked in; override for anything real)
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/bikeleasing \
  -e SPRING_DATASOURCE_USERNAME=admin -e SPRING_DATASOURCE_PASSWORD=admin \
  miravelo/app:1.0-SNAPSHOT
```

Then open <http://localhost:8080/operaton> (admin/admin) and run the Bruno smoke test above.

**Podman:** `bootBuildImage` needs a Docker-API socket. Expose podman's and point the build at it:

```bash
podman system service --time=0 unix:///tmp/podman.sock &
export DOCKER_HOST=unix:///tmp/podman.sock
./gradlew :service:app:bootBuildImage
```

**Configuration.** `application.yaml` ships dev defaults; the deploy-relevant values are read from the
environment (they win over the baked defaults):

| Env var | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://postgres:5432/bikeleasing` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | `admin` / `admin` |

> **Not production-hardened.** The image carries the example admin/admin credentials from
> `application.yaml`. Override them (and the DB credentials) before running anywhere real. Schema is
> owned by Flyway and Hibernate only validates ([ADR-0010](docs/adr/0010-flyway-for-database-migrations.md)),
> so the Postgres volume persists across `down`/`up` — reset it with
> `docker compose -f stack/docker-compose.yml down -v`.

## Scripts

```bash
# backend
./gradlew build                         # arch + unit + process + model validation + spec export
./gradlew :service:app:pitest           # mutation score >= 80
./gradlew generateBpmnModels            # regenerate the typed process API after editing a .bpmn

# BPMN (root-level tooling)
npm run lint:bpmn                       # bpmnlint the .bpmn models
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
- **Keep the gates green.** The architecture (ArchUnit + Konsist), contract-drift and mutation (≥ 80)
  gates run in CI on every PR. They are fitness functions, not style guides — a violation fails the
  build. The mutation gate is **diff-scoped** on PRs (only the classes you changed); the full-module
  gate-80 sweep runs nightly.
- **Add tests.** This is a TDD codebase; match the test style to the layer (see `AGENTS.md`).
  Mutation testing means a test that runs without asserting will fail CI.
- **Changing the API?** Regenerate and commit `openapi/openapi.json` in the same change; the contract
  is drift-gated, so a stale spec fails CI.
- **Changing the database schema?** Flyway owns it. Add a new forward-only migration
  `V{n}__description.sql` under `service/app/src/main/resources/db/migration/` in the same change as
  the entity edit — never edit an already-applied migration. Hibernate runs `validate`, so a mismatch
  fails startup. See [ADR-0010](docs/adr/0010-flyway-for-database-migrations.md).

## Before opening a PR

```bash
./gradlew build
git diff --exit-code openapi/openapi.json    # the API contract must not drift
./gradlew :service:app:pitest                # mutation score >= 80
```

All of these run in CI on every pull request (JDK 21 / Node ≥ 22.12).

## Reporting bugs / requesting features

Open an issue. For a process- or contract-related bug, attaching the relevant `.bpmn` model or the
`openapi.json` diff is the fastest path to a fix.
</content>
