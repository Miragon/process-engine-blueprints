# Documentation

Deeper context that the [README](../README.md) deliberately leaves out — the *why* behind the repo's
shape. This folder holds the **Architecture Decision Records** (the decisions) and the **diagrams**.

An ADR captures a **decision** — dated, numbered, and effectively immutable once accepted (a reversal
gets a new ADR, not an edit). Procedures are not decisions, so they live elsewhere: setup, the ports,
the dev loop and the smoke test in [CONTRIBUTING.md](../CONTRIBUTING.md); the day-to-day workflow and
the skills in [AGENTS.md](../AGENTS.md). Where a procedure has a decision behind it, that reasoning is
in the relevant ADR's own Decision section (e.g. the contract flow in ADR-0004, the mutation gate in
ADR-0006).

## Architecture Decision Records

Each non-obvious decision is recorded as an ADR in [`adr/`](adr/), in a MADR/Nygard-lite format
(Status · Context · Decision · Consequences). They are numbered from 0001, never renumbered, and
copied from [`adr/0000-adr-template.md`](adr/0000-adr-template.md). Write a new one with the
`create-adr` skill.

| ADR | Decision |
|---|---|
| [0001](adr/0001-record-architecture-decisions.md) | Record architecture decisions (one Markdown file per decision). |
| [0002](adr/0002-hexagonal-architecture-for-the-backend.md) | Hexagonal architecture for the backend, machine-enforced by ArchUnit + Konsist. |
| [0003](adr/0003-feature-sliced-design-for-the-frontend.md) | Feature-Sliced Design for the frontend (and why `processes/` is banned). |
| [0004](adr/0004-openapi-as-the-checked-in-contract.md) | OpenAPI as the checked-in, drift-gated contract between backend and frontend. |
| [0005](adr/0005-frontend-stays-out-of-the-gradle-build.md) | The frontend stays out of the Gradle build (npm-only). |
| [0006](adr/0006-mutation-testing-as-a-blocking-pr-gate.md) | Mutation testing as a blocking PR gate. |
| [0007](adr/0007-agents-md-as-the-single-source.md) | `AGENTS.md` as the single source of agent instructions. |
| [0008](adr/0008-fixed-ports-for-v1-portless-as-the-upgrade.md) | Fixed ports for v1, portless as the upgrade path. |
| [0009](adr/0009-two-test-layers-bruno-and-playwright.md) | Two test layers: Bruno API scenarios and Playwright browser journeys. |
| [0010](adr/0010-two-architecture-test-tools-archunit-and-konsist.md) | Two architecture-test tools: ArchUnit (bytecode) and Konsist (source). |
| [0011](adr/0011-track-the-latest-major-versions.md) | Deliberately track the latest major versions across the stack. |
| [0012](adr/0012-actuator-probes-and-prometheus-metrics.md) | Actuator health/liveness/readiness probes and Prometheus metrics, exposed out of the box. |

## Diagrams

- [`assets/bike-leasing.png`](assets/bike-leasing.png) — the BPMN process at a glance.
- [`assets/frontend-applications.png`](assets/frontend-applications.png) — the React frontend's applications list.
