# Java + Maven variant

The bike-leasing blueprint in **Java 21**, built with **Maven**, on Spring Boot 4 against a remote
Operaton 2.1 engine. It is the stack most enterprise teams and our trainings use, and needs no Kotlin
or Gradle knowledge.

<!-- variant:blueprint -->
> [!NOTE]
> Functionally identical to the [Kotlin + Gradle variant](../kotlin-gradle/README.md), which is the one
> we recommend when you are free to choose. Same process, same REST contract, same scenarios.
<!-- /variant:blueprint -->

## 🧰 Commands

Run them from this directory; the Maven wrapper is included. Postgres comes from
`docker compose -f ../stack/docker-compose.yml up -d`. Start the engine host before the worker: the
worker deploys its process into the engine at start-up.

| Task | Command |
|---|---|
| Install the shared modules once, so a single app can run | `./mvnw -DskipTests install` |
| Run the engine host on :8081 | `./mvnw -pl service/engine-service spring-boot:run` |
| Run the worker on :8082 | `./mvnw -pl service/example-service spring-boot:run` |
| Full build (arch + Checkstyle + unit + process + model validation + spec export) | `./mvnw verify` |
| Mutation testing of the worker (gate 80) | `./mvnw -pl service/example-service -am test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed process API after editing a `.bpmn` | `./mvnw -pl service/example-service generate-sources` |
| Build the worker OCI image | `./mvnw -pl service/example-service -am -DskipTests spring-boot:build-image` |

## 📂 Layout

```
pom.xml                        parent: all versions and plugin management
config/checkstyle/             the two source rules (no wildcard imports, one top-level type per file)
service/
  common-architecture-tests/   reusable ArchUnit rule suite (src/main)
  common-operaton-client/      generated Operaton /engine-rest client (from the official OpenAPI spec)
  engine-service/              Operaton engine host on :8081 — /engine-rest + Cockpit, deploys no model,
                               hosts the in-engine execution and task listeners
  example-service/             the worker on :8082, package root io.miragon.blueprint
    adapter/inbound/rest        REST controllers + OpenAPI / problem-details config
    adapter/inbound/operaton    external-task workers (subscribe to the BPMN topics)
    adapter/outbound/engine     deploys the model + drives the remote engine via the generated client
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/…          simulated dealer / contract / insurance / notification adapters
    process                     generated *ProcessApi (bpmn-to-code)
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/{bpmn,dmn,forms}  the process models and Camunda Forms the worker owns
    resources/db/migration      Flyway versioned schema migrations
```

<!-- variant:blueprint -->
The resources are kept identical to the other variant's; CI fails when they differ.
<!-- /variant:blueprint -->

## 🔌 How the remote wiring works

- **Service tasks are external tasks.** Every `<serviceTask>` in the model is `camunda:type="external"`
  with a topic (`bikeLeasing.<task>`). The worker subscribes with `@ExternalTaskSubscription` workers
  that fetch, lock and complete them over the engine's REST API and delegate to the domain use cases.
  A worker that produces variables passes them on `complete(...)`; `orderBike` raises the
  `bikeUnavailable` **BPMN error** via `handleBpmnError` when the dealer has no bike.
- **The worker owns and deploys the model.** `ProcessModelDeploymentAdapter` deploys the BPMN, DMN and
  forms into the engine at start-up (idempotent via duplicate filtering), so the engine stays a
  generic host. This is the right default when a single service owns the process.
- **Driving the process goes through a generated client.** `RemoteLeasingProcessAdapter` starts the
  process by message, correlates the messages that release the wait states and completes the
  `clarify-alternative` user task — all against `/engine-rest`, all correlated by the `ApplicationId`
  business key. See [`service/common-operaton-client`](service/common-operaton-client/README.md).
- **Listeners run in the engine.** A listener has no external-task equivalent, so the two examples are
  Spring beans in `engine-service`, referenced by expression from the deployed model. They address
  process variables by plain string, because the engine host does not depend on the worker. See
  [`service/engine-service`](service/engine-service/README.md).
- **DMN, timers, compensation, the event sub-process and the escalation** run inside the engine; the
  worker never touches them.

## 🧱 How it is built

- **Hexagonal architecture.** Domain and use cases never depend on Operaton. `common-architecture-tests`
  enforces layering, dependency direction and naming with **ArchUnit**; **Checkstyle** adds the two
  source rules. The worker opts in with `class ArchitectureTest extends ServiceArchitectureTest`.
- **Generated process API.** The `bpmn-to-code` Maven plugin turns each `.bpmn` into a typed,
  node-centric `*ProcessApi` class on every build. Never hand-edit the `process` package.
- **Unit tests** (JUnit 5 + Mockito) cover every domain type, service and adapter — controllers via
  `@WebMvcTest` with `@MockitoBean`, persistence via `@DataJpaTest`, the external-task workers directly,
  the engine adapters against the generated client.
- **Process tests** (`operaton-bpm-assert`) deploy the model into a standalone in-memory engine,
  complete each external task by topic and assert the walked path against the generated API.
- **Model validation** (`bpmn-to-code-testing`) checks the models at build time, including a custom rule
  that every service task is an external task with a topic.
- **Mutation testing** (PIT, gate 80) — diff-scoped on pull requests
  (`-Ppit-diff -DtargetClasses="a.b.*"`), full sweep nightly.
- **OpenAPI contract.** A test exports the worker's springdoc spec to
  [`../openapi/openapi.json`](../openapi/openapi.json); CI fails on drift.

<!-- variant:blueprint -->
## 🔀 What differs from the Kotlin variant

Only idioms: **records** and `Optional` instead of `data` classes and nullable types, **Mockito** instead
of MockK, **SLF4J** instead of kotlin-logging, **Checkstyle** instead of Konsist. Records carry no
nullability, so the REST DTOs declare it with `@Schema(requiredMode = …)` / `@Schema(nullable = true)` to
produce the same contract. The generated engine client uses the generator's `restclient` library instead
of `jvm-spring-restclient`, so the engine adapters call it differently while sending the same requests.
<!-- /variant:blueprint -->
