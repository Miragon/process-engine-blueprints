# Java + Maven variant

The bike-leasing service in **Java 21**, built with **Maven**, on Spring Boot 4.1 and an embedded
Operaton 2.1 engine. It is the stack most enterprise teams and trainings use, and needs no Kotlin
or Gradle knowledge.

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
| Run the service on :8080 | `./mvnw -DskipTests install && ./mvnw -pl service/app spring-boot:run` |
| Full build (arch + Checkstyle + unit + process + model validation + spec export) | `./mvnw verify` |
| Mutation testing (gate 80) | `./mvnw -pl service/app -am test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed process API after editing a `.bpmn` | `./mvnw -pl service/app io.miragon:bpmn-to-code-maven:generate-bpmn-api` |
| Build the OCI image `miravelo/app` | `./mvnw -DskipTests install && ./mvnw -pl service/app -DskipTests spring-boot:build-image` |

## 📂 Layout

```
pom.xml                        parent: all versions and plugin management
config/checkstyle/             the two source rules (no wildcard imports, one top-level type per file)
service/
  common-architecture-tests/   reusable ArchUnit rule suite (src/main)
  app/                         the service, package root io.miragon.blueprint
    adapter/inbound/rest        REST controllers + OpenAPI / problem-details config
    adapter/inbound/operaton    JavaDelegates + listeners for the BPMN service tasks
    adapter/outbound/operaton   drives the engine (RuntimeService / TaskService) + task inbox
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/…          simulated dealer / contract / insurance / notification adapters
    adapter/process             generated *ProcessApi (bpmn-to-code) + engine config
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/{bpmn,dmn,forms}  the process models and Camunda Forms
    resources/db/migration      Flyway versioned schema migrations
```

<!-- variant:blueprint -->
The resources are kept identical to the other variant's; CI fails when they differ.
<!-- /variant:blueprint -->

## 🧱 How it is built

- **Hexagonal architecture.** Domain and use cases never depend on Operaton, so the business logic is
  testable and the engine replaceable. `common-architecture-tests` enforces layering, dependency
  direction and naming with **ArchUnit**; **Checkstyle** adds the two source rules. A service opts in
  with `class ArchitectureTest extends ServiceArchitectureTest`.
- **Generated process API.** The [`bpmn-to-code`](https://github.com/emaarco/bpmn-to-code) Maven plugin
  turns each `.bpmn` into a typed, node-centric `*ProcessApi` class on every build, so element ids,
  messages, timers and variables are compile-checked. Never hand-edit `adapter/process`.
- **Unit tests** (JUnit 5 + Mockito) cover every domain type, service and adapter — controllers via
  `@WebMvcTest` with `@MockitoBean`, persistence via `@DataJpaTest`.
- **Process tests** (`operaton-bpm-assert`) drive the deployed model deterministically — timers and async
  continuations are fired and messages correlated by hand — and assert the walked path against the
  generated API.
- **Model validation** (`bpmn-to-code-testing`) checks the models at build time, including a custom rule
  that every service task uses a delegate expression.
- **Mutation testing** (PIT, gate 80) grades assertion strength — diff-scoped on pull requests
  (`-Ppit-diff -DtargetClasses="a.b.*"`), full sweep nightly.
- **OpenAPI contract.** A test exports the springdoc spec to [`../openapi/openapi.json`](../openapi/openapi.json);
  CI fails on drift. The API answers errors as **RFC-7807 problem details**.
- **Operations.** Actuator probes and Prometheus metrics at `/actuator/*`; **Flyway** owns the schema and
  Hibernate only validates it.

<!-- variant:blueprint -->
## 🔀 What differs from the Kotlin variant

Only idioms: **records** and `Optional` instead of `data` classes and nullable types, **Mockito** instead
of MockK, **SLF4J** instead of kotlin-logging, **Checkstyle** instead of Konsist. Records carry no
nullability, so the REST DTOs declare it with `@Schema(requiredMode = …)` / `@Schema(nullable = true)` to
produce the same contract.
<!-- /variant:blueprint -->
