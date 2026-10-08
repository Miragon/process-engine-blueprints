# Kotlin + Gradle variant

The remote bike-leasing blueprint in **Kotlin 2.4**, built with **Gradle** and a `libs.versions.toml`
version catalog, on Spring Boot 4 against a remote CIB seven 2.2 engine: an engine host and a worker
that owns the process.

<!-- variant:blueprint -->
> [!TIP]
> **This is the stack we recommend** when you are free to choose. Null-safety keeps the domain model and
> the OpenAPI contract precise without annotations, `data` and `value` classes keep the domain compact,
> and Konsist adds source-level architecture rules that bytecode analysis cannot express. The
> [Java + Maven variant](../java-maven/README.md) is functionally identical for teams bound to that stack.
<!-- /variant:blueprint -->

## 🧰 Commands

Run them from this directory. Postgres comes from `docker compose -f ../stack/docker-compose.yml up -d`.

| Task | Command |
|---|---|
| Run the engine host on :8081 | `./gradlew :service:engine-service:bootRun` |
| Run the worker on :8082 (the engine must be up) | `./gradlew :service:example-service:bootRun` |
| Full build (arch + unit + process + model validation + spec export) | `./gradlew build` |
| Mutation testing of the worker (gate 80) | `./gradlew :service:example-service:pitest` |
| Regenerate the typed process API after editing a `.bpmn` | `./gradlew generateBpmnModels` |
| Build the worker's OCI image | `./gradlew :service:example-service:bootBuildImage` |

## 📂 Layout

```
service/
  common-architecture-tests/   reusable ArchUnit + Konsist rule suite (src/main)
  common-cibseven-client/      CIB seven /engine-rest client, generated from the official OpenAPI spec
  engine-service/              the engine host: /engine-rest + Cockpit on :8081, deploys no model,
                               hosts the in-engine execution and task listeners
  example-service/             the worker on :8082, package root io.miragon.blueprint
    adapter/inbound/rest        REST controllers + OpenAPI / problem-details config
    adapter/inbound/cibseven    external-task workers, one per BPMN topic
    adapter/outbound/engine     deploys the model and drives the remote engine via the generated client
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/…          simulated dealer / contract / insurance / notification adapters
    process                     generated *ProcessApi and shared constants (bpmn-to-code)
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/{bpmn,dmn,forms}  the process models and Camunda Forms the worker owns and deploys
    resources/db/migration      Flyway versioned schema migrations
```

<!-- variant:blueprint -->
The resources are kept identical to the other variant's; CI fails when they differ.
<!-- /variant:blueprint -->

## 🔌 How the remote wiring works

- **Service tasks are external tasks.** Every `<serviceTask>` in the model is `camunda:type="external"`
  with a topic (`bikeLeasing.<task>`). The worker subscribes with `@ExternalTaskSubscription` handlers
  that fetch, lock and complete them over `/engine-rest`. A handler that produces variables passes them
  on `complete(...)`; `validateApplication` raises the `applicationInvalid` BPMN error.
- **The worker owns and deploys the model.** `ProcessModelDeploymentAdapter` deploys the `.bpmn`, `.dmn`
  and `.form` files at start-up (idempotent through duplicate filtering), so the engine stays a generic
  host.
- **The process is driven over REST.** `RemoteLeasingProcessAdapter` starts the process through its
  message start event, correlates the messages that release the wait states and completes the
  `clarify-alternative` user task — all correlated by the `ApplicationId` business key, through the
  client generated in [`common-cibseven-client`](service/common-cibseven-client/README.md).
- **Listeners run in the engine.** A listener has no external-task equivalent, so the two examples are
  Spring beans in [`engine-service`](service/engine-service/README.md) and reference process variables
  by plain string name.
- **DMN, timers, compensation, the event sub-process and the escalation** run inside the engine; the
  worker never touches them.

## 🧱 How it is built

- **Hexagonal architecture.** Domain and use cases never depend on CIB seven, so the business logic is
  testable and the engine replaceable. `common-architecture-tests` enforces it with **ArchUnit**
  (bytecode: layering, dependency direction, naming) and **Konsist** (source: one declaration per file,
  no wildcard imports); the worker opts in with `class ArchitectureTest : ServiceArchitectureTest(...)`.
- **Generated process API.** The `bpmn-to-code` Gradle plugin turns each `.bpmn` into a typed,
  node-centric `*ProcessApi` object, so element ids, topics, messages, variables and the paths the
  process tests walk are compile-checked. Never hand-edit the `process` package.
- **Unit tests** (JUnit 5 + MockK) cover every domain type, service and adapter — controllers via
  `@WebMvcTest`, persistence via `@DataJpaTest`, the engine adapter via `MockRestServiceServer`.
- **Process tests** (`cibseven-bpm-assert`) run the model in a standalone in-memory engine: external
  tasks are completed by topic, timers fired and messages correlated by hand, and the walked path is
  asserted as a compile-checked `ProcessPath`.
- **Model validation** (`bpmn-to-code-testing`) checks the models at build time, including a custom rule
  that every service task is an external task with a topic.
- **Mutation testing** (PIT, gate 80) grades assertion strength — diff-scoped on pull requests, full
  sweep nightly.
- **OpenAPI contract.** A test exports the springdoc spec to [`../openapi/openapi.json`](../openapi/openapi.json);
  CI fails on drift.
