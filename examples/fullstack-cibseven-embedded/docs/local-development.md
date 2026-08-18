# Local development

## Prerequisites

- JDK 21, Node ≥ 22.12, Docker (or Podman) for Postgres.
- One-time: `npm --prefix tools ci && npm --prefix tools run hooks:install` (BPMN lint + git hooks),
  and `npm --prefix frontend ci`.

## Ports (one source of truth)

| What | Port |
|---|---|
| Postgres | 5432 |
| Backend (REST + `/engine-rest`) | 8080 |
| CIB seven Cockpit / webapps | 8080/camunda (admin/admin) |
| OpenAPI spec · Swagger UI | 8080/v3/api-docs · 8080/swagger-ui.html |
| Vite dev server | 5173 |

Under Conductor these are fixed and the workspace runs `nonconcurrent` (one app at a time). See
[ADR-0008](adr/0008-fixed-ports-for-v1-portless-as-the-upgrade.md).

## The dev loop

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres
./gradlew :service:app:bootRun                      # backend + engine on :8080
npm --prefix frontend run dev                       # UI on :5173 (Vite proxies /api, /v3/api-docs,
                                                    #   /engine-rest, /camunda to :8080 — no CORS)
```

Because Vite proxies to the backend, the browser sees one origin and **no CORS code exists on the
production path**. `DevCorsConfiguration` ships inactive (`@Profile("dev")`) as a documented escape
hatch you should not need.

## Manual smoke test

Open <http://localhost:5173>:

1. submit an application choosing **Mountain Trail 600 (BIKE-OOS)** — deliberately out of stock;
2. sign the contract; the order finds it unavailable and parks the case;
3. open `/aufgaben`, resolve the clarification choosing **Gravel Explorer 900**;
4. back on the detail page, report the handover and watch the status advance on its own (the
   detail page polls while the case is non-terminal — this is the eventual-consistency lesson, not a
   hack).

Confirm <http://localhost:8080/camunda> (admin/admin) and <http://localhost:8080/swagger-ui.html>
both load.

## Automated checks

```bash
# backend: architecture, unit, process, model validation, spec export
./gradlew build
git diff --exit-code openapi/openapi.json
./gradlew :service:app:pitest                 # mutation score >= 80

# frontend: format, eslint, steiger, knip, typecheck, vitest
npm --prefix frontend run verify
npm --prefix frontend run api:check           # generated client is in sync

# end-to-end against a running stack
cd bruno && npx --yes @usebruno/cli run . --env local -r    # 6 API scenario folders
npm --prefix frontend run e2e                               # 5 Playwright specs (chromium)

# BPMN
npm --prefix tools run lint:bpmn
```

## Changing the API

Edit a controller, then run the **`sync-api-client`** skill (or manually: regenerate the spec via the
`OpenApiSpecExportTest`, `npm --prefix frontend run api:generate`, fix the resulting TypeScript, and
confirm both drift gates are green). See [api-contract.md](api-contract.md).

## Changing the process

Edit the `.bpmn` under `service/app/src/main/resources/bpmn/`, then regenerate the typed process API
with `./gradlew generateBpmnModels`. Never hand-edit the generated `adapter/process/*ProcessApi.kt`.
`bpmnlint` runs on staged models via the pre-commit hook.
