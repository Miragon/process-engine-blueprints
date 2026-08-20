# Contributing

Thanks for your interest in the fullstack bike-leasing blueprint! Contributions of all kinds are
welcome — bug reports, feature ideas, docs, and code.

## Getting started

```bash
git clone git@github.com:miragon-blueprints/fullstack-example.git
cd fullstack-example
npm --prefix tools ci && npm --prefix tools run hooks:install   # BPMN lint + git hooks
npm --prefix frontend ci                                        # frontend dependencies
```

You need **JDK 21**, **Node ≥ 22.12**, and **Docker (or Podman)** for Postgres. The backend and
frontend are two separate toolchains on purpose — the frontend stays out of the Gradle build (see
[ADR-0005](docs/adr/0005-frontend-stays-out-of-the-gradle-build.md)).

Run the whole stack locally:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres
./gradlew :service:app:bootRun                      # backend + engine on :8080
npm --prefix frontend run dev                       # UI on :5173 (proxies /api to :8080)
```

See [docs/local-development.md](docs/local-development.md) for the ports, the manual smoke test, and
the full dev loop.

## Scripts

```bash
# backend
./gradlew build                         # arch + unit + process + model validation + spec export
./gradlew :service:app:pitest           # mutation score >= 80
./gradlew generateBpmnModels            # regenerate the typed process API after editing a .bpmn

# frontend
npm --prefix frontend run verify        # format, eslint, steiger (FSD), knip, tsc, vitest
npm --prefix frontend run api:generate  # regenerate the typed client from openapi.json
npm --prefix frontend run e2e           # Playwright specs (chromium)

# BPMN
npm --prefix tools run lint:bpmn        # bpmnlint the .bpmn models
```

## Ground rules

- **Start from an issue.** Every change traces back to one — open an issue (or pick an existing one)
  and agree on the approach *before* you write code, then reference it in the PR (`Closes #123`).
  This keeps substantial changes discussed up front and the history navigable.
- **Read [`AGENTS.md`](AGENTS.md) first.** It is the single source of guidance for humans and AI
  agents alike; [`frontend/AGENTS.md`](frontend/AGENTS.md) covers the React app's toolchain.
- **Conventional Commits.** Commit messages and PR titles follow
  [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`,
  `refactor:`, `test:`, `chore:`). Write everything in **English**.
- **Keep the gates green.** The architecture (ArchUnit + Konsist), contract-drift, mutation (≥ 80)
  and FSD gates run in CI on every PR. They are fitness functions, not style guides — a violation
  fails the build.
- **Add tests.** This is a TDD codebase; match the test style to the layer (see `AGENTS.md`).
  Mutation testing means a test that runs without asserting will fail CI.
- **Changing the API or the process?** Use the `sync-api-client` / `automate-process` skills so the
  committed `openapi/openapi.json` contract and the generated code stay in sync.

## Before opening a PR

```bash
./gradlew build
git diff --exit-code openapi/openapi.json    # the API contract must not drift
./gradlew :service:app:pitest                # mutation score >= 80
npm --prefix frontend run verify
npm --prefix frontend run api:check          # the generated client is in sync
```

All of these run in CI on every pull request (JDK 21 / Node ≥ 22.12). The `review-vertical-slice`
subagent is a good pre-flight check that a feature is wired end to end across the whole stack.

## Reporting bugs / requesting features

Open an issue. For a process- or contract-related bug, attaching the relevant `.bpmn` model or the
`openapi.json` diff is the fastest path to a fix.
