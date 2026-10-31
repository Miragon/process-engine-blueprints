# Kotlin + Gradle variant

The bike-leasing service in **Kotlin 2.4**, built with **Gradle** and a `libs.versions.toml` version
catalog, on Spring Boot 4 against a self-managed Camunda 8.9 cluster, bound through VanillaBP.

<!-- variant:blueprint -->
> [!TIP]
> **This is the stack we recommend** when you are free to choose. Null-safety keeps the domain model and
> the OpenAPI contract precise without annotations, `data` and `value` classes keep the domain compact,
> and Konsist adds source-level architecture rules that bytecode analysis cannot express. The
> [Java + Maven variant](../java-maven/README.md) is functionally identical for teams bound to that stack.
<!-- /variant:blueprint -->

## 🧰 Commands

Run them from this directory. The Camunda 8 stack comes from
`docker compose -f ../stack/docker-compose.yml up -d`.

| Task | Command |
|---|---|
| Run the service on :8081 | `./gradlew :service:app:bootRun` |
| Full build (arch + unit + process + model validation + spec export) | `./gradlew build` |
| Mutation testing (gate 80) | `./gradlew :service:app:pitest` |
| Regenerate the typed process API after editing a `.bpmn` | `./gradlew generateBpmnModels` |
| Build the OCI image | `./gradlew :service:app:bootBuildImage` |

## 📂 Layout

```
service/
  common-architecture-tests/   reusable ArchUnit + Konsist rule suite (src/main)
  common-zeebe/                Zeebe glue: connection config + the VanillaBP Camunda 8 adapter
  common-zeebe-test/           camunda-process-test helpers (assertions, per-test workflow deployment)
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

- **Hexagonal architecture.** Domain and use cases never depend on Zeebe, so the business logic is
  testable and the engine replaceable. `common-architecture-tests` enforces it with **ArchUnit**
  (bytecode: layering, dependency direction, naming, no engine API in the service) and **Konsist**
  (source: one declaration per file, no wildcard imports); a service opts in with
  `class ArchitectureTest : ServiceArchitectureTest(...)`.
- **A workflow aggregate instead of process variables.** The leasing application row is VanillaBP's
  workflow aggregate: its id is the business key and the message correlation key, and it releases only
  a whitelist of routing values to the engine.
- **Workflow tasks, not job workers.** Each BPMN task is a `@WorkflowTask` in `adapter/inbound/workflow`,
  bound to exactly one use case. `common-zeebe` contributes the Zeebe connection and puts VanillaBP's
  Camunda 8 adapter on the runtime classpath only, so the service compiles against the SPI alone. The
  client stays on the 8.8 line the adapter is built against.
- **Generated process API.** The `bpmn-to-code` Gradle plugin turns each `.bpmn` into a typed,
  node-centric `*ProcessApi` object, so element ids, job types, messages and the paths the process
  tests walk are compile-checked. Never hand-edit the generated files in `adapter/process`.
- **Unit tests** (JUnit 5 + MockK) cover every domain type, service, adapter and workflow task —
  controllers via `@WebMvcTest`, persistence via `@DataJpaTest`.
- **Process tests** (`camunda-process-test`) drive the deployed model on a real in-container Camunda 8
  engine: timers are advanced with `increaseTime`, messages go through the real adapter, and the walked
  path is asserted as a compile-checked `ProcessPath`.
- **Model validation** (`bpmn-to-code-testing`) checks the models at build time for engine `ZEEBE`.
- **Mutation testing** (PIT, gate 80) grades assertion strength — nightly, on the domain only.
- **OpenAPI contract.** A test exports the springdoc spec to [`../openapi/openapi.json`](../openapi/openapi.json);
  CI fails on drift.
