# CIB seven Remote Bike-Leasing Blueprint

A ready-to-fork **starting point** for automating a business process on
[CIB seven](https://cibseven.org) (the community fork of Camunda 7) with a **remote engine**,
Spring Boot and Kotlin. Same MiraVelo bike-leasing scenario as the embedded
[`cibseven-embedded-example`](https://github.com/miragon-blueprints/cibseven-embedded-example) — the
**opposite topology**: the engine is a generic host, while a separate worker **owns the process**,
deploys it into the engine, and runs all service-task logic as **external tasks** over the engine's
REST API.

## The scenario

Meet **MiraVelo** — a (fictional) lifestyle bike brand for the quarter-life-crisis crowd: gravel bikes
for the weekends that count, road bikes for everyone who just wants to feel the asphalt. MiraVelo sells
its bikes on a **leasing model** for private and corporate customers, and this project automates that
leasing application from the first request to an active lease.

It's a made-up company, so nobody gets hurt when the DMN politely declines a 15-year-old's application
for a carbon road bike.

## Embedded vs. remote — what changes

The process model, the domain and the use cases are identical to the embedded blueprint. Only the
*topology* differs:

| | Embedded | **Remote (this repo)** |
|---|---|---|
| Engine | in-process, one app | its own app (`engine-service`, **:8081**) |
| Service tasks | `JavaDelegate`s in the app | **external tasks** handled by a worker (`example-service`, **:8082**) |
| Driving the process | `RuntimeService` / `TaskService` | a `RestClient` against `/engine-rest` |
| Process contract | generated inside the app | a **shared module** both apps depend on |

![The bike-leasing process](docs/bike-leasing.png)

The process still walks through the broad palette of BPMN elements you meet in real projects — a
**message start event**, service tasks, a **DMN business-rule task**, an **embedded sub-process** with
an **event-based gateway** (sign vs. a 14-day deadline) and a non-interrupting **7-day reminder
timer**, a **parallel fork/join**, a **user task with a Camunda Form**, **compensation / SAGA**
handlers guarded by **error** and **escalation** boundary events, a **call activity**, a **message
event sub-process** (application withdrawal) and a **terminate end event**.

## How it's built

```
service/
  common-architecture-tests/   reusable ArchUnit + Konsist rule suite (src/main)
  common-package/              THE CONTRACT: BPMN/DMN/forms + generated *ProcessApi (bpmn-to-code)
  engine-service/              generic CIB seven engine host — /engine-rest + Cockpit, no model (:8081)
  example-service/             the worker; OWNS + deploys the process; business logic (hexagonal), :8082
    adapter/inbound/rest        domain REST controllers
    adapter/inbound/cibseven    external-task workers (subscribe to the BPMN topics)
    adapter/outbound/engine     deploys the model + drives the remote engine via RestClient
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/dealer     simulated bike dealer (stock check + order)
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
bruno/                         REST scenarios (happy-path / escalation / abort / not-solvent / …)
tools/                         BPMN linting (bpmnlint)
stack/                         Postgres dev stack (docker compose)
.github/                       pre-merge pipeline + Dependabot
```

- **Stack:** Kotlin 2.4 · Spring Boot 4 · CIB seven 2.2 (remote) · PostgreSQL · Gradle with a
  `libs.versions.toml` version catalog.
- **The contract, once:** `common-package` owns the `.bpmn`/`.dmn`/`.form` models and the
  [`bpmn-to-code`](https://github.com/emaarco/bpmn-to-code)-generated `*ProcessApi` (process id,
  element ids, messages, timers, variables and **external-task topics**). The `example-service`
  references the same topics and message names — one source of truth, no drift between model and worker.
- **Who owns and deploys the model:** the `example-service` **owns the process** and deploys it into the
  remote engine at start-up (`ProcessModelDeploymentAdapter`, idempotent via `enable-duplicate-filtering`), so the
  engine stays a generic, model-agnostic host. This is the right default when a **single service** owns
  the process. The alternative — the **engine** carrying the model, for a process fulfilled by *several*
  services — is documented in [`service/engine-service/README.md`](service/engine-service/README.md).

## How the remote wiring works

- **Service tasks are external tasks.** Every `<serviceTask>` in the model is
  `camunda:type="external"` with a topic (`bikeLeasing.<task>`). The `example-service` subscribes with
  `@ExternalTaskSubscription` workers (`adapter/inbound/cibseven`) that fetch, lock and complete them
  over the engine's REST API — the same use cases the embedded blueprint calls from its delegates.
  - A worker that produces variables passes them on `complete(...)` (e.g. `orderBike` →
    `orderId` / `bikeAvailable`).
  - `validateApplication` raises the `applicationInvalid` **BPMN error** via `handleBpmnError`, so the
    error boundary event still diverts to rejection.
- **Driving the process is done over REST.** `RemoteLeasingProcessAdapter` (`adapter/outbound/engine`)
  starts the process through its **message start event**, correlates the messages that release the wait
  states (contract signed, handover reported, application withdrawn) and completes the
  `clarify-alternative` user task — all against `/engine-rest`, all correlated by the **`ApplicationId`
  business key**, exactly as the embedded adapter does through the Java API.
- **DMN, timers, compensation, the event sub-process and the escalation** all run **inside the
  engine** — the worker never touches them.

## Run it

```bash
# 1. start Postgres (creates the engine DB and the worker's domain DB)
docker compose -f stack/docker-compose.yml up -d

# 2. start the (model-agnostic) engine host first — Cockpit/Tasklist at
#    http://localhost:8081/camunda (admin/admin)
./gradlew :service:engine-service:bootRun

# 3. start the worker (in a second shell) — it deploys the process into the engine at start-up,
#    so the engine must already be running
./gradlew :service:example-service:bootRun

# 4. lint the BPMN models
npm --prefix tools ci && npm --prefix tools run lint:bpmn

# 5. drive the scenarios (build + arch + model-validation tests first, then the REST flows)
./gradlew build
cd bruno && npx @usebruno/cli run . --env local -r
```

Start a case with `POST http://localhost:8082/api/bike-leasing`
(`{ "customerName": …, "email": …, "age": 35, "monthlyNetIncome": 3500, "bikeId": "BIKE-900", "bikeModel": "Gravel Explorer 900" }`).

The `age` and `monthlyNetIncome` feed the `checkCreditRating` DMN (evaluated by the engine); the
`bikeId` identifies the bike and is the *only* bike attribute the engine ever carries. The descriptive
`bikeModel` lives in a separate **bike portfolio** aggregate in the worker's own database (keyed by
`bikeId`) — never as a process variable — and `GET /api/bike-leasing/{id}` resolves it back from there.

Watch the external-task workers auto-complete `validateApplication`, `orderBike`, … in the
`example-service` log, and inspect the running instance in the CIB seven Cockpit at
http://localhost:8081/camunda.

## Design decisions

- **Hexagonal architecture** keeps the engine and framework at the edges: the domain and use cases
  never depend on CIB seven, so business logic is testable and the engine is replaceable. The
  `:service:common-architecture-tests` module enforces this with **ArchUnit** and **Konsist** — one
  line wires it into the worker: `class ArchitectureTest : ServiceArchitectureTest(...)`.
- **Unit tests** (JUnit 5 + MockK) cover every domain type, application service and adapter —
  controllers via `@WebMvcTest`, persistence via `@DataJpaTest`, the external-task workers directly,
  and the remote engine adapter via `MockRestServiceServer`.
- **Model validation** (`bpmn-to-code-testing`) checks the `.bpmn` models structurally at build time in
  `common-package` — including a custom rule that every service task must be an **external task with a
  topic** (the remote counterpart to the embedded delegate-expression rule).
- **Process tests** (`cibseven-bpm-assert`, in `example-service` — with the process owner) spin up a
  standalone in-memory engine, deploy the model from `common-package`, and assert the *topology* —
  happy-path, escalation, DMN rejection, abort/compensation and the bike-unavailable → alternative loop.
  Service tasks are **external tasks**, so the test completes each one explicitly by topic (supplying the
  output variables a real worker would return); user tasks, messages and timers are released by hand.
- **Bruno + CI** proves the same scenarios against the *running* pair of apps: domain REST endpoints
  (`:8082`) drive the business actions, and the CIB seven `/engine-rest` API (`:8081`) completes user
  tasks and fires timer jobs so the whole flow runs in the pipeline without real 14-day waits.
- **Dependabot** keeps Gradle, the Postgres image and GitHub Actions current.

## Contributing

Contributions are welcome. Please open an issue to discuss substantial changes first, keep the
architecture tests green (`./gradlew build`), and use
[Conventional Commits](https://www.conventionalcommits.org) for commit messages and PR titles.

## License

Licensed under the [MIT License](./LICENSE).
