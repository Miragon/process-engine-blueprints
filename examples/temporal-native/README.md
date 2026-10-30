# Temporal Bike-Leasing Blueprint

> [!WARNING]
> **Beta, not BPMN.** This example has no process model. It runs the MiraVelo flow of the other blueprints
> as [Temporal](https://temporal.io) workflows in Kotlin, to compare the two approaches. It may change or
> disappear, and its dev server keeps everything in memory: restart it and all cases are gone.

The same domain, application services, REST API and database as the `zeebe-native` blueprint,
with Temporal behind the adapters. [ADR-0014](docs/adr/0014-temporal-as-a-beta-next-to-the-bpmn-engines.md)
maps every BPMN concept to its Temporal counterpart and lists the deliberate differences.

## The process

![The bike-leasing process](docs/assets/bike-leasing.png)

The picture shows the flow as the sibling blueprints model it in BPMN. Here it is documentation only:
what runs is the code.

**MiraVelo** is a (fictional) bike brand that sells on a leasing model. A credit rule decides on the
application, the customer signs within 14 days (reminded after 7), then the bike is ordered and the
policy issued in parallel. An unavailable bike leads to a clarification with the customer. After the
handover the 14-day withdrawal period runs, then the leasing is active. The customer can withdraw at
any time, which reverses every completed step.

The flow is [`BikeLeasingWorkflowImpl`](kotlin-gradle/service/app/src/main/kotlin/io/miragon/blueprint/adapter/process/BikeLeasingWorkflowImpl.kt).

## Quick start

Requirements: **JDK 21** and **Docker** (or Podman).

```bash
docker compose -f stack/docker-compose.yml up -d
cd kotlin-gradle && ./gradlew :service:app:bootRun
```

| What | URL |
|---|---|
| Temporal UI | <http://localhost:8233> |
| Swagger UI | <http://localhost:8081/swagger-ui.html> |

Drive the process over REST with the [Bruno](bruno/README.md) scenarios: `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r --exclude-tags=demo-data`.
Setup, ports and containers are in [`CONTRIBUTING.md`](CONTRIBUTING.md).

### Clarify a return

After a withdrawal the order cancellation waits for the supplier's answer. There is no endpoint for it:
send the signal in the Temporal UI on the workflow `<applicationId>-cancel-order`, or with the CLI:

```bash
docker compose -f stack/docker-compose.yml exec temporal \
  temporal workflow signal --workflow-id <applicationId>-cancel-order --name returnClarified
```

### Watch a dealer outage

Submit a request for the bike `BIKE-FAIL`, sign the contract, and open the workflow in the Temporal UI:
`orderBike` stays a pending activity and retries every 10 seconds. The application can still be withdrawn.

## What's inside

| Path | Content |
|---|---|
| [`kotlin-gradle/`](kotlin-gradle/README.md) | the service |
| [`openapi/`](openapi/openapi.json) | the checked-in, drift-gated OpenAPI contract |
| [`bruno/`](bruno/README.md) | the REST scenarios |
| [`stack/`](stack/docker-compose.yml) | the Temporal dev server and Postgres |
| [`docs/`](docs/README.md) | the Architecture Decision Records: why the repo is shaped this way |

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) and use [Conventional Commits](https://www.conventionalcommits.org).

## License

Licensed under the [MIT License](./LICENSE).
