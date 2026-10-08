# Java + Maven variant

The remote bike-leasing blueprint in **Java 21**, built with **Maven**, on Spring Boot 4 against a
remote CIB seven 2.2 engine: an engine host and a worker that owns the process. It is the stack most
enterprise teams and our trainings use, and needs no Kotlin or Gradle knowledge.

<!-- variant:blueprint -->
> [!NOTE]
> Functionally identical to the [Kotlin + Gradle variant](../kotlin-gradle/README.md), which is the one
> we recommend when you are free to choose. Same process, same REST contract, same scenarios.
<!-- /variant:blueprint -->

## 🧰 Commands

Run them from this directory; the Maven wrapper is included. Postgres comes from
`docker compose -f ../stack/docker-compose.yml up -d`.

| Task | Command |
|---|---|
| Run the engine host on :8081 | `./mvnw -pl service/engine-service spring-boot:run` |
| Run the worker on :8082 (the engine must be up) | `./mvnw -pl service/example-service -am spring-boot:run` |
| Full build (arch + Checkstyle + unit + process + model validation + spec export) | `./mvnw verify` |
| Mutation testing of the worker (gate 80) | `./mvnw -pl service/example-service -am test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed process API after editing a `.bpmn` | `./mvnw -pl service/example-service -am generate-sources` |
| Build the worker's OCI image | `./mvnw -pl service/example-service -am -DskipTests package spring-boot:build-image-no-fork` |

## 📂 Layout

```
pom.xml                        parent: all versions and plugin management
config/checkstyle/             the two source rules (no wildcard imports, one top-level type per file)
service/
  common-architecture-tests/   reusable ArchUnit rule suite (src/main)
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
  on `complete(...)`; `orderBike` raises the `bikeUnavailable` BPMN error when the dealer has no bike.
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

- **Hexagonal architecture.** Domain and use cases never depend on CIB seven. `common-architecture-tests`
  enforces layering, dependency direction and naming with **ArchUnit**; **Checkstyle** adds the two
  source rules. The worker opts in with `class ArchitectureTest extends ServiceArchitectureTest`.
- **Generated process API.** The `bpmn-to-code` Maven plugin turns each `.bpmn` into a typed,
  node-centric `*ProcessApi` class on every build. Never hand-edit the `process` package.
- **Unit tests** (JUnit 5 + Mockito) cover every domain type, service and adapter — controllers via
  `@WebMvcTest` with `@MockitoBean`, persistence via `@DataJpaTest`, the engine adapter via
  `MockRestServiceServer`.
- **Process tests** (`cibseven-bpm-assert`) run the model in a standalone in-memory engine: external
  tasks are completed by topic, timers fired and messages correlated by hand, and the walked path is
  asserted as a compile-checked `PathWalk`.
- **Model validation** (`bpmn-to-code-testing`) checks the models at build time, including a custom rule
  that every service task is an external task with a topic.
- **Mutation testing** (PIT, gate 80) — diff-scoped on pull requests (`-DtargetClasses="a.b.*"`), full
  sweep nightly.
- **OpenAPI contract.** A test exports the springdoc spec to [`../openapi/openapi.json`](../openapi/openapi.json);
  CI fails on drift.

<!-- variant:blueprint -->
## 🔀 What differs from the Kotlin variant

Only idioms: **records** and `Optional` instead of `data` classes and nullable types, **Mockito** instead
of MockK, **SLF4J** instead of kotlin-logging, **Checkstyle** instead of Konsist. Records carry no
nullability, so the REST DTOs declare it with annotations to produce the same contract.
<!-- /variant:blueprint -->
