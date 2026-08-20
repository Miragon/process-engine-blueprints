# Documentation

Deeper context that the [README](../README.md) deliberately leaves out. The README is the compact
entry point; this folder is where individual decisions and workflows are explained a level down.

Two kinds of document live here, and the split is intentional:

- **ADRs** capture a **decision** — the *why* behind a structural choice. They are dated, numbered,
  and effectively immutable once accepted (a reversal gets a new ADR, not an edit).
- **Guides** are **how-to / reference** — the *how* of running or changing something. They evolve
  freely with the code.

So not everything is an ADR because most of this folder isn't a decision: "how to run the dev loop"
or "how the contract is regenerated" is a procedure, not a choice to be defended. Where a guide has a
decision behind it, it links the ADR (e.g. the API-contract guide points at ADR-0004).

## Topic guides

| Guide | What it covers |
|---|---|
| [local-development.md](local-development.md) | Prerequisites, ports, the dev loop, the manual smoke test, and how to change the API or the process. |
| [api-contract.md](api-contract.md) | How `openapi/openapi.json` is generated, committed, and drift-gated — and how a backend change reaches the typed frontend client. |
| [mutation-testing.md](mutation-testing.md) | Why PIT mutation testing gates PRs at 80, and how to read and improve a mutation score. |

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

## Diagrams

- `bike-leasing.png` — the BPMN process at a glance.
- `frontend-applications.png` — the React frontend's applications list.
