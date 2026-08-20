# 0006 — Mutation testing as a blocking PR gate

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

Line coverage answers "was this line executed?", not "would a test notice if the code broke?". That
gap matters most for **AI-assisted tests**, which reliably chase coverage while writing weak assertions
(assert-not-null instead of assert-a-value). A blueprint that invites agents to generate tests needs a
gate that grades *assertion strength*, not just execution.

## Decision

We run **PIT (pitest)** as a **blocking gate** with `mutationThreshold = 80`, configured in
`service/app/build.gradle.kts` and run via `./gradlew :service:app:pitest`.

- It mutates the whole `io.miragon.blueprint.*` module and **excludes noise**: the generated
  `*ProcessApi`, the Spring bootstrap and `BikeCatalogueSeeder`, and the `adapter.inbound.cibseven.*`
  delegates/listeners (thin glue exercised only by the slow engine tests).
- The **kill-set** is the fast mockk / `@WebMvcTest` / `@DataJpaTest` unit tests; the JGiven engine
  integration tests (`process.*`) and the ArchUnit/Konsist tests (`architecture.*`) are excluded from
  the kill-set — they'd make every run slow and non-deterministic without adding mutation signal.
- It runs in **its own parallel job** in `.github/workflows/pre-merge.yml`, separate from the main
  build/test lane, fails the PR under the threshold, and uploads the HTML report as an artifact.

**Why 80 and not 100:** PIT mutates JVM bytecode, and Kotlin emits synthetic constructs (`value class`
null checks, safe-call mapping, `data class` accessors) that are *equivalent mutants* — no test can
kill them. ~23 residual survivors here are all of that kind, so 100% is unreachable without the
commercial arcmutate plugin. 80 is the honest bar.

**The 5-minute escape clause:** the gate is a guardrail, not a tollbooth. If pitest cannot finish
within a **~5-minute** budget for a given change, it may be deferred to a nightly run rather than
blocking the PR — mutation testing is inherently slower than unit tests and must not become the thing
that stalls merges. At authoring time this was theoretical: the measured wall-clock for the full run
was **~49 seconds**, comfortably inside the budget.

**What a weak test looks like** (the two patterns the gate caught in this repo's own spike): a test
that asserts too few of a DTO's fields — PIT blanks the unasserted ones (`return ""`) and every test
stays green; and a test that exercises only one branch of a boolean — the *"always return true"*
mutant is then *equivalent* to the original. Both are fixed by asserting **every** mapped field and
**both** outcomes of each branch — one assertion per outcome, not per method.

## Consequences

- **Positive:** AI-generated tests are graded on whether they'd catch a real fault; weak assertions
  surface as surviving mutants with per-mutant, inline feedback.
- **Negative / trade-offs:** slower than unit tests (hence the separate job and the escape clause); the
  threshold is capped below 100 by unavoidable Kotlin noise.
- **Neutral:** raising the bar toward 100 is a documented upgrade path — the commercial
  [arcmutate Kotlin plugin](https://docs.arcmutate.com/docs/kotlin.html) filters most of the
  equivalent-mutant noise (`+KOTLIN_NO_NULLS` and Kotlin-aware mutators) and would let the threshold rise.
