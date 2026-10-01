# Fluxnova Bike-Leasing Blueprint

> [!NOTE]
> **🚧 Work in progress.** This is a **solution template** — a reference to fork and build on, for
> our consultants and anyone else — not a product that ships. It's still being fleshed out, so parts
> may be incomplete and it may not yet fully demonstrate what it's meant to. Treat it as a
> living example, and expect it to keep evolving.

A ready-to-fork **starting point** for automating a business process on
[Fluxnova](https://fluxnova.finos.org) (the FINOS-governed fork of Camunda 7) with an **embedded
engine**, Spring Boot and Kotlin — one complete, runnable, production-shaped BPMN service you can clone
and make your own. The engine runs **inside** the Spring Boot service: no remote engine, no separate
engine container.

> [!TIP]
> **Coming from CIB seven, Operaton or Camunda 7?** This blueprint is the Fluxnova port of the
> [CIB seven embedded example](https://github.com/miragon-blueprints/cibseven-embedded-example) — same
> process, same REST API, same tests. What changed and why is in
> [Differences to the CIB seven embedded example](#differences-to-the-cib-seven-embedded-example) and, in
> full, in [`docs/fluxnova-migration.md`](docs/fluxnova-migration.md).

## The scenario

Meet **MiraVelo** — a (fictional) lifestyle bike brand for the quarter-life-crisis crowd: gravel bikes
for the weekends that count, road bikes for everyone who just wants to feel the asphalt. MiraVelo sells
its bikes on a **leasing model** for private and corporate customers, and this project automates that
leasing application from the first request to an active lease.

It's a made-up company, so nobody gets hurt when the DMN politely declines a 15-year-old's application
for a carbon road bike.

## What's inside

Most engine examples stop at a happy-path service task. This one deliberately walks through the **broad
palette of BPMN elements you actually meet in real processes** — and the engineering scaffolding around
them — so a new project starts from something complete instead of a blank page:

![The bike-leasing process](docs/assets/bike-leasing.png)

- a **message start event**, **service tasks** (JavaDelegates) and a **DMN business-rule task**;
- an **embedded sub-process** with an **event-based gateway** (sign vs. a 14-day deadline) and a
  non-interrupting **7-day reminder timer**;
- a **parallel fork/join**, and a **user task with a Fluxnova Form** — completable in the Tasklist *or*
  via a REST endpoint;
- an **execution listener** on a service task and a **task listener** on the user task — the two
  common listener hooks, wired as Spring beans just like the delegates;
- **compensation / SAGA** handlers guarded by **error** and **escalation** boundary events;
- a **call activity** into a second process, a **message event sub-process** (application withdrawal),
  and a **terminate end event**.

## How it's built

```
service/
  common-architecture-tests/   reusable ArchUnit + Konsist rule suite (src/main)
  app/                         the Fluxnova bike-leasing service (hexagonal)
    adapter/inbound/rest        domain REST controllers + OpenAPI / problem-details config
    adapter/inbound/fluxnova    JavaDelegates + listeners for the BPMN service tasks
    adapter/outbound/fluxnova   drives the engine (RuntimeService / TaskService) + task inbox
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/dealer     simulated bike dealer (stock check + order)
    adapter/process             generated *ProcessApi (bpmn-to-code) + engine config
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/{bpmn,dmn,forms}  the process models and Fluxnova Forms
    resources/db/migration      Flyway versioned schema migrations
bruno/                         REST scenarios (happy-path / escalation / abort / not-solvent / …)
openapi/                       the checked-in, drift-gated OpenAPI contract (openapi.json)
docs/                          Architecture Decision Records, migration notes + diagrams
stack/                         Postgres dev stack (docker compose)
.github/                       pre-merge + nightly pipelines + Dependabot
package.json                   root-level bpmnlint config + git-hook installer
```

- **Stack:** Kotlin 2.4 · Java 21 · Spring Boot 4.1 · **Fluxnova 3.0.1** (embedded) · PostgreSQL 18 ·
  Flyway · Gradle 9 with a `libs.versions.toml` version catalog.
- **Generated process API:** the [`bpmn-to-code`](https://github.com/emaarco/bpmn-to-code) Gradle
  plugin turns each `.bpmn` into a typed `*ProcessApi` object, so element ids, messages, timers and
  variables are compile-checked constants used by both delegates and tests.
- **Forms:** Fluxnova Forms (`.form`) are deployed with the process and render in the Fluxnova
  Tasklist for the user tasks.
- **OpenAPI contract:** `springdoc` serves the live spec at `/v3/api-docs` (Swagger UI at
  `/swagger-ui.html`); a test exports it to [`openapi/openapi.json`](openapi/openapi.json) and CI fails
  on drift, so the committed contract can never lie about the code (see ADR-0003).
- **BPMN linting:** [`bpmnlint`](https://github.com/bpmn-io/bpmnlint) — `bpmnlint:recommended`,
  `camunda-compat` and the central [`@miragon/bpmnlint-plugin-rules`](https://www.npmjs.com/package/@miragon/bpmnlint-plugin-rules)
  — gates the `.bpmn` models (`--max-warnings=0`), run in CI and as a pre-commit hook.

## Design decisions

- **Hexagonal architecture** keeps the engine and framework at the edges: the domain and use cases
  never depend on Fluxnova, so business logic is testable and the engine is replaceable. The
  `:service:common-architecture-tests` module enforces this with **ArchUnit** (bytecode: layering,
  dependency direction, naming) and **Konsist** (source: one declaration per file, no wildcard
  imports) — one line wires it into a service: `class ArchitectureTest : ServiceArchitectureTest(...)`.
- **Unit tests** (JUnit 5 + MockK) cover every domain type, application service and adapter with
  given/when/then comments and shared `testLeasingApplication(...)` builders — controllers via
  `@WebMvcTest`, persistence via `@DataJpaTest`. JavaDelegates are covered by the process tests.
- **Process tests** (`fluxnova-bpm-assert`) drive the deployed model on the embedded engine
  deterministically — timers and async continuations are fired and messages correlated by hand —
  covering happy-path, escalation, abort, DMN rejection, and the bike-unavailable →
  alternative-selection loop. A deployment test asserts that both BPMN models, the DMN and the forms
  are deployed on Fluxnova.
- **One engine only:** `verifyFluxnovaIsTheOnlyEngine` (part of `./gradlew build`) fails the build if a
  CIB seven, Camunda or Operaton artifact reaches the runtime or test classpath.
- **Model validation** (`bpmn-to-code-testing`) checks the `.bpmn` models structurally at build time
  (`BpmnRules.all()` plus a custom rule requiring every service task to use a delegate expression).
- **Mutation testing** (PIT, gate 80) grades assertion strength, not just line coverage — diff-scoped
  on every PR and a full sweep nightly (ADR-0004).
- **Bruno + CI** proves the same scenarios against the *running* app: domain REST endpoints drive the
  business actions, and the Fluxnova `/engine-rest` API completes user tasks and fires timer jobs so
  the whole flow runs in the pipeline without real 14-day waits.
- **Ops-ready out of the box:** Flyway versioned migrations with Hibernate on `validate` (ADR-0010),
  actuator health/liveness/readiness probes + Prometheus metrics (ADR-0009), and an OCI image built by
  `./gradlew :service:app:bootBuildImage` — no Dockerfile (ADR-0011).
- **Dependabot** keeps Gradle, the Postgres image, the BPMN tooling and GitHub Actions current.

The *why* behind each of these choices is recorded as an [Architecture Decision Record](docs/README.md).

## Run it

**Prerequisites:** JDK 21, Docker or Podman (for Postgres), and Node 22+ for the BPMN lint and the Bruno
CLI. Gradle comes with the wrapper. The only infrastructure is one PostgreSQL database — it holds the
application tables and the engine's `ACT_*` tables.

```bash
# 1. start Postgres
docker compose -f stack/docker-compose.yml up -d

# 2. run the app (Fluxnova Tasklist/Monitoring/Admin at http://localhost:8080/fluxnova, admin/admin;
#    Swagger UI at http://localhost:8080/swagger-ui.html)
./gradlew :service:app:bootRun

# 3. lint the BPMN models
npm ci && npm run lint:bpmn

# 4. drive the scenarios (build + arch + process tests first, then the REST flows)
./gradlew build
cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r
```

| What | Where |
|---|---|
| Domain REST API | <http://localhost:8080/api> — contract in [`openapi/openapi.json`](openapi/openapi.json) |
| Engine REST API | <http://localhost:8080/engine-rest> |
| Tasklist · Monitoring · Admin | <http://localhost:8080/fluxnova> (admin/admin) |
| Swagger UI · OpenAPI spec | <http://localhost:8080/swagger-ui.html> · <http://localhost:8080/v3/api-docs> |
| Health · metrics | <http://localhost:8080/actuator/health> · <http://localhost:8080/actuator/prometheus> |

Fluxnova calls the classic Cockpit **Monitoring** (`/fluxnova/app/monitoring/`). The Tasklist starts
without a task filter — click *Add a simple filter* once to see the open user tasks.

### Walk through the complete process

The Bruno collection runs every scenario for you. To drive the happy path by hand (the snippet uses
`jq`):

```bash
# 1. submit a leasing request — starts the process by message, returns the applicationId
curl -s -X POST http://localhost:8080/api/bike-leasing -H 'Content-Type: application/json' \
  -d '{"customerName":"Alice Rider","email":"alice@example.com","age":35,"monthlyNetIncome":3500.0,"bikeId":"BIKE-900","bikeModel":"Gravel Explorer 900"}'

ID=<applicationId from the response>

# 2. read the domain view — wait until contractId is set (validation + DMN + contract sent)
curl -s http://localhost:8080/api/bike-leasing/$ID

# 3. sign the contract — forks into insurance + bike order; wait for status ORDERED
curl -s -X POST http://localhost:8080/api/bike-leasing/$ID/sign-contract

# 4. report the handover — the process now waits for the 14-day withdrawal period
curl -s -X POST http://localhost:8080/api/bike-leasing/$ID/report-handover

# 5. skip the wait: find the timer job of that instance and execute it through the engine REST API
PI=$(curl -s "http://localhost:8080/engine-rest/process-instance?businessKey=$ID" | jq -r '.[0].id')
JOB=$(curl -s "http://localhost:8080/engine-rest/job?processInstanceId=$PI&activityId=event_withdrawalPeriodElapsed&timers=true" | jq -r '.[0].id')
curl -s -X POST http://localhost:8080/engine-rest/job/$JOB/execute

# 6. the lease is active
curl -s http://localhost:8080/api/bike-leasing/$ID      # "status":"ACTIVE"
```

The other paths use the same API:

| Scenario | How to trigger it | Bruno folder |
|---|---|---|
| Not solvent (DMN rejects) | submit with `"age": 15` | `04-not-solvent` |
| Escalation (contract not signed) | fire the `event_signatureDeadline` timer job instead of signing | `02-escalation` |
| Abort with compensation | `POST /api/bike-leasing/{id}/withdraw` after signing, then complete `userTask_clarifyReturn` | `03-abort` |
| Bike unavailable (user task) | submit `"bikeId": "BIKE-OOS"`, then `POST …/clarify-alternative` | `05-bike-unavailable` |
| Incident and retry | submit `"bikeId": "BIKE-FAIL"` | `06-incident-demo` |
| Lists and task inbox | `GET /api/bikes`, `GET /api/bike-leasing`, `GET /api/tasks/clarify-alternative` | `07-list-and-inbox` |

The `age` and `monthlyNetIncome` feed the `checkCreditRating` DMN; the `bikeId` identifies the bike and
is the *only* bike attribute the engine ever carries. The descriptive `bikeModel` lives in a separate
**bike portfolio** aggregate (its own `bike_portfolio` table, keyed by `bikeId`) — never as a process
variable — and `GET /api/bike-leasing/{id}` resolves it back from there.

If the requested bike is out of stock, the `Clarify alternative with customer` user task can be resolved
**two ways**, a deliberate contrast:

- the **recommended** path — a client calls `POST …/api/bike-leasing/{id}/clarify-alternative`, which
  routes through the domain (persisting the chosen alternative) *before* completing the task; versus
- the **form-only** path on `clarify-return` in `cancel-bike-order.bpmn`, kept as a counter-example:
  completing it via the Fluxnova Form or `/engine-rest` never touches the domain, so its data lands only
  in process variables (see the `bpmn:documentation` on each task).

Bike availability itself is decided by a `BikeDealerPort` outbound adapter (`checkAvailability` /
`order`) whose small out-of-stock deny-list drives the branch.

## Incident demo

Want to teach **transaction boundaries, retries and incidents**? Submit a request for the poison bike
`BIKE-FAIL`: the simulated dealer "outage" fails the *Order bike from dealer* job, its retries count
down (`R3/PT10S`), and an **incident** appears in the Monitoring app to analyze and retry. A
ready-to-run Bruno collection lives in `bruno/06-incident-demo/`.

## Database and configuration

- **One PostgreSQL database, two owners.** Flyway owns the application tables
  (`service/app/src/main/resources/db/migration`) and Hibernate only validates them. Fluxnova creates
  and updates its own `ACT_*` schema on start-up (`fluxnova.bpm.database.schema-update: true`).
- **Persistent by default.** Process instances survive a restart — the Postgres volume is only dropped
  by `docker compose -f stack/docker-compose.yml down -v`.
- **Engine configuration** lives under the `fluxnova.bpm.*` prefix in
  [`application.yaml`](service/app/src/main/resources/application.yaml): admin user, deployment
  patterns for `.bpmn` / `.dmn` / `.form`, the webapp path and the job-executor polling.
- **Deploy-relevant values come from the environment:**

  | Env var | Purpose | Default |
  |---|---|---|
  | `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/bikeleasing` |
  | `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | `admin` / `admin` |
  | `SERVER_PORT` | HTTP port | `8080` |

- **Tests** run on an in-memory H2 database with the job executor switched off, so they need no
  container.

## Known limitations

- **Spring Boot line.** Fluxnova 3.0.1 is built and tested upstream against Spring Boot 4.0.5. This
  blueprint runs it on Spring Boot 4.1.1 — covered by this repo's own test and Bruno suites, not by
  upstream.
- **Jackson workaround.** Fluxnova 3.0.1's `engine-rest` pins a pre-release Jackson java.time module
  that breaks date serialisation on Jackson 3.1. It is excluded in `service/app/build.gradle.kts`
  ([finos/fluxnova-bpm-platform#244](https://github.com/finos/fluxnova-bpm-platform/issues/244)); the
  exclusion can go once Fluxnova 3.1.0 is on Maven Central.
- **`camunda:` stays in the models and their tooling.** See
  [Compatibility references that stay](#compatibility-references-that-stay).
- **Model metadata was set by hand.** The BPMN, DMN and form headers carry the values the official
  Fluxnova migrator writes; the files were not round-tripped through the Fluxnova Modeler GUI.
- **No Java + Maven variant yet.** The sibling blueprints publish one at a `java-maven` tag; this
  repository only has the playbook ([`docs/variants/java-maven.md`](docs/variants/java-maven.md)).
- **Not production-hardened.** Fixed ports, `admin`/`admin` credentials and an unauthenticated
  `/engine-rest` are development defaults.

## Differences to the CIB seven embedded example

The process, the domain, the REST contract and the test scenarios are unchanged. The engine-facing
differences:

| | CIB seven embedded example | This blueprint |
|---|---|---|
| Engine | CIB seven 2.2.0 | Fluxnova 3.0.1 |
| Starters | `org.cibseven.bpm.springboot:cibseven-bpm-spring-boot-starter-{webapp,rest}-4` | `org.finos.fluxnova.bpm.springboot:fluxnova-bpm-spring-boot-starter-{webapp,rest}` |
| Java packages | `org.cibseven.bpm.*` | `org.finos.fluxnova.bpm.*` |
| Configuration prefix | `camunda.bpm.*` | `fluxnova.bpm.*` |
| Webapps | `/camunda` — Cockpit, Tasklist | `/fluxnova` — Monitoring (the renamed Cockpit), Tasklist, Admin |
| Extra web config | CIB seven webclient excluded, JWT secret in `application.yaml` | none needed |
| Build workaround | `cibseven-webclient-web` exclusion | `jackson-datatype-jsr310` exclusion |
| Process tests | `cibseven-bpm-assert` (+ unused JGiven dependencies) | `fluxnova-bpm-assert`, JGiven dropped |
| Additional gates | — | deployment test, `verifyFluxnovaIsTheOnlyEngine` |
| Model metadata | Camunda Modeler / Camunda Platform 7.24 | Fluxnova Modeler / Fluxnova Platform 3.0 |

### Compatibility references that stay

These `camunda` names are deliberate, not leftovers:

- **`camunda:` extension attributes in the BPMN and DMN** (`camunda:delegateExpression`,
  `camunda:asyncAfter`, `camunda:formRef`, `camunda:historyTimeToLive`, …). Fluxnova 3.0.1 reads its
  executable extensions from the `http://camunda.org/schema/1.0/bpmn` namespace, and the Fluxnova
  Modeler writes them the same way.
- **`ProcessEngine.CAMUNDA_7` in the `bpmn-to-code` configuration** and `BpmnEngine.CAMUNDA_7` in the
  generated `*ProcessApi`. `bpmn-to-code` 5.2 has no Fluxnova dialect; its Camunda 7 dialect is the one
  that reads `camunda:` models.
- **`camunda-compat` and `camunda-bpmn-moddle` in `.bpmnlintrc`** — the lint rules and the moddle
  descriptor for that same namespace.
- **`org.camunda.bpm.model:camunda-bpmn-model` on the test classpath** — the model parser that
  `bpmn-to-code-testing` brings along. It is the only allow-listed exception of
  `verifyFluxnovaIsTheOnlyEngine` and never reaches the runtime classpath.

## Origin

This repository was derived from
[`miragon-blueprints/cibseven-embedded-example`](https://github.com/miragon-blueprints/cibseven-embedded-example)
at commit [`9a55373`](https://github.com/miragon-blueprints/cibseven-embedded-example/commit/9a553739187d759de7b5c0eb97c672cf777934c2)
(MIT, Copyright (c) 2026 Miragon GmbH) and migrated to Fluxnova. The commit
`chore: import cibseven-embedded-example as migration baseline` is that verbatim import; every later
commit is part of the migration. Fluxnova itself is Apache-2.0 licensed and is used as a dependency —
no Fluxnova source is copied into this repository.

## Contributing

Contributions are welcome. Please open an issue to discuss substantial changes first, keep the
architecture tests green (`./gradlew build`), and use
[Conventional Commits](https://www.conventionalcommits.org) for commit messages and PR titles.

## License

Licensed under the [MIT License](./LICENSE).
