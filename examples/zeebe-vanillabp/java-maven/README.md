# Java + Maven variant

The bike-leasing service in **Java 21**, built with **Maven**, on Spring Boot 4 against a self-managed
Camunda 8.9 cluster, bound through VanillaBP. It is the stack most enterprise teams and our trainings
use, and needs no Kotlin or Gradle knowledge.

<!-- variant:blueprint -->
> [!NOTE]
> Functionally identical to the [Kotlin + Gradle variant](../kotlin-gradle/README.md), which is the one
> we recommend when you are free to choose. Same process, same REST contract, same scenarios.
<!-- /variant:blueprint -->

## 🧰 Commands

Run them from this directory; the Maven wrapper is included. The Camunda 8 stack comes from
`docker compose -f ../stack/docker-compose.yml up -d`.

| Task | Command |
|---|---|
| Run the service on :8081 | `./mvnw -DskipTests install && ./mvnw -pl service/app spring-boot:run` |
| Full build (arch + Checkstyle + unit + process + model validation + spec export) | `./mvnw verify` |
| Mutation testing (gate 80) | `./mvnw -pl service/app -am test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed process API after editing a `.bpmn` | `./mvnw -pl service/app generate-sources` |
| Build the OCI image | `./mvnw -pl service/app -am -DskipTests spring-boot:build-image` |

## 📂 Layout

```
pom.xml                        parent: all versions and plugin management
config/checkstyle/             the two source rules (no wildcard imports, one top-level type per file)
service/
  common-architecture-tests/   reusable ArchUnit rule suite (src/main)
  common-zeebe/                Zeebe glue: connection config + the VanillaBP Camunda 8 adapter
  common-zeebe-test/           camunda-process-test helper (per-test workflow deployment)
  app/                         the service, package root io.miragon.blueprint
    adapter/inbound/rest        REST controllers + OpenAPI / CORS / problem-details config
    adapter/inbound/workflow    VanillaBP @WorkflowTask bindings for the BPMN tasks
    adapter/outbound/workflow   drives the engine through VanillaBP's ProcessService
    adapter/outbound/db         JPA persistence (workflow aggregate, task inbox, bike portfolio)
    adapter/outbound/…          simulated dealer / contract / insurance / notification adapters
    adapter/process             generated *ProcessApi (bpmn-to-code) + the workflow aggregate
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/bpmn              the process and decision models VanillaBP deploys
    resources/db/migration      Flyway versioned schema migrations
```

<!-- variant:blueprint -->
The resources are kept identical to the other variant's; CI fails when they differ.
<!-- /variant:blueprint -->

## 🧱 How it is built

- **Hexagonal architecture.** Domain and use cases never depend on Zeebe. `common-architecture-tests`
  enforces layering, dependency direction, naming and "no engine API in the service" with **ArchUnit**;
  **Checkstyle** adds the two source rules. A service opts in with
  `class ArchitectureTest extends ServiceArchitectureTest`.
- **A workflow aggregate instead of process variables.** The leasing application row is VanillaBP's
  workflow aggregate: its id is the business key and the message correlation key, and it releases only
  a whitelist of routing values to the engine.
- **Workflow tasks, not job workers.** Each BPMN task is a `@WorkflowTask` in `adapter/inbound/workflow`,
  bound to exactly one use case. `common-zeebe` is a runtime-scoped dependency, so the Zeebe client and
  VanillaBP's Camunda 8 adapter are not on the compile classpath of the service. The client stays on
  the 8.8 line the adapter is built against.
- **Generated process API.** The `bpmn-to-code` Maven plugin turns each `.bpmn` into a typed,
  node-centric `*ProcessApi` class on every build. Never hand-edit the generated files in
  `adapter/process`.
- **Unit tests** (JUnit 5 + Mockito) cover every domain type, service, adapter and workflow task —
  controllers via `@WebMvcTest` with `@MockitoBean`, persistence via `@DataJpaTest`.
- **Process tests** (`camunda-process-test`) drive the deployed model on a real in-container Camunda 8
  engine and assert the walked path as a compile-checked `PathWalk` over the generated API.
- **Model validation** (`bpmn-to-code-testing`) checks the models at build time for engine `ZEEBE`.
- **Mutation testing** (PIT, gate 80) — nightly, on the domain only.
- **OpenAPI contract.** A test exports the springdoc spec to [`../openapi/openapi.json`](../openapi/openapi.json);
  CI fails on drift.

<!-- variant:blueprint -->
## 🔀 What differs from the Kotlin variant

Only idioms: **records** and `Optional` instead of `data` classes and nullable types, **Mockito** instead
of MockK, **SLF4J** instead of kotlin-logging, **Checkstyle** instead of Konsist. Records carry no
nullability, so the REST DTOs declare it with `@Schema(requiredMode = …)` / `@Schema(nullable = true)` to
produce the same contract, and the request record rejects a missing text field in its constructor the
way a non-null Kotlin parameter does.
<!-- /variant:blueprint -->
