# MiraVelo bike leasing (Operaton remote)

[![Pre-Merge Kotlin/Gradle](https://github.com/miragon-blueprints/operaton-remote-example/actions/workflows/pre-merge-kotlin-gradle.yml/badge.svg)](https://github.com/miragon-blueprints/operaton-remote-example/actions/workflows/pre-merge-kotlin-gradle.yml) [![Pre-Merge Java/Maven](https://github.com/miragon-blueprints/operaton-remote-example/actions/workflows/pre-merge-java-maven.yml/badge.svg)](https://github.com/miragon-blueprints/operaton-remote-example/actions/workflows/pre-merge-java-maven.yml) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A solution template to fork for automating a business process on [Operaton](https://operaton.org)
(the community fork of Camunda 7) with a **remote engine** and Spring Boot. The engine runs as a
generic host, while a separate worker owns the process: it deploys the model into the engine and runs
all service-task logic as **external tasks** over the engine's REST API. It is still a work in progress.

<!-- variant:blueprint -->
## Pick your stack

| | [`kotlin-gradle/`](kotlin-gradle/README.md) | [`java-maven/`](java-maven/README.md) |
|---|---|---|
| **Stack** | Kotlin 2.4 · Gradle | Java 21 · Maven |
| **Choose it when** | you are free to choose — **our recommendation** | Java + Maven is your standard, or you are in a training |

Both run the same process behind the same REST contract; CI keeps their models and configuration
identical. Building on one? [Turn the repo into a single-stack starter](docs/starter.md).
<!-- /variant:blueprint -->

## The process

![The bike-leasing process](docs/assets/bike-leasing.png)

**MiraVelo** is a fictional bike brand that sells on a leasing model. The project takes a leasing
application from the first request to an active lease and deliberately covers the BPMN elements you
meet in real processes: message start, service tasks run as external tasks by the worker and a DMN
rule, an embedded sub-process with an event-based gateway and reminder timer, a parallel fork/join, a
user task with a Camunda Form, execution and task listeners that run inside the engine, compensation
guarded by error and escalation events, a call activity, a withdrawal event sub-process and a
terminate end event.

## Quick start

Requires JDK 21 and Docker or Podman. Start Postgres, then the engine host, then the worker (in a
second shell) — the worker deploys its process into the engine at start-up.

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres on :5432
# variant:kotlin-gradle
cd kotlin-gradle && ./gradlew :service:engine-service:bootRun    # engine host on :8081
cd kotlin-gradle && ./gradlew :service:example-service:bootRun   # worker on :8082
# /variant:kotlin-gradle
# variant:java-maven
cd java-maven && ./mvnw -DskipTests install && ./mvnw -pl service/engine-service spring-boot:run   # engine host on :8081
cd java-maven && ./mvnw -pl service/example-service spring-boot:run                                # worker on :8082
# /variant:java-maven
```

| What | URL | Login |
|---|---|---|
| Operaton Cockpit / Tasklist | <http://localhost:8081/operaton> | admin / admin |
| Worker Swagger UI | <http://localhost:8082/swagger-ui.html> | |

Drive the whole process over REST: `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r`.
Tests, ports and containers are in [CONTRIBUTING.md](CONTRIBUTING.md).

## What's inside

| Path | Content |
|---|---|
| [`openapi/`](openapi/openapi.json) | the checked-in, drift-gated OpenAPI contract of the worker |
| [`bruno/`](bruno/README.md) | REST scenarios, the two ways to complete a user task, the incident demo |
| [`stack/`](stack/docker-compose.yml) | the Postgres dev stack |
| [`docs/`](docs/README.md) | the Architecture Decision Records |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).

## License

[MIT](LICENSE) © Miragon GmbH.
