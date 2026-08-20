# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it. A nested `frontend/AGENTS.md` covers the React app's different toolchain.

## Project Overview

A full end-to-end **MiraVelo bike-leasing** example: the same BPMN process the sibling engine
blueprints implement, plus a UI and an enforced architecture on both sides of the stack.

- **Backend** (`service/app`) — Kotlin / Spring Boot 4, hexagonal, CIB seven 2.2.0 embedded engine
  (JavaDelegates). Package root `io.miragon.blueprint`.
- **Frontend** (`frontend/`) — React 19, Feature-Sliced Design, Tailwind v4, TanStack Router/Query.
  npm-only, deliberately outside the Gradle build (ADR-0005).
- **The seam between them** is `openapi/openapi.json`: springdoc generates it from the controllers,
  it is **committed and drift-gated**, and orval regenerates the frontend's typed client from it.
  A backend REST change reaches the browser via the `sync-api-client` skill. See ADR-0004.

## Development Setup

Three commands to a running stack:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres
./gradlew :service:app:bootRun                      # backend + engine on :8080
npm --prefix frontend run dev                       # UI on :5173 (proxies /api to :8080)
```

### Ports (one source of truth — keep README, this file, `.conductor/settings.toml`, `playwright.config.ts` in sync)

| What | Port |
|---|---|
| Postgres | 5432 |
| Backend (REST + engine-rest) | 8080 |
| CIB seven Cockpit / webapps | 8080/camunda (admin/admin) |
| OpenAPI spec · Swagger UI | 8080/v3/api-docs · 8080/swagger-ui.html |
| Actuator (health/liveness/readiness · prometheus) | 8080/actuator |
| Vite dev server | 5173 |

## Build Commands

| Area | Command |
|---|---|
| Backend (arch + unit + process + model validation + spec export) | `./gradlew build` |
| Mutation testing (gate 80) | `./gradlew :service:app:pitest` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./gradlew generateBpmnModels` |
| Regenerate + verify the OpenAPI contract | `./gradlew :service:app:test --tests "io.miragon.blueprint.openapi.OpenApiSpecExportTest"` then `git diff --exit-code openapi/openapi.json` |
| Frontend everything | `npm --prefix frontend run verify` |
| Regenerate the API client | `npm --prefix frontend run api:generate` (check: `api:check`) |
| API scenarios (running stack) | `cd bruno && npx --yes @usebruno/cli run . --env local -r` |
| Browser e2e (running stack) | `npm --prefix frontend run e2e` |
| BPMN lint | `npm --prefix tools run lint:bpmn` |
| Backend OCI image · full-stack run | `./gradlew :service:app:bootBuildImage` · `docker compose -f stack/docker-compose.full.yml up` — [ADR-0014](docs/adr/0014-build-and-deployment-approach.md), CONTRIBUTING "Run it in containers" |

## Architecture — the rules are machine-enforced

The backend's hexagonal rules live in `service/common-architecture-tests` (ArchUnit + Konsist) and
**fail the build**. Read `HexagonalArchitectureTest.kt` and `NamingConventionArchitectureTest.kt`
before writing code. The hard rules:

- **One inbound port per controller.** `onlyFulfilOneUseCase` counts constructor params in
  `application.port.inbound` and fails at >1. An inbox listing + a completion are two controllers.
- **No new top-level `config` package.** The containment rule ignores only *direct* members of the
  root package, so `io.miragon.blueprint.config` would fail. Cross-cutting `@Configuration` (CORS,
  OpenAPI, error handling) goes in `adapter.inbound.rest` — the `Configuration` suffix is whitelisted
  there.
- **`adapter/process` is generated.** Never hand-edit `*ProcessApi.kt`; edit the `.bpmn` and re-run
  `generateBpmnModels`.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.outbound` `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.

The frontend's Feature-Sliced Design is enforced by steiger (`lint:fsd`) + ESLint import rules +
knip (`lint:deadcode`). See `frontend/AGENTS.md`. Key rule: **`src/processes/` is banned** — in this
codebase "process" means the BPMN model, so the step rail is `widgets/leasing-progress`.

## BPMN Quality Gates

- `bpmn-to-code` generates typed process constants from the models at build time; a custom model
  test requires every service task to use a delegate expression (`#{beanName}`).
- `bpmnlint` runs on staged `.bpmn` via `.githooks/pre-commit` (install: `npm --prefix tools run hooks:install`).

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | mockk unit tests (mock the ports) |
| `adapter.inbound.rest` | `@WebMvcTest` + MockkBean |
| `adapter.outbound.db` | `@DataJpaTest` |
| process end-to-end | CIB seven process tests (JGiven) |
| frontend slices | vitest + MSW (generated handlers) |

**Mutation testing gates PRs at 80** (`:service:app:pitest`): a test that executes without asserting
will fail CI. Coverage says a line ran; mutation says a test would have noticed. The PR gate runs
**diff-scoped** (only the classes the PR changed, still blocking); the **full-module** gate-80 sweep
runs nightly. See ADR-0006.

## Verify After Each Task (targeted, not a full build)

- Backend service/controller: `./gradlew :service:app:test --tests "*<Name>Test"`
- Architecture only: `./gradlew :service:app:test --tests "io.miragon.blueprint.architecture.*"`
- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`, then
  `npm --prefix frontend run api:check`
- Frontend slice: `npm --prefix frontend run typecheck && npm --prefix frontend run test`
- Whole frontend gate: `npm --prefix frontend run verify`

## Working with GitHub

Use the `gh` CLI. Write everything (issues, PRs, commit messages) in **English**. Use
**Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `ci:`, `build:`).

## Skills

Reusable procedures live in `.claude/skills/<name>/SKILL.md`:

| Skill | Use it to |
|---|---|
| `automate-process` | model/adjust a BPMN process and wire its glue code |
| `create-rest-controller` | add a REST endpoint the hexagonal way (port → service → controller) |
| `create-delegate` | add a JavaDelegate for a BPMN service task |
| `create-persistence-adapter` | add an outbound persistence adapter |
| `sync-api-client` | propagate a backend change to a clean frontend client (the highest-value one) |
| `create-feature-slice` | scaffold a new FSD slice |
| `verify-model-visually` | inspect a BPMN model's structure |
| `create-adr` | record an architecture decision |

## Subagents

`.claude/agents/`: **`review-process`** (BPMN model ↔ glue-code consistency) and
**`review-vertical-slice`** (walk BPMN element → port → service → controller → `openapi.json` →
generated client → FSD slice → Bruno → Playwright and report what's missing at each hop).

## ADRs

Architecture decisions are recorded in `docs/adr/` (0001–0013). Read them to understand *why* the
repo is shaped this way before proposing structural changes.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed
doesn't look right, suggest corrections, ask critical questions, and push back where needed.
Challenge ideas that could benefit from further improvement or iterative refinement rather than just
accepting them at face value.
