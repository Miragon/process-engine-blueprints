# 0005 — The frontend stays out of the Gradle build

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

A fullstack repo can wire the frontend into the backend build — e.g. a Gradle plugin that installs
Node, runs `npm ci`, and bundles `dist/` into the Spring Boot jar. That makes `./gradlew build`
produce a single deployable, but it also makes every backend build pay for a Node toolchain, couples
two release cadences, and serialises two ecosystems that have nothing to say to each other except the
committed OpenAPI contract ([ADR-0004](0004-openapi-as-the-checked-in-contract.md)).

## Decision

The frontend is **npm-only** and lives entirely outside Gradle. `settings.gradle.kts` includes only
`service:common-architecture-tests` and `service:app`, with an explicit comment that `frontend/` is
npm-only on purpose and a pointer to this ADR.

- **`./gradlew build` needs no Node** — it compiles Kotlin, runs the JVM tests (including the
  `OpenApiSpecExportTest` that writes the contract), and never touches `frontend/`.
- The frontend has its own lifecycle: `npm run build` (`tsc --noEmit && vite build`), `npm run verify`
  (format, eslint, steiger, knip, tsc, vitest), `npm run e2e` (Playwright).
- The two sides communicate **only** through the committed `openapi/openapi.json`, so neither build has
  to invoke the other.
- **CI runs the jobs in parallel** — a JVM `build` lane and a Node/Bruno lane in
  `.github/workflows/pre-merge.yml` — because there is no build-time dependency between them.

## Consequences

- **Positive:** fast, hermetic backend builds; no Node in the JVM toolchain; independent versioning and
  parallel CI; a frontend developer never runs Gradle and vice versa.
- **Negative / trade-offs:** `./gradlew build` does **not** yield one all-in-one artifact — deployment
  ships two artifacts (a jar and the static `dist/`); the contract must be regenerated and committed to
  hand it across the gap.
- **Neutral:** the boundary is the OpenAPI file, which each side gates independently.
