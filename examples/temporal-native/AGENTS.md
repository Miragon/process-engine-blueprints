# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it.

## Project Overview

A headless **MiraVelo bike-leasing** example marked **beta**: the flow the sibling blueprints model in
BPMN, written as Temporal workflows in Kotlin, with an enforced hexagonal architecture and a REST API —
no frontend. There is no process model.

- **Backend** — Spring Boot 4, hexagonal, Temporal 1.40 with `@WorkflowImpl` and `@ActivityImpl`. Package
  root `io.miragon.blueprint`.
- **Kotlin only.** `blueprint.yaml` states why there is no Java variant.
- **Core shared with `zeebe-native`.** `domain/`, `application/`, their tests, `V1__init.sql` and
  `openapi/openapi.json` are byte-identical; the monorepo's `drift.yml` enforces it. A change there goes to
  both examples, so keep KDocs engine-neutral.
- **The REST surface** is described by `openapi/openapi.json`: springdoc generates it from the
  controllers, and it is **committed and drift-gated**. See
  [ADR-0003](docs/adr/0003-openapi-as-the-checked-in-contract.md).
- **BPMN to Temporal:** the concept mapping and the deliberate differences are in
  [ADR-0014](docs/adr/0014-temporal-as-a-beta-next-to-the-bpmn-engines.md).

## Repository Map

```
kotlin-gradle/service/
  common-architecture-tests/   reusable architecture rule suite
  common-temporal/             Temporal glue: connection defaults, Kotlin data converter
  common-temporal-test/        helpers for process tests on the test server
  app/                         the bike-leasing service (hexagonal)
    adapter/inbound/rest        domain REST controllers + OpenAPI / CORS / problem-details config
    adapter/inbound/temporal    one *Activity per use case
    adapter/outbound/temporal   starts, signals and updates the workflow + task inbox
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/…          simulated dealer / contract / insurance / notification adapters
    adapter/process             workflows, activity contracts (<Process>Activities.kt), payloads, credit rule
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/db/migration      Flyway versioned schema migrations
```

Around it:

```
bruno/                         REST scenarios (happy-path / abort / not-solvent / …)
openapi/                       the checked-in, drift-gated OpenAPI contract (openapi.json)
docs/                          Architecture Decision Records
stack/                         Temporal dev server + Postgres (docker compose)
.github/                       pre-merge + nightly pipelines + Dependabot
.claude/                       Claude Code skills, review agent
scripts/create-starter.sh      flattens the example into a plain project (runs on export)
```

### Left out on purpose

The BPMN examples carry these; they have nothing to work on here:

| Left out | Why |
|---|---|
| `package.json`, `.bpmnlintrc`, `.claude/hooks/lint-bpmn.sh`, `.githooks/pre-commit` | bpmnlint and bpmn-cli need a model |
| `designiq.yml` | designIQ opens process models |
| agent `review-process`, bpmn plugins in `.claude/settings.json` | they audit models against their glue |
| bpmn-to-code | it generates the process API from a model |
| `bruno/06-incident-demo`, `bruno/10-alternative-via-tasklist` | no incidents and no Tasklist in Temporal |

## Development Setup

Two commands to a running stack — Temporal and Postgres, then the backend on :8081, which connects to
Temporal on :7233:

```bash
docker compose -f stack/docker-compose.yml up -d
```

```bash
cd kotlin-gradle && ./gradlew :service:app:bootRun
```

### Ports (one source of truth — keep README, this file, `.conductor/settings.toml` in sync)

| What | Port |
|---|---|
| Postgres (app read-model DB) | 5432 |
| Backend REST | 8081 |
| Temporal gRPC | 7233 |
| Temporal UI | 8233 |
| OpenAPI spec · Swagger UI | 8081/v3/api-docs · 8081/swagger-ui.html |
| Actuator (health/liveness/readiness · prometheus) | 8081/actuator |

## Build Commands

| Area (run in `kotlin-gradle/`) | Command |
|---|---|
| Backend (arch + unit + process + spec export) | `./gradlew build` |
| Mutation testing (gate 80) | `./gradlew :service:app:pitest` |
| Regenerate the OpenAPI contract | `./gradlew :service:app:test --tests "io.miragon.blueprint.adapter.inbound.rest.OpenApiSpecExportTest"` |
| Backend OCI image | `./gradlew :service:app:bootBuildImage` |

| Area (repo root) | Command |
|---|---|
| Verify the OpenAPI contract after regenerating it | `git diff --exit-code openapi/openapi.json` |
| API scenarios (running stack) | `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r --exclude-tags=demo-data` |

The OCI image decision is [ADR-0011](docs/adr/0011-build-and-deployment-approach.md); the how-to is
CONTRIBUTING "Run it in containers".

## Architecture — the rules are machine-enforced

The backend's hexagonal rules live in `service/common-architecture-tests` (ArchUnit, plus a
source-level tool for the rules bytecode cannot express) and **fail the build**. Read
`HexagonalArchitectureTest` and `NamingConventionArchitectureTest` before writing code. The hard rules:

- **One inbound port per controller or activity.** `onlyFulfilOneUseCase` counts constructor params in
  `application.port.inbound` and fails at >1.
- **`adapter/process` stays deterministic.** It may depend on Temporal, Kotlin, Java and itself only.
  Time, randomness, I/O and Spring beans belong in an activity.
- **No new top-level `config` package.** The containment rule ignores only *direct* members of the
  root package, so `io.miragon.blueprint.config` would fail. Cross-cutting `@Configuration` (CORS,
  OpenAPI, error handling) goes in `adapter.inbound.rest` — the `Configuration` suffix is whitelisted
  there. Engine wiring belongs in `common-temporal`.
- **The workflow owns the order of steps.** An activity calls one use case and decides nothing.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.inbound.temporal` `Activity`; `adapter.outbound` `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.

## Changing a workflow

A running case replays its history against the current code. Reordering, adding or removing an activity
or timer breaks replay of running cases unless it is guarded with `Workflow.getVersion`. The dev server
keeps nothing across a restart, so this only bites on a real cluster.

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | unit tests with mocked ports |
| `adapter.inbound.rest` | `@WebMvcTest` with the use case mocked |
| `adapter.inbound.temporal` | plain unit tests with the use case mocked |
| `adapter.outbound.db` | Spring Data JPA slice tests |
| process end-to-end | `BikeLeasingProcessTest` on the in-memory test server: real activities and process adapter, use cases mocked, one test per branch |

**Mutation testing gates the domain at 80, nightly**: a test that executes without asserting will fail the nightly run. It mutates
`io.miragon.blueprint.domain.*` only and does not block pull requests.
See ADR-0004.

## Verify After Each Task (targeted, not a full build)

In `kotlin-gradle/`:

- Backend service/controller: `./gradlew :service:app:test --tests "*<Name>Test"`
- Architecture only: `./gradlew :service:app:test --tests "io.miragon.blueprint.ArchitectureTest*"`
- Workflow changed: `./gradlew :service:app:test --tests "io.miragon.blueprint.adapter.process.*"`

From the repo root:

- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`

## Agent Tooling

Diagnose a running case in the Temporal UI (<http://localhost:8233>): the workflow id is the application
id, the child workflow is `<applicationId>-cancel-order`. Moving a case on (signalling a workflow) is a
business action, taken by a client of the app's API or by a human, not by an agent.

Skills and agents live in `.claude/` (see [.claude/README.md](.claude/README.md)); other agents can read
them as recipes:

| Use | When |
|---|---|
| skill `implement-issue` | take a GitHub issue from plan (with approval) to a verified change |
| skill `review-change` | review a diff or PR, findings by severity |
| skill `create-adr` | record a decision in `docs/adr/` |
| skills `create-rest-controller`, `create-persistence-adapter` | one building block of a layer, with its production and test pattern |
| agent `review-vertical-slice` | read-only walk of one feature from the workflow step to the Bruno scenario |

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
