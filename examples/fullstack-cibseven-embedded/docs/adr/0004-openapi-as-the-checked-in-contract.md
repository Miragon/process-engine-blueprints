# 0004 — OpenAPI as the checked-in contract

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

The Kotlin backend and the TypeScript frontend meet at a REST boundary. That boundary can be described
three ways: (a) hand-write a spec and hope the code matches it; (b) hand-write TS types and hope they
match the backend; (c) derive one side from the other. Options (a) and (b) drift silently — the first
symptom is a runtime 400 in the browser. We want the contract to be *impossible* to desync, and we
want a new contributor to be able to read it without running anything.

## Decision

The **backend is the single source of truth**, and the contract is **generated but committed**:

1. **springdoc** serves a live OpenAPI document from the annotated controllers
   (`@Operation(operationId = …)` gives each endpoint a stable client method name).
2. `OpenApiSpecExportTest` — a code generator wearing a JUnit costume — fetches `/v3/api-docs`,
   re-serialises it **deterministically** (keys sorted, fixed two-space LF indenter, trailing newline,
   `servers` block dropped so the random test port can't cause churn) and writes
   **`openapi/openapi.json`** at the repo root. It runs inside `./gradlew build`.
3. CI regenerates the spec and runs **`git diff --exit-code`** on `openapi/openapi.json` — a **drift
   gate**. If a controller changed and the committed spec wasn't updated, the build fails.
4. **orval** (`frontend/orval.config.ts`) reads that committed JSON and generates the TS client into
   `frontend/src/shared/api/generated/`: TanStack Query hooks (through the hand-written `httpClient`
   mutator), **MSW** handlers (so integration tests are nearly free), and **zod** schemas (the submit
   form reuses the request-body schema as its resolver, so form and wire format cannot drift).
5. `npm run api:check` regenerates and `git diff --exit-code`s the generated client — the same drift
   gate on the frontend side.

## Why generated-but-committed beats the alternatives

- **vs. hand-written spec/types:** eliminates the drift class entirely — the gate fails the PR, not the
  browser.
- **vs. generated-at-build (not committed):** the committed JSON is reviewable in every PR diff (an API
  change is *visible*), the frontend can build without booting the backend, and offline/agent workflows
  keep working. The cost — a checked-in generated file — is paid down by the two drift gates that keep
  it honest.

The [`sync-api-client`](../../.claude/skills/sync-api-client/SKILL.md) skill runs the whole loop
(regenerate spec → orval → fix TS across FSD slices → `api:check` clean).

## Consequences

- **Positive:** one contract, two gated mirrors; API changes are visible in review; the frontend build
  is decoupled from a running backend.
- **Negative / trade-offs:** two generated trees live in git; forgetting to regenerate is an *expected*
  failure mode — the gate is what makes that safe, so it must never be disabled.
- **Neutral:** determinism is a hard requirement of the export test — any non-deterministic serialisation
  would make the gate flap.

## Implementation notes

Two things bite when this runs on the embedded CIB seven engine:

- **Swagger UI vs. the CIB seven webclient.** The webapp registers its own resource handlers *and* its
  own `OpenAPI` bean. `OpenApiConfiguration`'s bean is marked `@Primary` so springdoc serves ours for
  `/api/**`, and `/camunda`, `/swagger-ui.html` and `/v3/api-docs` coexist (verified at build time). If
  a future upgrade breaks that, swap to `springdoc-openapi-starter-webmvc-api` (spec only, no UI) — the
  frontend only needs `/v3/api-docs`.
- **Jackson 3 date-time.** Spring Boot 4 ships Jackson 3, which defaults `WRITE_DATES_AS_TIMESTAMPS` on,
  and the webclient serves `/api` with its own mapper that ignores global config. springdoc types the
  fields as `string/date-time`, so DTO date fields are pinned with `@JsonFormat(shape = STRING)` to keep
  payload and contract in sync. Operation ids are set explicitly (`@Operation(operationId = …)`) so the
  generated hook names stay clean and stable (e.g. `useListLeasingApplications`).
