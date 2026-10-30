# Kotlin + Gradle

The bike-leasing service in **Kotlin 2.4**, built with **Gradle** and a `libs.versions.toml` version
catalog, on Spring Boot 4 and Temporal 1.40.

## 🧰 Commands

Run them from this directory. Temporal and Postgres come from
`docker compose -f ../stack/docker-compose.yml up -d`.

| Task | Command |
|---|---|
| Run the service on :8081 | `./gradlew :service:app:bootRun` |
| Full build (arch + unit + process + spec export) | `./gradlew build` |
| Mutation testing (gate 80) | `./gradlew :service:app:pitest` |
| Build the OCI image | `./gradlew :service:app:bootBuildImage` |

The process tests run on Temporal's in-memory test server, so the full build needs neither Docker nor
the dev stack.

## 📂 Layout

```
service/
  common-architecture-tests/   reusable ArchUnit + Konsist rule suite (src/main)
  common-temporal/             Temporal glue: connection defaults, Kotlin data converter
  common-temporal-test/        helpers for process tests on the test server
  app/                         the service, package root io.miragon.blueprint
    adapter/inbound/rest        REST controllers + OpenAPI / CORS / problem-details config
    adapter/inbound/temporal    one *Activity per use case
    adapter/outbound/temporal   starts and signals the workflows + task inbox
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/…          simulated dealer / contract / insurance / notification adapters
    adapter/process             workflows, activity contracts (Activities.kt), credit rule
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/db/migration      Flyway versioned schema migrations
```

## 🧱 How it is built

- **Hexagonal architecture.** Domain and use cases never depend on Temporal; `drift.yml` keeps them
  byte-identical to `zeebe-native`. `common-architecture-tests` enforces the layering with **ArchUnit**
  and **Konsist**.
- **Reusable Temporal glue.** `common-temporal` contributes the connection defaults and a `DataConverter`
  that handles Kotlin data classes; workers and clients come from `temporal-spring-boot-starter`.
- **Workflows without side effects.** `adapter/process` may depend on Temporal, Kotlin and Java only;
  everything with a side effect is an activity. An ArchUnit rule enforces it.
- **Activities, not workers.** Each `*Activity` calls one use case and holds no business logic.
  `orderBike` reports an unavailable bike as a non-retryable `ApplicationFailure`, which the workflow
  catches.
- **Application id as workflow id**, so signals and updates need no correlation key.
- **Unit tests** (JUnit 5 + MockK) cover every domain type, service, adapter and activity.
- **Process tests** run the workflows on Temporal's in-memory test server with the real activities and
  the process adapter, use cases mocked and timers skipped, one test per branch.
- **Mutation testing** (PIT, gate 80) grades assertion strength — diff-scoped on pull requests, full
  sweep nightly.
- **OpenAPI contract.** A test exports the springdoc spec to [`../openapi/openapi.json`](../openapi/openapi.json);
  CI fails on drift.
