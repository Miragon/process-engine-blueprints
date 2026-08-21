# 0003 — OpenAPI as the checked-in contract

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

The Kotlin backend exposes a REST boundary that other systems — a UI, another service, a test suite, an
AI agent — consume. That boundary can be described two ways: (a) hand-write a spec and hope the code
matches it, or (b) derive the spec from the code. Option (a) drifts silently — the first symptom is a
runtime 400 in a consumer. We want the contract to be *impossible* to desync from the implementation,
and we want a new contributor to be able to read it without running anything.

## Decision

The **backend is the single source of truth**, and the contract is **generated but committed**:

1. **springdoc** serves a live OpenAPI document from the annotated controllers
   (`@Operation(operationId = …)` gives each endpoint a stable, well-named operation id).
2. `OpenApiSpecExportTest` — a code generator wearing a JUnit costume — fetches `/v3/api-docs`,
   re-serialises it **deterministically** (keys sorted, fixed two-space LF indenter, trailing newline,
   `servers` block dropped so the random test port can't cause churn) and writes
   **`openapi/openapi.json`** at the repo root. It runs inside `./gradlew build`.
3. CI regenerates the spec and runs **`git diff --exit-code`** on `openapi/openapi.json` — a **drift
   gate**. If a controller changed and the committed spec wasn't updated, the build fails.

Any consumer — a generated client, a mock server, a contract test — reads that committed JSON. Because
the spec is a checked-in file rather than a live endpoint, a consumer can be built without booting the
backend, and offline/agent workflows keep working.

## Why generated-but-committed beats the alternatives

- **vs. a hand-written spec:** eliminates the drift class entirely — the gate fails the PR, not the
  consumer at runtime.
- **vs. generated-at-build (not committed):** the committed JSON is reviewable in every PR diff (an API
  change is *visible*), consumers can build without a running backend, and offline/agent workflows keep
  working. The cost — a checked-in generated file — is paid down by the drift gate that keeps it honest.

## Consequences

- **Positive:** one contract, verifiable and reviewable; API changes are visible in review; consumers
  are decoupled from a running backend.
- **Negative / trade-offs:** a generated file lives in git; forgetting to regenerate is an *expected*
  failure mode — the gate is what makes that safe, so it must never be disabled.
- **Neutral:** determinism is a hard requirement of the export test — any non-deterministic serialisation
  would make the gate flap.

## Implementation notes

Two things bite when this runs on the embedded Operaton engine:

- **Swagger UI vs. the Operaton webapp.** The webapp registers its own resource handlers *and* its own
  `OpenAPI` bean. `OpenApiConfiguration`'s bean is marked `@Primary` so springdoc serves ours for
  `/api/**`, and `/operaton`, `/swagger-ui.html` and `/v3/api-docs` coexist (verified at build time). If
  a future upgrade breaks that, swap to `springdoc-openapi-starter-webmvc-api` (spec only, no UI) — a
  consumer only needs `/v3/api-docs`.
- **Jackson 3 date-time.** Spring Boot 4 ships Jackson 3, which defaults `WRITE_DATES_AS_TIMESTAMPS` on,
  and the webapp serves its API with its own mapper that ignores global config. springdoc types the
  fields as `string/date-time`, so DTO date fields are pinned with `@JsonFormat(shape = STRING)` to keep
  payload and contract in sync. Operation ids are set explicitly (`@Operation(operationId = …)`) so
  generated consumer names stay clean and stable (e.g. `listLeasingApplications`).
</content>
