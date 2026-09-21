# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it.

## Project Overview

A headless **MiraVelo bike-leasing** example: one complete BPMN process automated on an embedded
Operaton engine, with an enforced hexagonal architecture and a committed REST contract. Headless —
there is no UI; the engine's Cockpit/Tasklist webapps and the REST API are the only entry points.

- **Backend** (`service/app`) — Java 21 / Spring Boot 4, hexagonal, Operaton 2.x embedded engine
  (JavaDelegates). Package root `io.miragon.blueprint`.
- **The REST contract** is `openapi/openapi.json`: springdoc generates it from the controllers, and it
  is **committed and drift-gated** in CI. RFC-7807 problem details are enabled. See ADR-0003.

## Development Setup

Two commands to a running stack:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres + EnterpriseGlue The Bridge
mvn -pl service/app spring-boot:run                 # backend + engine on :8080
```

The stack also starts **EnterpriseGlue The Bridge** (an additional Operaton UI on `:8081`); register
the engine in it under Platform Settings → Engines with base URL
`http://host.docker.internal:8080/engine-rest`.

### Ports (one source of truth — keep README, this file and `.conductor/settings.toml` in sync)

| What | Port |
|---|---|
| Postgres | 5432 |
| Backend (REST + engine-rest) | 8080 |
| Operaton Cockpit / webapps | 8080/operaton (admin/admin) |
| OpenAPI spec · Swagger UI | 8080/v3/api-docs · 8080/swagger-ui.html |
| Actuator (health/liveness/readiness · prometheus) | 8080/actuator |
| EnterpriseGlue The Bridge (additional UI) | 8081 (admin@enterpriseglue.com/adminadmin) |

## Build Commands

| Area | Command |
|---|---|
| Backend (arch + unit + process + model validation + spec export) | `mvn verify` |
| Mutation testing (gate 80) | `mvn -pl service/app -am test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `mvn -pl service/app generate-sources` |
| Regenerate + verify the OpenAPI contract | `mvn -pl service/app test -Dtest=OpenApiSpecExportTest` then `git diff --exit-code openapi/openapi.json` |
| API scenarios (running stack) | `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r` |
| BPMN lint | `npm run lint:bpmn` |
| Backend OCI image | `mvn -pl service/app spring-boot:build-image` (image `miravelo/app`) — [ADR-0011](docs/adr/0011-build-and-deployment-approach.md), CONTRIBUTING "Run it in containers" |

## Architecture — the rules are machine-enforced

The backend's hexagonal rules live in `service/common-architecture-tests` (ArchUnit) and
**fail the build**. Read `HexagonalArchitectureTest.java` and `NamingConventionArchitectureTest.java`
before writing code (see ADR-0007). The two source-shape rules ArchUnit can't see — one top-level
type per file, no wildcard imports (except `java.util`) — are enforced by the
**maven-checkstyle-plugin** (`config/checkstyle/checkstyle.xml`), which also fails the build. The
hard rules:

- **One inbound port per controller.** `onlyFulfilOneUseCase` counts constructor params in
  `application.port.inbound` and fails at >1. An inbox listing + a completion are two controllers.
- **No new top-level `config` package.** The containment rule ignores only *direct* members of the
  root package, so `io.miragon.blueprint.config` would fail. Cross-cutting `@Configuration` (CORS,
  OpenAPI, error handling) goes in `adapter.inbound.rest` — the `Configuration` suffix is whitelisted
  there.
- **`adapter/process` is generated.** Never hand-edit `*ProcessApi.java`; edit the `.bpmn` and re-run
  `mvn -pl service/app generate-sources`.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.outbound` `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.

## BPMN Quality Gates

- `bpmn-to-code` generates typed process constants from the models at build time; a custom model
  test requires every service task to use a delegate expression (`#{beanName}`).
- `bpmnlint` runs on staged `.bpmn` via the pre-commit hook (install: `npm run hooks:install`).

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | Mockito unit tests (mock the ports) |
| `adapter.inbound.rest` | `@WebMvcTest` + `@MockitoBean` |
| `adapter.outbound.db` | `@DataJpaTest` |
| process end-to-end | Operaton process tests (JUnit 5 + operaton-bpm-assert) |

**Mutation testing gates PRs at 80** (`pitest-maven`): a test that executes without asserting
will fail CI. Coverage says a line ran; mutation says a test would have noticed. The PR gate runs
**diff-scoped** (only the classes the PR changed, still blocking); the **full-module** gate-80 sweep
runs nightly. See ADR-0004.

## Verify After Each Task (targeted, not a full build)

- Backend service/controller: `mvn -pl service/app test -Dtest='*<Name>Test'`
- Architecture only: `mvn -pl service/app test -Dtest="io.miragon.blueprint.architecture.*"`
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
</content>
</invoke>
