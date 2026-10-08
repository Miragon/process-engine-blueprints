# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it.

## Project Overview

A headless **MiraVelo bike-leasing** example: the same BPMN process the sibling engine blueprints
implement, on Camunda 8 / Zeebe, with an enforced hexagonal architecture and a REST API — no frontend.

- **Backend** (`service/app`) — Spring Boot 4, hexagonal, Camunda 8 / Zeebe 8.9 engine
  bound through **VanillaBP** (`@WorkflowTask`, `ProcessService`) — this branch is the spike written
  up in `docs/vanillabp-spike.md`. Package root `io.miragon.blueprint`.
  <!-- variant:blueprint -->
- **Two equivalent variants on `main`** — `kotlin-gradle/` (Kotlin, **the recommended stack**) and
  `java-maven/` (Java 21, for teams bound to it and for trainings). Each is self-contained and
  carries its own copy of the process models, migrations and `application.yaml`; the
  `Blueprint Checks` workflow fails when the two `src/main/resources` trees differ, so **edit a model in
  one variant and copy it to the other**. The variants must stay functionally identical: **make every
  change in behaviour in both variants in the same PR.** Changes that only concern one language's
  idioms stay on that side. See [ADR-0013](docs/adr/0013-two-stack-variants-side-by-side-on-main.md).
  The OpenAPI contract below is drift-gated against **both** variants.
  Stack-specific content in shared files (docs, Dependabot, Conductor settings) is wrapped in
  `variant:<name>` markers so `scripts/create-starter.sh` can strip it — see
  [docs/starter.md](docs/starter.md).
  <!-- /variant:blueprint -->
- **The REST surface** is described by `openapi/openapi.json`: springdoc generates it from the
  controllers, and it is **committed and drift-gated** so the checked-in contract can never lie about
  the code. See ADR-0003.

## Development Setup

Two commands to a running stack — Camunda 8 (Zeebe + Operate/Tasklist) with Postgres, then the backend
on :8081, which connects to Zeebe on :26500:

```bash
docker compose -f stack/docker-compose.yml up -d
```

<!-- variant:kotlin-gradle -->
```bash
cd kotlin-gradle && ./gradlew :service:app:bootRun
```
<!-- /variant:kotlin-gradle -->
<!-- variant:java-maven -->
```bash
cd java-maven && ./mvnw -DskipTests install && ./mvnw -pl service/app spring-boot:run
```
<!-- /variant:java-maven -->

### Ports (one source of truth — keep README, this file, `.conductor/settings.toml` in sync)

| What | Port |
|---|---|
| Postgres (app read-model DB) | 5432 |
| Backend REST | 8081 |
| Zeebe gRPC · REST | 26500 · 8080/v2 |
| Camunda 8 web apps (Operate / Tasklist) | 8080 |
| OpenAPI spec · Swagger UI | 8081/v3/api-docs · 8081/swagger-ui.html |
| Actuator (health/liveness/readiness · prometheus) | 8081/actuator |

## Build Commands

<!-- variant:kotlin-gradle -->
| Area (run in `kotlin-gradle/`) | Command |
|---|---|
| Backend (arch + unit + process + model validation + spec export) | `./gradlew build` |
| Mutation testing (gate 80) | `./gradlew :service:app:pitest` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./gradlew generateBpmnModels` |
| Regenerate the OpenAPI contract | `./gradlew :service:app:test --tests "io.miragon.blueprint.openapi.OpenApiSpecExportTest"` |
| Backend OCI image (`miravelo/zeebe-example`) | `./gradlew :service:app:bootBuildImage` |
<!-- /variant:kotlin-gradle -->

<!-- variant:java-maven -->
| Area (run in `java-maven/`) | Command |
|---|---|
| Backend (arch + Checkstyle + unit + process + model validation + spec export) | `./mvnw verify` |
| Mutation testing (gate 80) | `./mvnw -pl service/app -am test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./mvnw -pl service/app generate-sources` |
| Regenerate the OpenAPI contract | `./mvnw -pl service/app -am test -Dtest=OpenApiSpecExportTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Backend OCI image (`miravelo/zeebe-example`) | `./mvnw -pl service/app -am -DskipTests spring-boot:build-image` |
<!-- /variant:java-maven -->

| Area (repo root) | Command |
|---|---|
| Verify the OpenAPI contract after regenerating it | `git diff --exit-code openapi/openapi.json` |
| API scenarios (running stack) | `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r` |
| BPMN lint | `npm run lint:bpmn` |

The OCI image decision is [ADR-0011](docs/adr/0011-build-and-deployment-approach.md); the how-to is
CONTRIBUTING "Run it in containers".

## Architecture — the rules are machine-enforced

The backend's hexagonal rules live in `service/common-architecture-tests` (ArchUnit, plus a
source-level tool for the rules bytecode cannot express) and **fail the build** (see
[ADR-0007](docs/adr/0007-two-architecture-test-tools-archunit-and-konsist.md)). Read `HexagonalArchitectureTest` and `NamingConventionArchitectureTest` before
writing code. The hard rules:

- **One inbound port per controller.** `onlyFulfilOneUseCase` counts constructor params in
  `application.port.inbound` and fails at >1. An inbox listing + a completion are two controllers.
- **No new top-level `config` package.** The containment rule ignores only *direct* members of the
  root package, so `io.miragon.blueprint.config` would fail. Cross-cutting `@Configuration` (CORS,
  OpenAPI, error handling) goes in `adapter.inbound.rest` — the `Configuration` suffix is whitelisted
  there.
- **`adapter/process` holds the generated process API and the workflow aggregate.** Never hand-edit
  `*ProcessApi` or the shared `ServiceTasks`/`Messages`/`ProcessVariables`/`Errors`/`Escalations`
  files; edit the `.bpmn` and regenerate. `LeasingApplicationAggregate` is
  hand-written: it is the VanillaBP workflow aggregate the workflow tasks and the persistence share.
- **No engine API in the service.** Production code must not depend on `io.camunda` (or any other
  engine API); the VanillaBP SPI is only allowed in `adapter.inbound.workflow` and
  `adapter.outbound.workflow`. The Camunda client stays on the 8.8 line the VanillaBP adapter needs.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.inbound.workflow` `Task|Workflow`; `adapter.outbound` `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.

## BPMN Quality Gates

- `bpmn-to-code` generates typed process constants from the models at build time; the
  `BikeLeasingModelValidationTest` (`BpmnRules.all()` for Zeebe) validates the models' structure.
- Since bpmn-to-code 6 the API is node-centric: `<Process>ProcessApi.FlowNodes.<Node>` carries the
  element (`.id`, `ELEMENT_ID`), its `Variables` and its successors (`Next`). Process tests assert
  the walked path as a compile-checked walk over that tree instead of hand-maintained element-id
  lists.
- `bpmnlint` (`bpmnlint:recommended` + `camunda-compat/camunda-cloud-8-9` + `@miragon/rules/all`)
  runs on staged `.bpmn` via `.githooks/pre-commit` (install: `npm run hooks:install`). Element ids
  follow the `flow_`/`event_`/`gateway_`/`serviceTask_` conventions the rules enforce.

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | unit tests with mocked ports |
| `adapter.inbound.rest` | `@WebMvcTest` with the use case mocked |
| `adapter.inbound.workflow` | plain unit tests on a `LeasingApplicationAggregate` (mock the use case) |
| `adapter.outbound.db` | Spring Data JPA slice tests |
| process end-to-end | Camunda 8 process tests (`@CamundaSpringProcessTest` + `WorkflowDeploymentTestConfiguration`, in-container Zeebe), paths asserted via `ProcessPath` |

**Mutation testing gates PRs at 80**: a test that executes without asserting
will fail CI. Coverage says a line ran; mutation says a test would have noticed. The PR gate runs
**diff-scoped** (only the classes the PR changed, still blocking); the **full-module** gate-80 sweep
runs nightly. See ADR-0004.

## Verify After Each Task (targeted, not a full build)

<!-- variant:blueprint -->
Verify the variant you touched — which, for a change in behaviour, is both.
<!-- /variant:blueprint -->

<!-- variant:kotlin-gradle -->
In `kotlin-gradle/`:

- Backend service/controller: `./gradlew :service:app:test --tests "*<Name>Test"`
- Architecture only: `./gradlew :service:app:test --tests "io.miragon.blueprint.architecture.*"`
  <!-- /variant:kotlin-gradle -->

<!-- variant:java-maven -->
In `java-maven/`:

- Backend service/controller: `./mvnw -pl service/app -am test -Dtest="<Name>Test" -Dsurefire.failIfNoSpecifiedTests=false`
- Architecture only: `./mvnw -pl service/app -am test -Dtest="ArchitectureTest" -Dsurefire.failIfNoSpecifiedTests=false`
  <!-- /variant:java-maven -->

From the repo root:

- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`

## Working with GitHub

Use the `gh` CLI. Write everything (issues, PRs, commit messages) in **English**. Use
**Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `ci:`, `build:`).

## ADRs

Architecture decisions are recorded in `docs/adr/`. Read them to understand *why* the
repo is shaped this way before proposing structural changes.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed
doesn't look right, suggest corrections, ask critical questions, and push back where needed.
Challenge ideas that could benefit from further improvement or iterative refinement rather than just
accepting them at face value.
