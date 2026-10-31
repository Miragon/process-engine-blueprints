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

## Ground Rules for Changes

- **Ask before deciding business logic.** Gateway conditions, deadlines, error paths, who works a
  user task and the copy users read are business decisions: propose, don't pick.
- **Derive, never invent.** Details come from the issue, the model or the code; mark anything else
  as an assumption.
- **Keep element ids stable.** The generated process API and the process tests follow a rename
  through the compiler, but the Bruno scenarios and the Playwright specs in `frontend/e2e/` name
  element ids as strings (`serviceTask_orderBike`, `userTask_clarifyAlternative`, …): a rename
  updates them in the same change.

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
| CIB seven webclient (new UI, header "Cockpit" link) | 8080/webapp (admin/admin) |
| OpenAPI spec · Swagger UI | 8080/v3/api-docs · 8080/swagger-ui.html |
| Actuator (health/liveness/readiness · prometheus) | 8080/actuator |
| Vite dev server | 5173 |

## Build Commands

| Area | Command |
|---|---|
| Backend (arch + unit + process + model validation + spec export) | `./gradlew build` |
| Mutation testing (gate 80) | `./gradlew :service:app:pitest` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./gradlew generateBpmnModels` |
| Regenerate + verify the OpenAPI contract | `./gradlew :service:app:test --tests "io.miragon.blueprint.adapter.inbound.rest.OpenApiSpecExportTest"` then `git diff --exit-code openapi/openapi.json` |
| Frontend everything | `npm --prefix frontend run verify` |
| Regenerate the API client | `npm --prefix frontend run api:generate` (check: `api:check`) |
| API scenarios (running stack) | `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r --exclude-tags=demo-data` |
| Browser e2e (running stack) | `npm --prefix frontend run e2e` |
| BPMN lint | `npm run lint:bpmn` |
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
- **`adapter/process` is generated.** Never hand-edit `*ProcessApi.kt` or the shared
  `ServiceTasks`/`Messages`/`ProcessVariables`/`Errors`/`Escalations` files; edit the `.bpmn` and
  re-run `generateBpmnModels`. `.claude/settings.json` denies edits to them, to `openapi.json` and to
  the generated frontend client.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.outbound` `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.

The frontend's Feature-Sliced Design is enforced by steiger (`lint:fsd`) + ESLint import rules +
knip (`lint:deadcode`). See `frontend/AGENTS.md`. Key rule: **`src/processes/` is banned** — in this
codebase "process" means the BPMN model, so the step rail is `widgets/leasing-progress`.

## Process Glue

- A delegate extends `BaseDelegate` and overrides `executeTask`. It reads the application id from
  the business key (`execution.processBusinessKey`) and every other variable through
  `<Process>ProcessApi.FlowNodes.<Node>.Variables`, never a raw string.
- A domain exception becomes `BpmnError(Errors.<X>.code, …)` where the model has a boundary event
  for it (see `OrderBikeDelegate`).
- A `@Transactional` service behind a delegate that throws the exception the delegate maps declares
  it in `noRollbackFor` (see `OrderBikeService`). Otherwise the engine's job transaction is marked
  rollback-only and the error path never runs.
- Process variables stay minimal: ids, plus the inputs a gateway or decision reads (the DMN's `age`
  and `monthlyNetIncome`). The engine is not a database.

## BPMN Quality Gates

- `bpmn-to-code` generates typed process constants from the models at build time; a custom model
  test requires every service task to use a delegate expression (`#{beanName}`).
- The generated `adapter/process` sources are committed and **drift-gated**: `./gradlew build`
  regenerates them in place, and CI runs `git diff --exit-code` on the package, so a `.bpmn` edit
  without a regenerate fails the build — same contract as the OpenAPI spec (ADR-0004).
- Since bpmn-to-code 6 the API is node-centric: `<Process>ProcessApi.FlowNodes.<Node>` carries the
  element (`.id`, `ELEMENT_ID`), its `Variables` and its successors (`Next`). Process tests assert
  the walked path as a compile-checked `ProcessPath` (`adapter/process/ProcessPathAssertions.kt`)
  instead of hand-maintained element-id lists.
- `bpmnlint` runs on staged `.bpmn` via `.githooks/pre-commit` (install: `npm run hooks:install`).

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | mockk unit tests (mock the ports) |
| `adapter.inbound.rest` | `@WebMvcTest` + MockkBean |
| `adapter.inbound.cibseven` | MockK unit test per delegate (skill `create-delegate`) |
| `adapter.outbound.db` | `@DataJpaTest` |
| process end-to-end | CIB seven process tests (JGiven), paths asserted via `ProcessPath` |
| frontend slices | vitest + MSW (generated handlers) |

**Mutation testing gates the domain at 80, nightly** (`:service:app:pitest`): a test that executes without asserting
will fail
the nightly run. Coverage says a line ran; mutation says a test would have noticed. It mutates `io.miragon.blueprint.domain.*` only and does not block pull requests. See ADR-0006.

## Verify After Each Task (targeted, not a full build)

- Backend service/controller: `./gradlew :service:app:test --tests "*<Name>Test"`
- Architecture only: `./gradlew :service:app:test --tests "io.miragon.blueprint.ArchitectureTest"`
- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`, then
  `npm --prefix frontend run api:check`
- Frontend slice: `npm --prefix frontend run typecheck && npm --prefix frontend run test`
- Whole frontend gate: `npm --prefix frontend run verify`

## Working with GitHub

Use the `gh` CLI. Write everything (issues, PRs, commit messages) in **English**. Use
**Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `ci:`, `build:`).

## Agent Tooling

Two CLIs come as Claude Code plugins (`.claude/settings.json`) and are pinned in the root
`package.json`:

- **bpmn-cli** (`npx bpmn`, skill `bpmn-cli:bpmn`) for every change to a `.bpmn`: semantic edits, no
  XML by hand, so the diagram (BPMNDI) stays intact. `BPMN_KEEP_FLOW_IDS=1` keeps flow ids stable.
- **pulse-cli** (`npx pulse-cli`, skill `pulse-cli:pulse-cli`, `/pulse-cli:status`) for the local engine, via
  `.pulse-cli.json`. Run `npx pulse-cli guide` before using it. It reads and repairs
  (`inspect --business-key <applicationId>`, `status`, `incident`, history, `retry`). It never
  deploys: the service deploys the models from its classpath, and a hand deploy is a version the
  generated process API does not know. It never moves a case either (correlating a message,
  completing a user task): that is a business action, taken through the app's UI or API or by a
  human in the Tasklist, not by an agent.

Two more plugins bring recipes, no CLI: **bpmn-to-code** (skills `bpmn-to-code:*`, e.g.
`bpmn-to-code:test-process`, `bpmn-to-code:create-process-adapter`) for the generated process API and
the code around it, pinned to the bpmn-to-code version of the build files, and **bpmnlint-rules**
(skills `bpmnlint-rules:*`) for the Miragon rules in `.bpmnlintrc`, pinned to
`@miragon/bpmnlint-plugin-rules` in `package.json`. They are generic: where they differ from this file
or the local skills below, this file and the local skills win.

`.mcp.json` adds a pinned Playwright MCP server for skill `test-in-browser`. A PostToolUse hook
(`.claude/hooks/lint-bpmn.sh`) runs bpmnlint after every edit of an executable `.bpmn` and hands the
findings back.

Skills and agents live in `.claude/` (see [.claude/README.md](.claude/README.md)); other agents can read
them as recipes:

| Use | When |
|---|---|
| skill `implement-issue` | take a GitHub issue from plan (with approval) to a verified change |
| skill `automate-process` | change an executable model and its glue code, validate it |
| skill `create-rest-controller` | add a REST endpoint the hexagonal way (port → service → controller) |
| skill `create-delegate` | add or test a JavaDelegate for a BPMN service task |
| skill `create-persistence-adapter` | add an outbound persistence adapter |
| skill `sync-api-client` | propagate a backend change to a clean frontend client (the highest-value one) |
| skill `create-feature-slice` | scaffold a new FSD slice |
| skill `review-change` | review a diff or PR, findings by severity |
| skill `test-in-browser` | check a flow in the running app with Playwright MCP, with screenshots |
| skill `create-adr` | record a decision in `docs/adr/` |
| agent `review-process` | read-only audit of the models against their glue code and tests |
| agent `review-vertical-slice` | walk one feature BPMN element → port → service → controller → `openapi.json` → generated client → FSD slice → Bruno → Playwright and report what's missing at each hop |

## ADRs

Architecture decisions are recorded in `docs/adr/` (0001–0013). Read them to understand *why* the
repo is shaped this way before proposing structural changes.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed
doesn't look right, suggest corrections, ask critical questions, and push back where needed.
Challenge ideas that could benefit from further improvement or iterative refinement rather than just
accepting them at face value.
