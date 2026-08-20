# Fullstack Bike-Leasing Blueprint

> [!NOTE]
> **🚧 Work in progress.** This is a **solution template** — a reference to fork and build on, for
> our consultants and anyone else — not a product that ships. It's still being fleshed out, so parts
> may be incomplete and it may not yet fully demonstrate what it's meant to. Treat it as a
> living example, and expect it to keep evolving.

A ready-to-fork **fullstack** starting point for automating a business process end to end: a
[CIB seven](https://cibseven.org) (community fork of Camunda 7) **embedded-engine** backend, a
**React frontend**, and an **AI-agent setup** — one complete, runnable BPMN application with an
enforced architecture on *both* sides of the stack.

![MiraVelo's React frontend — the leasing applications list with each case's bike and live status, a link to the CIB seven Cockpit, and a German/English language switch](docs/assets/frontend-applications.png)

## Why this template exists

Most process-automation examples stop at the engine: a `.bpmn` file, a handful of delegates, maybe a
REST endpoint. Real projects need more — a UI customers actually use, a contract between front and
back, tests that mean something, and an architecture that doesn't rot as the process grows. This
template is our answer to *"give me a sane place to start a greenfield process-automation project"*.

It is a **proposal, not just a demo.** Fork it and you inherit a hexagonal backend, a Feature-Sliced
frontend, a committed API contract, and a full quality net — all of it **machine-enforced**, so the
structure stays intact whether the next feature is written by a consultant or an AI agent. The
guardrails (architecture tests, mutation testing, linting, typed contracts) exist precisely so that
agent-generated and hand-written code get the same fast, local "you broke a rule" feedback.

The scenario below is fictional; the architecture, the guardrails, and the end-to-end wiring are the
part you keep.

## The scenario

Meet **MiraVelo** — a (fictional) lifestyle bike brand for the quarter-life-crisis crowd: gravel
bikes for the weekends that count, road bikes for everyone who just wants to feel the asphalt.
MiraVelo sells its bikes on a **leasing model** for private and corporate customers, and this project
automates that leasing application from the first request to an active lease.

It's a made-up company, so nobody gets hurt when the DMN politely declines a 15-year-old's
application for a carbon road bike.

In the UI a customer submits an application (name, income, a bike from the catalogue), watches the
case advance on its own — the detail page polls while the engine works asynchronously — and signs the
contract or reports the handover when the process asks for it. When a bike turns out to be out of
stock, the case lands in a **back-office inbox** (`/aufgaben`), where an agent picks an alternative
and the process continues. Every business action goes through the domain; timers and the deliberately
UI-less `clarify-return` task stay engine concerns.

## What is inside

- **Backend** — Kotlin / Spring Boot 4, **hexagonal**, CIB seven 2.2.0 embedded (JavaDelegates). The
  full BPMN palette (message start, DMN, event-based gateway, timers, parallel fork/join, user tasks,
  compensation) with architecture (ArchUnit + Konsist), model validation (`bpmn-to-code`), mutation
  testing (pitest, gated at 80) and API scenarios (Bruno) enforced at build time.
- **Frontend** — React 19, **Feature-Sliced Design**, Tailwind v4 (canonical Miragon CI), TanStack
  Router/Query, a shadcn-style design system, forms with react-hook-form + zod. Layering enforced by
  steiger + ESLint + knip; MSW-backed vitest; Playwright e2e mapped 1:1 to the Bruno scenarios.
- **The contract** — springdoc generates `openapi/openapi.json`, which is **committed and
  drift-gated**; orval regenerates the frontend's typed TanStack Query client from it. No contract,
  no fullstack story.
- **AI-ready** — `AGENTS.md` + `frontend/AGENTS.md`, eight skills and two review subagents in
  `.claude/`, and the ADRs in `docs/adr/`.

## Technology choices

Every non-obvious decision is recorded as an ADR — read the *why* before changing the *what*:

- **CIB seven, embedded** — the engine runs *inside* the Spring Boot app, so delegates are plain
  beans and the process is unit-testable without a remote engine. (community Camunda 7 fork)
- **Hexagonal backend, machine-enforced** — business logic stays engine- and framework-agnostic
  behind ports; ArchUnit + Konsist fail the build on a violation — [ADR-0002](docs/adr/0002-hexagonal-architecture-for-the-backend.md)
- **Feature-Sliced Design frontend** (and why `processes/` is banned) — [ADR-0003](docs/adr/0003-feature-sliced-design-for-the-frontend.md)
- **OpenAPI as the checked-in contract** — the backend is the single source of truth; drift fails
  CI — [ADR-0004](docs/adr/0004-openapi-as-the-checked-in-contract.md)
- **Frontend stays out of the Gradle build** — npm-only, so `./gradlew build` needs no Node — [ADR-0005](docs/adr/0005-frontend-stays-out-of-the-gradle-build.md)
- **Mutation testing as a blocking PR gate** — a test that runs without asserting fails at score
  80 — [ADR-0006](docs/adr/0006-mutation-testing-as-a-blocking-pr-gate.md)
- **`AGENTS.md` as the single source** of agent instructions — [ADR-0007](docs/adr/0007-agents-md-as-the-single-source.md)
- **Fixed ports for v1, portless as the upgrade** — [ADR-0008](docs/adr/0008-fixed-ports-for-v1-portless-as-the-upgrade.md)
- **Always on the latest major** — a deliberate stance, not drift: the template shows the current
  technological stand (and AI-driven development against it), gated so bumps stay safe — [ADR-0011](docs/adr/0011-track-the-latest-major-versions.md)
- **Production-shaped from the start** — actuator health/liveness/readiness probes and a Prometheus
  scrape endpoint ship out of the box — [ADR-0012](docs/adr/0012-actuator-probes-and-prometheus-metrics.md)

## Getting started

```bash
# 1. start Postgres
docker compose -f stack/docker-compose.yml up -d

# 2. run the backend + engine (CIB seven Cockpit at http://localhost:8080/camunda, admin/admin)
./gradlew :service:app:bootRun

# 3. run the UI (http://localhost:5173)
npm --prefix frontend ci
npm --prefix frontend run dev
```

Then open <http://localhost:5173>, submit an application with the out-of-stock bike, sign the
contract, open `/aufgaben`, resolve the clarification with an available bike, and watch the detail
page's status advance on its own. See [CONTRIBUTING.md](CONTRIBUTING.md) for the full dev loop (Bruno,
Playwright, contract regeneration).

Verify the whole thing:

```bash
./gradlew build                        # arch + unit + process + model validation + spec export
git diff --exit-code openapi/openapi.json
./gradlew :service:app:pitest          # mutation score >= 80
npm --prefix frontend run verify       # format, eslint, steiger, knip, tsc, vitest
```

## Repository structure

```
fullstack-example/
├── AGENTS.md · CLAUDE.md            # AI guidance (AGENTS.md is the single source)
├── service/
│   ├── common-architecture-tests/  # ArchUnit + Konsist rules (fail the build)
│   └── app/                         # hexagonal Kotlin/Spring Boot 4 + CIB seven
├── openapi/openapi.json            # GENERATED by a test, COMMITTED, drift-gated in CI
├── frontend/                        # React + FSD (npm-only; e2e/ Playwright specs)
├── bruno/                           # 6 API scenario collections
├── tools/                           # bpmnlint + git-hook installer
├── stack/docker-compose.yml         # Postgres 18.4
├── docs/{README.md, adr/, assets/}  # ADRs (decisions) + diagrams
├── .claude/{skills/, agents/}       # 8 skills, 2 subagents
└── .github/workflows/               # pre-merge (5 parallel jobs) + nightly
```

**Stack:** Kotlin · Spring Boot 4 · CIB seven 2.2.0 · React 19 · TanStack Router/Query · Tailwind v4 ·
orval · Vite · Vitest · Playwright.

**Ports:** Postgres `5432` · backend + engine `8080` · Cockpit `8080/camunda` · spec
`8080/v3/api-docs` · actuator `8080/actuator` · Vite `5173`.

## Documentation

The README stays deliberately compact; the reasoning behind its shape lives as the Architecture
Decision Records under [`docs/adr/`](docs/adr/) — start at the [docs index](docs/README.md). Setup, the
ports, the dev loop and the manual smoke test are in [CONTRIBUTING.md](CONTRIBUTING.md).

## Contributing

Contributions are welcome — bug reports, feature ideas, docs, and code. See
[CONTRIBUTING.md](CONTRIBUTING.md) to get set up, and use
[Conventional Commits](https://www.conventionalcommits.org) for commit messages and PR titles.

## License

MIT — see [LICENSE](LICENSE).
