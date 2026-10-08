# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it (see [ADR-0005](docs/adr/0005-agents-md-as-the-single-source.md)).

## Project Overview

A **MiraVelo bike-leasing** example implemented against a **remote** Operaton engine: the BPMN process
the sibling engine blueprints implement, split across two Spring Boot apps and with an enforced
hexagonal architecture in the worker. There is **no frontend** — this is a headless
remote/external-task blueprint.

- **Engine host** (`service/engine-service`) — a generic, model-agnostic Operaton 2.1 engine host
  on **:8081** (`/engine-rest` + Cockpit/Tasklist). It deploys no model; it only hosts the in-engine
  execution/task listener beans. Package root `io.miragon.blueprint`.
- **Worker** (`service/example-service`) — Spring Boot 4, hexagonal, on **:8082**. It owns the domain,
  use cases and adapters, **owns and deploys** the BPMN/DMN/form models, and drives the remote engine
  through the generated REST client. Service tasks are `camunda:type="external"`; the worker
  subscribes to their topics (`bikeLeasing.<task>`) via external-task workers under
  `adapter/inbound/operaton`. Package root `io.miragon.blueprint`.
- **Generated engine client** (`service/common-operaton-client`) — a typed `/engine-rest` client
  generated from Operaton's official OpenAPI spec, pinned to the engine version so the two never
  drift. The worker uses it instead of hand-written HTTP calls.
- **Shared architecture tests** (`service/common-architecture-tests`) — the rule suite the worker wires
  in (see [ADR-0007](docs/adr/0007-two-architecture-test-tools-archunit-and-konsist.md)).
  <!-- variant:blueprint -->
- **Two equivalent variants on `main`** — `kotlin-gradle/` (Kotlin, **the recommended stack**) and
  `java-maven/` (Java 21, for teams bound to it and for trainings). Each is self-contained and
  carries its own copy of the process models, forms, migrations and `application.yaml`; the
  `Blueprint Checks` workflow fails when the `src/main/resources` trees differ, so **edit a model in
  one variant and copy it to the other**. The variants must stay functionally identical: **make every
  change in behaviour in both variants in the same PR.** Changes that only concern one language's
  idioms stay on that side. See [ADR-0014](docs/adr/0014-two-stack-variants-side-by-side-on-main.md).
  The OpenAPI contract below is drift-gated against **both** variants.
  Stack-specific content in shared files (docs, Dependabot, Conductor settings) is wrapped in
  `variant:<name>` markers so `scripts/create-starter.sh` can strip it — see
  [docs/starter.md](docs/starter.md).
  <!-- /variant:blueprint -->
- **The worker's own API** is `openapi/openapi.json`: springdoc generates it from the controllers, it
  is **committed and drift-gated** (a test regenerates it during the build). See
  [ADR-0003](docs/adr/0003-openapi-as-the-checked-in-contract.md).

## Repository Map

<!-- variant:kotlin-gradle variant:nested -->
- `kotlin-gradle/` — both apps in Kotlin, built with Gradle
  <!-- /variant:kotlin-gradle -->
  <!-- variant:java-maven variant:nested -->
- `java-maven/` — both apps in Java 21, built with Maven
  <!-- /variant:java-maven -->

The build:

```
service/
  common-architecture-tests/   reusable architecture rule suite
  common-operaton-client/      generated Operaton /engine-rest client
  engine-service/              Operaton engine host (:8081), hosts the in-engine listeners
  example-service/             the worker (:8082, hexagonal), owns and deploys the models
    adapter/inbound/rest        domain REST controllers + OpenAPI / problem-details config
    adapter/inbound/operaton    external-task workers (subscribe to the BPMN topics)
    adapter/outbound/engine     deploys the model + drives the remote engine via the generated client
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/…          simulated dealer / contract / insurance / notification adapters
    process                     generated *ProcessApi (bpmn-to-code)
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/{bpmn,dmn,forms}  the process models and Camunda Forms
    resources/db/migration      Flyway versioned schema migrations
```

Around it:

```
bruno/                         REST scenarios (happy-path / escalation / abort / not-solvent / …)
openapi/                       the checked-in, drift-gated OpenAPI contract (openapi.json)
docs/                          Architecture Decision Records + diagrams
stack/                         Postgres dev stack (docker compose)
.github/                       pre-merge + nightly pipelines + Dependabot
.githooks/                     pre-commit hook (bpmnlint on staged .bpmn)
scripts/create-starter.sh      strips the repo down to one variant (docs/starter.md)
package.json / .bpmnlintrc     root-level bpmnlint config + git-hook installer (npm run lint:bpmn)
```

## Development Setup

The remote topology is three processes — Postgres, then the engine host, then the worker (the worker
deploys its process into the engine at start-up, so the engine must be up first):

```bash
docker compose -f stack/docker-compose.yml up -d    # Postgres (creates bikeleasing_engine + bikeleasing_app)
```

<!-- variant:kotlin-gradle -->
```bash
cd kotlin-gradle && ./gradlew :service:engine-service:bootRun     # engine host + Cockpit on :8081
cd kotlin-gradle && ./gradlew :service:example-service:bootRun    # worker on :8082
```
<!-- /variant:kotlin-gradle -->
<!-- variant:java-maven -->
```bash
cd java-maven && ./mvnw -DskipTests install                       # once: the shared modules
cd java-maven && ./mvnw -pl service/engine-service spring-boot:run     # engine host + Cockpit on :8081
cd java-maven && ./mvnw -pl service/example-service spring-boot:run    # worker on :8082
```
<!-- /variant:java-maven -->

### Ports (one source of truth — keep README, this file and `.conductor/settings.toml` in sync)

| What | Port |
|---|---|
| Postgres (`bikeleasing_engine`, `bikeleasing_app`) | 5432 |
| Engine host (`/engine-rest`) | 8081 |
| Operaton Cockpit / webapps | 8081/operaton (admin/admin) |
| Worker REST · OpenAPI spec · Swagger UI | 8082/api · 8082/v3/api-docs · 8082/swagger-ui.html |
| Worker actuator (health/liveness/readiness · prometheus) | 8082/actuator |

Under Conductor the ports are fixed and the workspace runs `nonconcurrent`
(see [ADR-0006](docs/adr/0006-fixed-ports-for-v1-portless-as-the-upgrade.md)).

## Build Commands

<!-- variant:kotlin-gradle -->
| Area (run in `kotlin-gradle/`) | Command |
|---|---|
| Everything (arch + unit + process + model validation + spec export, all modules) | `./gradlew build` |
| Worker mutation testing (gate 80) | `./gradlew :service:example-service:pitest` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./gradlew generateBpmnModels` |
| Regenerate the worker's OpenAPI contract | `./gradlew :service:example-service:test --tests "io.miragon.blueprint.openapi.OpenApiSpecExportTest"` |
| Worker OCI image (`miravelo/example-service`) | `./gradlew :service:example-service:bootBuildImage` |
<!-- /variant:kotlin-gradle -->

<!-- variant:java-maven -->
| Area (run in `java-maven/`) | Command |
|---|---|
| Everything (arch + Checkstyle + unit + process + model validation + spec export, all modules) | `./mvnw verify` |
| Worker mutation testing (gate 80) | `./mvnw -pl service/example-service -am test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./mvnw -pl service/example-service generate-sources` |
| Regenerate the worker's OpenAPI contract | `./mvnw -pl service/example-service -am test -Dtest=OpenApiSpecExportTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Worker OCI image (`miravelo/example-service`) | `./mvnw -pl service/example-service -am -DskipTests spring-boot:build-image` |
<!-- /variant:java-maven -->

| Area (repo root) | Command |
|---|---|
| Verify the OpenAPI contract after regenerating it | `git diff --exit-code openapi/openapi.json` |
| End-to-end scenarios against a running stack (Bruno) | `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r` — **pin the CLI version** (sandbox capabilities shift between majors; see [ADR-0012](docs/adr/0012-polling-for-eventual-consistency-in-e2e-tests.md)) |
| BPMN lint | `npm run lint:bpmn` |

The OCI image decision is [ADR-0011](docs/adr/0011-build-and-deployment-approach.md); the how-to is
CONTRIBUTING "Run it in containers".

## Architecture — the rules are machine-enforced

The worker's hexagonal rules live in `service/common-architecture-tests` (ArchUnit, plus a
source-level tool for the rules bytecode cannot express) and **fail the build**. Read
`HexagonalArchitectureTest` and `NamingConventionArchitectureTest` before writing code. The hard rules:

- **One inbound port per controller/worker.** `onlyFulfilOneUseCase` counts constructor params in
  `application.port.inbound` and fails at >1.
- **No new top-level `config` package** in the worker. The containment rule ignores only *direct*
  members of the root package, so `io.miragon.blueprint.config` would fail. Cross-cutting
  `@Configuration` (CORS, OpenAPI, error handling) goes in `adapter.inbound.rest` — the
  `Configuration` suffix is whitelisted there.
- **The `process` package is generated.** Never hand-edit `*ProcessApi` or the shared
  `ServiceTasks`/`Messages`/`ProcessVariables`/`Errors`/`Escalations` files; edit the `.bpmn` and
  regenerate.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.outbound` `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.
- **External-task workers** live under `adapter/inbound/operaton`, subscribe by topic, and extend
  `BaseExternalTaskWorker`. They are inbound adapters — the same one-use-case rule applies.

## BPMN Quality Gates

- The worker **owns** the `.bpmn`/`.dmn`/`.form` models under
  `service/example-service/src/main/resources` and deploys them into the remote engine at start-up.
- `bpmn-to-code` generates typed process constants from the models. Since bpmn-to-code 6 the API is
  node-centric: `<Process>ProcessApi.FlowNodes.<Node>` carries the element (`ELEMENT_ID`), its
  `Variables` and its successors, and topics, messages and variable names live in shared files
  (`ServiceTasks`, `Messages`, `ProcessVariables`). Process tests assert the walked path as a
  compile-checked path instead of hand-maintained element-id lists.
- `bpmn-to-code-testing` validates the models structurally at build time — including a custom rule
  that every service task must be an **external task with a topic**.
- `bpmnlint` runs on staged `.bpmn` via `.githooks/pre-commit` (install: `npm run hooks:install`).

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | unit tests with mocked ports |
| `adapter.inbound.rest` | `@WebMvcTest` with the use case mocked |
| `adapter.inbound.operaton` (external-task workers) | direct unit tests with the use case mocked |
| `adapter.outbound.db` | `@DataJpaTest` |
| `adapter.outbound.engine` (remote client) | against the wire format or the mocked generated client |
| process end-to-end | Operaton process tests (`operaton-bpm-assert`, in-memory engine) |

**Mutation testing gates PRs at 80**: a test that executes without asserting will fail CI. Coverage
says a line ran; mutation says a test would have noticed. The PR gate runs **diff-scoped** (only the
classes the PR changed, still blocking); the **full-module** gate-80 sweep runs nightly. See
[ADR-0004](docs/adr/0004-mutation-testing-as-a-blocking-pr-gate.md).

## Verify After Each Task (targeted, not a full build)

<!-- variant:blueprint -->
Verify the variant you touched — which, for a change in behaviour, is both.
<!-- /variant:blueprint -->

<!-- variant:kotlin-gradle -->
In `kotlin-gradle/`:

- Worker service/controller: `./gradlew :service:example-service:test --tests "*<Name>Test"`
- Architecture only: `./gradlew :service:example-service:test --tests "io.miragon.blueprint.architecture.*"`
  <!-- /variant:kotlin-gradle -->

<!-- variant:java-maven -->
In `java-maven/`:

- Worker service/controller: `./mvnw -pl service/example-service -am test -Dtest="<Name>Test" -Dsurefire.failIfNoSpecifiedTests=false`
- Architecture only: `./mvnw -pl service/example-service -am test -Dtest="ArchitectureTest" -Dsurefire.failIfNoSpecifiedTests=false`
  <!-- /variant:java-maven -->

From the repo root:

- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`
- Process changed: regenerate the process API, then the `process.*` tests

## Working with GitHub

Use the `gh` CLI. Write everything (issues, PRs, commit messages) in **English**. Use
**Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `ci:`, `build:`).

## ADRs

Architecture decisions are recorded in `docs/adr/`. Read them to understand *why* the repo is shaped
this way before proposing structural changes.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed
doesn't look right, suggest corrections, ask critical questions, and push back where needed.
Challenge ideas that could benefit from further improvement or iterative refinement rather than just
accepting them at face value.
