# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it.

## Project Overview

A **MiraVelo bike-leasing** example implemented against a **remote** CIB seven engine: the same BPMN
process the sibling engine blueprints implement, split across two Spring Boot apps and with an
enforced hexagonal architecture in the worker. There is **no frontend** — this is a headless
remote/external-task blueprint.

- **Engine host** (`service/engine-service`) — a generic, model-agnostic CIB seven 2.2.0 engine host
  on **:8081** (`/engine-rest` + Cockpit/Tasklist). It deploys no model; it only hosts the in-engine
  execution/task listener beans. Package root `io.miragon.blueprint`.
- **Worker** (`service/example-service`) — Kotlin / Spring Boot 4, hexagonal, on **:8082**. It owns
  the domain, use cases and adapters, **owns and deploys** the BPMN/DMN/form models, and drives the
  remote engine through the generated REST client. Service tasks are `camunda:type="external"`; the
  worker subscribes to their topics (`bikeLeasing.<task>`) via external-task workers under
  `adapter/inbound/cibseven`. Package root `io.miragon.blueprint`.
- **Generated engine client** (`service/common-cibseven-client`) — a typed `/engine-rest` client
  generated from CIB seven's official OpenAPI spec, pinned to the engine version so the two never
  drift. The worker uses it instead of hand-written HTTP calls.
- **Shared architecture tests** (`service/common-architecture-tests`) — the ArchUnit + Konsist rules
  the worker wires in.
- **The worker's own API** is `openapi/openapi.json`: springdoc generates it from the controllers, it
  is **committed and drift-gated** (a test regenerates it during `./gradlew build`). See ADR-0003.

## Development Setup

The remote topology is three processes — Postgres, then the engine host, then the worker (the worker
deploys its process into the engine at start-up, so the engine must be up first):

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres (creates bikeleasing_engine + bikeleasing_app)
./gradlew :service:engine-service:bootRun           # engine host + Cockpit on :8081
./gradlew :service:example-service:bootRun          # worker (REST + external-task workers) on :8082
```

### Ports (one source of truth — keep README, this file and `.conductor/settings.toml` in sync)

| What | Port |
|---|---|
| Postgres (`bikeleasing_engine`, `bikeleasing_app`) | 5432 |
| Engine host (`/engine-rest`) | 8081 |
| CIB seven Cockpit / webapps | 8081/camunda (admin/admin) |
| Worker REST · OpenAPI spec · Swagger UI | 8082/api · 8082/v3/api-docs · 8082/swagger-ui.html |
| Worker actuator (health/liveness/readiness · prometheus) | 8082/actuator |

## Build Commands

| Area | Command |
|---|---|
| Everything (arch + unit + process + model validation + spec export, all modules) | `./gradlew build` |
| Worker mutation testing (gate 80) | `./gradlew :service:example-service:pitest` |
| Regenerate + verify the worker's OpenAPI contract | `./gradlew :service:example-service:test --tests "io.miragon.blueprint.openapi.OpenApiSpecExportTest"` then `git diff --exit-code openapi/openapi.json` |
| BPMN lint | `npm run lint:bpmn` (from the repo root) |
| Worker OCI image | `./gradlew :service:example-service:bootBuildImage` — produces `miravelo/example-service:<version>`; see [ADR-0011](docs/adr/0011-build-and-deployment-approach.md) and CONTRIBUTING "Run it in containers" |

## Architecture — the rules are machine-enforced

The worker's hexagonal rules live in `service/common-architecture-tests` (ArchUnit + Konsist) and
**fail the build** — one line wires them into the worker (`class ArchitectureTest :
ServiceArchitectureTest(...)`). Read `HexagonalArchitectureTest.kt` and
`NamingConventionArchitectureTest.kt` before writing code. The hard rules:

- **One inbound port per controller/worker.** `onlyFulfilOneUseCase` counts constructor params in
  `application.port.inbound` and fails at >1.
- **No new top-level `config` package** in the worker. The containment rule ignores only *direct*
  members of the root package, so `io.miragon.blueprint.config` would fail. Cross-cutting
  `@Configuration` (CORS, OpenAPI, error handling) goes in `adapter.inbound.rest` — the
  `Configuration` suffix is whitelisted there.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.outbound` `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.
- **External-task workers** live under `adapter/inbound/cibseven`, subscribe by topic, and extend
  `BaseExternalTaskWorker`. They are inbound adapters — the same one-use-case rule applies.

## BPMN Quality Gates

- The worker **owns** the `.bpmn`/`.dmn`/`.form` models under
  `service/example-service/src/main/resources` and deploys them into the remote engine at start-up.
- `bpmn-to-code-testing` validates the models structurally at build time — including a custom rule
  that every service task must be an **external task with a topic**.
- `bpmnlint` runs on staged `.bpmn` via `.githooks/pre-commit` (install: `npm run hooks:install`).

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | mockk unit tests (mock the ports) |
| `adapter.inbound.rest` | `@WebMvcTest` + MockkBean |
| `adapter.inbound.cibseven` (external-task workers) | direct mockk unit tests |
| `adapter.outbound.db` | `@DataJpaTest` |
| `adapter.outbound.engine` (remote client) | `MockRestServiceServer` |
| process end-to-end | CIB seven process tests (`cibseven-bpm-assert`, in-memory engine) |

**Mutation testing gates PRs at 80** (`:service:example-service:pitest`): a test that executes
without asserting will fail CI. Coverage says a line ran; mutation says a test would have noticed.
See ADR-0004.

## Verify After Each Task (targeted, not a full build)

- Worker service/controller: `./gradlew :service:example-service:test --tests "*<Name>Test"`
- Architecture only: `./gradlew :service:example-service:test --tests "io.miragon.blueprint.architecture.*"`
- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`

## Working with GitHub

Use the `gh` CLI. Write everything (issues, PRs, commit messages) in **English**. Use
**Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `ci:`, `build:`).

## ADRs

Architecture decisions are recorded in `docs/adr/` (0001–0011). Read them to understand *why* the
repo is shaped this way before proposing structural changes.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed
doesn't look right, suggest corrections, ask critical questions, and push back where needed.
Challenge ideas that could benefit from further improvement or iterative refinement rather than just
accepting them at face value.
