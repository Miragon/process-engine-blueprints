# Fluxnova Bike-Leasing Blueprint

> [!NOTE]
> **🚧 Work in progress.** A **solution template** to fork and build on — not a product that ships.
> Expect it to keep evolving.

A ready-to-fork **starting point** for automating a business process on
[Fluxnova](https://fluxnova.finos.org) (the FINOS-governed fork of Camunda 7) with an **embedded engine**
and Spring Boot — one complete, runnable, production-shaped BPMN service. The engine runs **inside** the
service: no remote engine, no separate engine container.

> [!TIP]
> **Coming from CIB seven, Operaton or Camunda 7?** This is the Fluxnova port of the
> [CIB seven embedded example](https://github.com/miragon-blueprints/cibseven-embedded-example) — same
> process, same REST API, same tests. What changed, what deliberately stayed (`camunda:` in the models)
> and where it came from is in [`docs/fluxnova-migration.md`](docs/fluxnova-migration.md).

<!-- variant:blueprint -->
## 🧭 Pick your stack

| | [`kotlin-gradle/`](kotlin-gradle/README.md) | [`java-maven/`](java-maven/README.md) |
|---|---|---|
| **Stack** | Kotlin 2.4 · Gradle | Java 21 · Maven |
| **Choose it when** | you are free to choose — **our recommendation for a modern stack** | Java + Maven is your team's or company's standard, or you are in a training |

Both run the same process, expose the same REST contract and pass the same end-to-end scenarios. Each
directory is self-contained — build, code, process models and schema — and CI keeps the models and
configuration of the two identical, so only the language and the build tool differ. Building on one?
[Turn the repo into a single-stack starter](docs/starter.md) with one command.
<!-- /variant:blueprint -->

## 🚲 The scenario

**MiraVelo** is a (fictional) bike brand that sells on a **leasing model**. This service automates a
leasing application from the first request to an active lease — and deliberately walks through the
**broad palette of BPMN elements you meet in real processes**, not just a happy-path service task:

![The bike-leasing process](docs/assets/bike-leasing.png)

- **message start event**, **service tasks** (JavaDelegates) and a **DMN business-rule task**
- **embedded sub-process** with an **event-based gateway** and a non-interrupting **reminder timer**
- **parallel fork/join**, and a **user task with a Fluxnova Form** — completable in the Tasklist or via REST
- **execution** and **task listeners**, wired as Spring beans like the delegates
- **compensation / SAGA** handlers guarded by **error** and **escalation** boundary events
- **call activity**, **message event sub-process** (withdrawal) and a **terminate end event**

## 🚀 Run it

You need **JDK 21** and **Docker** (or Podman).

**1. Start Postgres**

```bash
docker compose -f stack/docker-compose.yml up -d
```

**2. Start the service** on :8080

<!-- variant:kotlin-gradle -->
```bash
cd kotlin-gradle && ./gradlew :service:app:bootRun
```
<!-- /variant:kotlin-gradle -->
<!-- variant:blueprint -->
or
<!-- /variant:blueprint -->
<!-- variant:java-maven -->
```bash
cd java-maven && ./mvnw -DskipTests install && ./mvnw -pl service/app spring-boot:run
```
<!-- /variant:java-maven -->

**3. Use it** — open the Tasklist / Monitoring / Admin webapps at <http://localhost:8080/fluxnova>
(admin/admin) or the Swagger UI at <http://localhost:8080/swagger-ui.html>, or drive the whole process
over REST:

```bash
cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r
```

Fluxnova calls the classic Cockpit **Monitoring**. The Tasklist starts without a task filter — click
*Add a simple filter* once to see the open user tasks.

## 📂 What's where

<!-- variant:kotlin-gradle variant:nested -->
- [`kotlin-gradle/`](kotlin-gradle/README.md) — the service in Kotlin + Gradle, its build and quality gates
  <!-- /variant:kotlin-gradle -->
  <!-- variant:java-maven variant:nested -->
- [`java-maven/`](java-maven/README.md) — the service in Java 21 + Maven, its build and quality gates
  <!-- /variant:java-maven -->
- [`openapi/`](openapi/openapi.json) — the checked-in, drift-gated OpenAPI contract
- [`bruno/`](bruno/README.md) — the REST scenarios, a walk-through by hand, the two ways to complete a user task, the incident demo
- [`stack/`](stack/docker-compose.yml) — the Postgres dev stack
- [`docs/`](docs/README.md) — the Architecture Decision Records and the [Fluxnova migration notes](docs/fluxnova-migration.md)
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — setup, ports, containers and the PR workflow

## 🚩 Known limitations

- **Spring Boot line.** Fluxnova 3.0.1 is built and tested upstream against Spring Boot 4.0.5. This
  blueprint runs it on Spring Boot 4.1.1 — covered by this repo's own test and Bruno suites, not by
  upstream.
- **Jackson workaround.** Fluxnova 3.0.1's `engine-rest` pins a pre-release Jackson java.time module
  that breaks date serialisation on Jackson 3.1. The build excludes it
  ([finos/fluxnova-bpm-platform#244](https://github.com/finos/fluxnova-bpm-platform/issues/244)); the
  exclusion can go once Fluxnova 3.1.0 is on Maven Central.
- **`camunda:` stays in the models and their tooling.** See
  [Compatibility namespaces that stay](docs/fluxnova-migration.md#7-compatibility-namespaces-that-stay).
- **Model metadata was set by hand.** The BPMN, DMN and form headers carry the values the official
  Fluxnova migrator writes; the files were not round-tripped through the Fluxnova Modeler GUI.
- **Not production-hardened.** Fixed ports, `admin`/`admin` credentials and an unauthenticated
  `/engine-rest` are development defaults.

## 🤝 Contributing

Contributions are welcome. Open an issue before a substantial change, keep the CI gates green and use
[Conventional Commits](https://www.conventionalcommits.org). The details are in
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## 📄 License

Licensed under the [MIT License](./LICENSE).
