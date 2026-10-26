# 0004 — OpenAPI as the checked-in contract

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

The Kotlin backend exposes a REST API that other parties consume — API clients, generated SDKs,
documentation tooling, and integration tests. That boundary can be described three ways: (a) hand-write
a spec and hope the code matches it; (b) let each consumer hand-write its own types and hope they match
the backend; (c) derive the contract from the code. Options (a) and (b) drift silently — the first
symptom is a runtime 400 at the caller. We want the contract to be *impossible* to desync, and we want
a new contributor to be able to read it without running anything.

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

The committed JSON is the **artifact any consumer builds against**: an API client or generated SDK, a
docs renderer, or a contract/mock for integration tests. Consumers regenerate from the checked-in file,
so they never need a running backend to build, and every API change is visible in the PR diff.

## Why generated-but-committed beats the alternatives

- **vs. hand-written spec/types:** eliminates the drift class entirely — the gate fails the PR, not the
  caller at runtime.
- **vs. generated-at-build (not committed):** the committed JSON is reviewable in every PR diff (an API
  change is *visible*), consumers can build without booting the backend, and offline/agent workflows
  keep working. The cost — a checked-in generated file — is paid down by the drift gate that keeps it
  honest.

## Consequences

- **Positive:** one contract, a single gated source of truth; API changes are visible in review; every
  consumer is decoupled from a running backend.
- **Negative / trade-offs:** a generated file lives in git; forgetting to regenerate is an *expected*
  failure mode — the gate is what makes that safe, so it must never be disabled.
- **Neutral:** determinism is a hard requirement of the export test — any non-deterministic serialisation
  would make the gate flap.

## Implementation notes

Two things to know when this runs on the embedded Fluxnova engine:

- **Swagger UI next to the Fluxnova webapp.** The webapp lives under `/fluxnova` and registers no
  `OpenAPI` bean of its own, so `OpenApiConfiguration`'s bean needs no `@Primary` and `/fluxnova`,
  `/swagger-ui.html` and `/v3/api-docs` coexist (verified against the running app). If a future upgrade
  breaks that, swap to `springdoc-openapi-starter-webmvc-api` (spec only, no UI) — a consumer only needs
  `/v3/api-docs`.
- **Jackson 3 date-time.** Fluxnova 3.0.1's `engine-rest` pins the pre-release
  `tools.jackson.datatype:jackson-datatype-jsr310:3.0.0-rc2`. Spring's JSON converter auto-discovers that
  module, and on Jackson 3.1 it fails every `java.time` value with a `NoSuchFieldError`. The module is
  excluded in `service/app/build.gradle.kts` — the upstream fix
  ([finos/fluxnova-bpm-platform#244](https://github.com/finos/fluxnova-bpm-platform/issues/244)) — because
  Jackson 3 handles `java.time` natively. springdoc types the fields as `string/date-time`, and DTO date
  fields stay pinned with `@JsonFormat(shape = STRING)` to keep payload and contract in sync. Operation ids are set explicitly (`@Operation(operationId = …)`) so the
  generated client method names stay clean and stable (e.g. `listLeasingApplications`).
