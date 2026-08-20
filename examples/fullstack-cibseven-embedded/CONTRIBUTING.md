# Contributing

Thanks for your interest in the fullstack bike-leasing blueprint! Contributions of all kinds are
welcome — bug reports, feature ideas, docs, and code.

## Getting started

```bash
git clone git@github.com:miragon-blueprints/fullstack-example.git
cd fullstack-example
npm --prefix tools ci && npm --prefix tools run hooks:install   # BPMN lint + git hooks
npm --prefix frontend ci                                        # frontend dependencies
```

You need **JDK 21**, **Node ≥ 22.12**, and **Docker (or Podman)** for Postgres. The backend and
frontend are two separate toolchains on purpose — the frontend stays out of the Gradle build (see
[ADR-0005](docs/adr/0005-frontend-stays-out-of-the-gradle-build.md)).

Run the whole stack locally:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres
./gradlew :service:app:bootRun                      # backend + engine on :8080
npm --prefix frontend run dev                       # UI on :5173 (proxies /api to :8080)
```

### Ports

| What | Port |
|---|---|
| Postgres | 5432 |
| Backend (REST + `/engine-rest`) | 8080 |
| CIB seven Cockpit / webapps | 8080/camunda (admin/admin) |
| OpenAPI spec · Swagger UI | 8080/v3/api-docs · 8080/swagger-ui.html |
| Actuator (health · liveness/readiness · prometheus) | 8080/actuator |
| Vite dev server | 5173 |

Vite proxies `/api`, `/v3/api-docs`, `/engine-rest` and `/camunda` to `:8080`, so the browser sees one
origin and no CORS code runs on the production path. Under Conductor the ports are fixed and the
workspace runs `nonconcurrent` (see [ADR-0008](docs/adr/0008-fixed-ports-for-v1-portless-as-the-upgrade.md)).

### Manual smoke test

Open <http://localhost:5173> and:

1. submit an application choosing **Mountain Trail 600 (BIKE-OOS)** — deliberately out of stock;
2. sign the contract; the order finds it unavailable and parks the case;
3. open `/aufgaben`, resolve the clarification choosing **Gravel Explorer 900**;
4. back on the detail page, report the handover and watch the status advance on its own (the page
   polls while the case is non-terminal).

Confirm <http://localhost:8080/camunda> (admin/admin), <http://localhost:8080/swagger-ui.html> and
<http://localhost:8080/actuator/health> (status `UP`) all load.

## Run it in containers

The dev loop above runs the backend and frontend from source. To run the whole thing as containers —
**frontend + app + Postgres** in one command — build the backend image, then start the full-stack
compose. The rationale is in [ADR-0014](docs/adr/0014-build-and-deployment-approach.md).

```bash
# 1. build the backend OCI image (Spring buildpacks — no Dockerfile). Produces miravelo/app:1.0-SNAPSHOT
./gradlew :service:app:bootBuildImage

# 2. bring up frontend + app + Postgres (the frontend image is built by compose)
docker compose -f stack/docker-compose.full.yml up --build
```

Then open <http://localhost:8090> and run the smoke test above. An nginx layer serves the built SPA
and reverse-proxies `/api`, `/engine-rest`, `/camunda`, `/v3/api-docs` to the app, so it is the same
single-origin, no-CORS topology as the Vite dev proxy.

| What | URL |
|---|---|
| Frontend (SPA + reverse proxy) | <http://localhost:8090> |
| CIB seven Cockpit | <http://localhost:8090/camunda> (admin/admin) |
| Backend / engine REST · Swagger UI | <http://localhost:8080> · <http://localhost:8080/swagger-ui.html> |
| Actuator probes | 8080/actuator/health/{readiness,liveness} |

**Podman:** `bootBuildImage` needs a Docker-API socket. Expose podman's and point the build at it:

```bash
podman system service --time=0 unix:///tmp/podman.sock &
export DOCKER_HOST=unix:///tmp/podman.sock
./gradlew :service:app:bootBuildImage
```

**Configuration.** `application.yaml` ships dev defaults; the deploy-relevant values are read from the
environment (they win over the baked defaults). The compose file sets these for you:

| Env var | Purpose | Compose default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://postgres:5432/bikeleasing` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | `admin` / `admin` |
| `APP_IMAGE` | backend image tag compose runs | `miravelo/app:1.0-SNAPSHOT` |

> **Not production-hardened.** The image carries the example `jwtSecret` and admin/admin credentials
> from `application.yaml`. Override them (and the DB credentials) before running anywhere real. Schema
> is owned by Flyway and Hibernate only validates ([ADR-0013](docs/adr/0013-flyway-for-database-migrations.md)),
> so the Postgres volume persists across `down`/`up` — reset it with `docker compose -f
> stack/docker-compose.full.yml down -v`.

## Scripts

```bash
# backend
./gradlew build                         # arch + unit + process + model validation + spec export
./gradlew :service:app:pitest           # mutation score >= 80
./gradlew generateBpmnModels            # regenerate the typed process API after editing a .bpmn

# frontend
npm --prefix frontend run verify        # format, eslint, steiger (FSD), knip, tsc, vitest
npm --prefix frontend run api:generate  # regenerate the typed client from openapi.json
npm --prefix frontend run e2e           # Playwright specs (chromium)

# BPMN
npm --prefix tools run lint:bpmn        # bpmnlint the .bpmn models
```

## Ground rules

- **Start from an issue.** Every change traces back to one — open an issue (or pick an existing one)
  and agree on the approach *before* you write code, then reference it in the PR (`Closes #123`).
  This keeps substantial changes discussed up front and the history navigable.
- **Read [`AGENTS.md`](AGENTS.md) first.** It is the single source of guidance for humans and AI
  agents alike; [`frontend/AGENTS.md`](frontend/AGENTS.md) covers the React app's toolchain.
- **Conventional Commits.** Commit messages and PR titles follow
  [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`,
  `refactor:`, `test:`, `chore:`). Write everything in **English**.
- **Keep the gates green.** The architecture (ArchUnit + Konsist), contract-drift, mutation (≥ 80)
  and FSD gates run in CI on every PR. They are fitness functions, not style guides — a violation
  fails the build. The mutation gate is **diff-scoped** on PRs (only the classes you changed); the
  full-module gate-80 sweep runs nightly.
- **Add tests.** This is a TDD codebase; match the test style to the layer (see `AGENTS.md`).
  Mutation testing means a test that runs without asserting will fail CI.
- **Changing the API or the process?** Use the `sync-api-client` / `automate-process` skills so the
  committed `openapi/openapi.json` contract and the generated code stay in sync.
- **Changing the database schema?** Flyway owns it. Add a new forward-only migration
  `V{n}__description.sql` under `service/app/src/main/resources/db/migration/` in the same change as
  the entity edit — never edit an already-applied migration. Hibernate runs `validate`, so a mismatch
  fails startup. A dev database first created by the old `ddl-auto: create` has no Flyway history;
  reset it once with `docker compose -f stack/docker-compose.yml down -v` before running. See
  [ADR-0013](docs/adr/0013-flyway-for-database-migrations.md).

## Before opening a PR

```bash
./gradlew build
git diff --exit-code openapi/openapi.json    # the API contract must not drift
./gradlew :service:app:pitest                # mutation score >= 80
npm --prefix frontend run verify
npm --prefix frontend run api:check          # the generated client is in sync
```

All of these run in CI on every pull request (JDK 21 / Node ≥ 22.12). The `review-vertical-slice`
subagent is a good pre-flight check that a feature is wired end to end across the whole stack.

## Reporting bugs / requesting features

Open an issue. For a process- or contract-related bug, attaching the relevant `.bpmn` model or the
`openapi.json` diff is the fastest path to a fix.
