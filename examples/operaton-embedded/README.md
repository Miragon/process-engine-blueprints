# Operaton Bike-Leasing Blueprint

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A solution template to fork for automating a business process on [Operaton](https://operaton.org)
(the community-driven fork of Camunda 7): one complete, runnable, production-shaped BPMN service on an
**embedded engine** with Spring Boot. It is headless — driven over REST, with the Operaton webapps for
the human steps — and still a work in progress.

<!-- variant:blueprint -->
## Pick your stack

| | [`kotlin-gradle/`](kotlin-gradle/README.md) | [`java-maven/`](java-maven/README.md) |
|---|---|---|
| **Stack** | Kotlin 2.4 · Gradle | Java 21 · Maven |
| **Choose it when** | you are free to choose — **our recommendation for a modern stack** | Java + Maven is your team's or company's standard, or you are in a training |

Both run the same process behind the same REST contract, and CI keeps their models and configuration
identical — building on one? [Turn the repo into a single-stack starter](docs/starter.md).
<!-- /variant:blueprint -->

## The process

![The bike-leasing process](docs/assets/bike-leasing.png)

**MiraVelo** is a fictional bike brand that sells on a leasing model. The service takes a leasing
application from the first request to an active lease and deliberately covers the BPMN elements you
meet in real processes: a message start event, JavaDelegate service tasks and a DMN business-rule
task, an embedded sub-process with an event-based gateway and a non-interrupting reminder timer, a
parallel fork/join, a user task with a Camunda Form, execution and task listeners, compensation (SAGA)
guarded by error and escalation boundary events, a call activity, a withdrawal message event
sub-process and a terminate end event.

## Quick start

Requires JDK 21 and Docker (or Podman).

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres + EnterpriseGlue The Bridge
# variant:kotlin-gradle
cd kotlin-gradle && ./gradlew :service:app:bootRun   # service + embedded engine on :8080
# /variant:kotlin-gradle
# variant:java-maven
cd java-maven && ./mvnw -DskipTests install && ./mvnw -pl service/app spring-boot:run   # service + embedded engine on :8080
# /variant:java-maven
```

| What | URL | Login |
|---|---|---|
| Operaton Cockpit / Tasklist | <http://localhost:8080/operaton> | admin / admin |
| Swagger UI | <http://localhost:8080/swagger-ui.html> | |
| [EnterpriseGlue The Bridge](https://github.com/EnterpriseGlue/enterpriseglue-the-bridge-oss) (additional UI) | <http://localhost:8081> | admin@enterpriseglue.com / adminadmin |

In The Bridge, register the engine under Platform Settings → Engines with base URL
`http://host.docker.internal:8080/engine-rest`.
Drive the whole process over REST: `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r`.
Tests, ports and containers are in [CONTRIBUTING.md](CONTRIBUTING.md).

## What's inside

| Path | Content |
|---|---|
| [`openapi/`](openapi/openapi.json) | the checked-in, drift-gated OpenAPI contract |
| [`bruno/`](bruno/README.md) | the REST scenarios, the two ways to complete a user task, the incident demo |
| [`stack/`](stack/docker-compose.yml) | the dev stack: Postgres + EnterpriseGlue The Bridge |
| [`docs/`](docs/README.md) | the Architecture Decision Records: why the repo is shaped this way |

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).

## License

Licensed under the [MIT License](LICENSE).
