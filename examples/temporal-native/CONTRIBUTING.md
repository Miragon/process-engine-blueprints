# Contributing

Thanks for your interest in the Temporal bike-leasing blueprint! Bug reports and feature ideas
are welcome as issues; we do not accept external pull requests.

## Getting started

```bash
git clone <this repository> process-engine-blueprints
cd process-engine-blueprints/examples/temporal-native
```

You need **JDK 21** and **Docker (or Podman)** for the local Temporal stack and
Postgres.


The build wrapper is included, so nothing else needs installing. Start Temporal and Postgres, then the backend
on :8081, which connects to Temporal on :7233:

```bash
docker compose -f stack/docker-compose.yml up -d
```

```bash
cd kotlin-gradle && ./gradlew :service:app:bootRun
```

### Ports

| What | Port |
|---|---|
| Postgres (app read-model DB) | 5432 |
| Backend REST | 8081 |
| Temporal gRPC | 7233 |
| Temporal UI | 8233 |
| OpenAPI spec · Swagger UI | 8081/v3/api-docs · 8081/swagger-ui.html |
| Actuator (health · liveness/readiness · prometheus) | 8081/actuator |

The backend serves its `/api` surface on a single origin, so no CORS code runs on the production path
(a dev-only escape hatch exists under the `dev` profile). Under Conductor the ports are fixed and the
workspace runs `nonconcurrent` (see [ADR-0006](docs/adr/0006-fixed-ports-for-v1-portless-as-the-upgrade.md)).

### Manual smoke test

With the stack and backend running, drive the API scenarios and confirm the operational surface:

```bash
cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r --exclude-tags=demo-data   # the end-to-end API scenarios
```

Confirm <http://localhost:8081/swagger-ui.html>, <http://localhost:8081/actuator/health> (status
`UP`) and the Temporal UI at <http://localhost:8233> all load. The 14-day withdrawal-period timer
(HANDED_OVER → ACTIVE) cannot be fast-forwarded on a running server, so that final transition is covered
by the process test rather than by Bruno.

## Run it in containers

The dev loop above runs the backend from source. To run it as an OCI image instead — built by Spring's
Cloud Native Buildpacks integration, no Dockerfile to maintain — build the image and run it against the
local stack. The rationale is in [ADR-0011](docs/adr/0011-build-and-deployment-approach.md).

Build the backend OCI image; it produces `miravelo/temporal-example:1.0-SNAPSHOT`:

```bash
(cd kotlin-gradle && ./gradlew :service:app:bootBuildImage)
```

Then run it against the local Temporal + Postgres stack:

```bash
docker compose -f stack/docker-compose.yml up -d
docker run --rm --network host \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bikeleasing \
  -e SPRING_DATASOURCE_USERNAME=admin -e SPRING_DATASOURCE_PASSWORD=admin \
  miravelo/temporal-example:1.0-SNAPSHOT
```

**Podman:** the image build needs a Docker-API socket. Expose podman's, then build the image as above:

```bash
podman system service --time=0 unix:///tmp/podman.sock &
export DOCKER_HOST=unix:///tmp/podman.sock
```

**Configuration.** `application.yaml` ships dev defaults; the deploy-relevant values are read from the
environment (they win over the baked defaults):

| Env var | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/bikeleasing` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | `admin` / `admin` |
| `TEMPORAL_ADDRESS` | Temporal frontend | `localhost:7233` |

> **Not production-hardened.** Override the DB credentials and point `TEMPORAL_ADDRESS` at your own
> cluster before running anywhere real. The Temporal dev server keeps its state in memory.

## Ground rules

- **Start from an issue.** Every change traces back to one — open an issue (or pick an existing one)
  and agree on the approach *before* you write code, then reference it in the PR (`Closes #123`).
  This keeps substantial changes discussed up front and the history navigable.
- **Read [`AGENTS.md`](AGENTS.md) first.** It is the single source of guidance for humans and AI
  agents alike.
- **Conventional Commits.** Commit messages and PR titles follow
  [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`,
  `refactor:`, `test:`, `chore:`). Write everything in **English**.
- **Keep the gates green.** The architecture, contract-drift and mutation (≥ 80)
  gates run in CI on every PR. They are fitness functions, not style guides — a violation fails the
  build. The mutation gate is **diff-scoped** on PRs (only the classes you changed); the full-module
  gate-80 sweep runs nightly.
- **Add tests.** This is a TDD codebase; match the test style to the layer (see `AGENTS.md`).
  Mutation testing means a test that runs without asserting will fail CI.
- **Changing the API?** Re-export the spec (the `OpenApiSpecExportTest`, which every full build runs)
  so the committed `openapi/openapi.json` contract stays in sync — it is **drift-gated in CI**.
- **Changing the process?** Change the workflow in `adapter/process` with a process test per new
  branch. A running case replays its history against the new code, so version a change that alters the
  order of activities or timers (`Workflow.getVersion`).
- **Changing the database schema?** Flyway owns it. Add a new forward-only migration
  `V{n}__description.sql` under `service/app/src/main/resources/db/migration/` in the same change as
  the entity edit — never edit an already-applied migration. Hibernate runs `validate`, so a mismatch
  fails startup. See [ADR-0010](docs/adr/0010-flyway-for-database-migrations.md).

## Before opening a PR

```bash
(cd kotlin-gradle && ./gradlew build && ./gradlew :service:app:pitest)   # mutation score >= 80
```

```bash
git diff --exit-code openapi/openapi.json    # the API contract must not drift
```

All of these run in CI on every pull request (JDK 21). Before opening a PR, sanity-check
that a feature is wired end to end across the backend — workflow step → outbound port → service →
inbound port → REST controller → `openapi.json` → Bruno scenario. The Claude Code agent
`review-vertical-slice` walks this chain for you.

## Reporting bugs / requesting features

Open an issue. For a process- or contract-related bug, attaching the workflow history or the
`openapi.json` diff is the fastest path to a fix.
