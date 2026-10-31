# 0006 — Mutation testing as a nightly gate on the domain

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amended:** 2026-10-31 — moved from a blocking PR gate to a nightly gate on the domain, to shrink the pre-merge pipeline.

## Context

Line coverage answers "was this line executed?", not "would a test notice if the code broke?". That
gap matters most for **AI-assisted tests**, which reliably chase coverage while writing weak assertions
(assert-not-null instead of assert-a-value). A blueprint that invites agents to generate tests needs a
gate that grades *assertion strength*, not just execution. Mutation testing is inherently slower than
unit tests, though, so the gate must stay a guardrail, not a tollbooth — it must not become the thing
that stalls merges.

## Decision

We run **PIT (pitest)** as a **nightly gate** with `mutationThreshold = 80`, configured in
`service/app/build.gradle.kts` and run via `./gradlew :service:app:pitest`.

- **It runs nightly, not on pull requests.** `.github/workflows/nightly*.yml` runs the gate and uploads
  the HTML report as an artifact. A red run does not block a merge; it asks for a follow-up.
- **It mutates the domain only** (`io.miragon.blueprint.domain.*`), the code that holds the rules.
  Adapters are mapping and wiring, which the unit, process and Bruno layers already exercise.
- The **kill-set** is the fast mockk / `@WebMvcTest` / `@DataJpaTest` / delegate unit tests; the JGiven engine
  integration tests (`adapter.process.*`) and the ArchUnit/Konsist tests (`ArchitectureTest`) are excluded from
  the kill-set — they'd make every run slow and non-deterministic without adding mutation signal.

**Why 80 and not 100:** PIT mutates JVM bytecode, and Kotlin emits synthetic constructs (`value class`
null checks, safe-call mapping, `data class` accessors) that are *equivalent mutants* — no test can
kill them. ~23 residual survivors here are all of that kind, so 100% is unreachable without the
commercial arcmutate plugin. 80 is the honest bar.

**What a weak test looks like** (the two patterns the gate caught in this repo's own spike): a test
that asserts too few of a DTO's fields — PIT blanks the unasserted ones (`return ""`) and every test
stays green; and a test that exercises only one branch of a boolean — the *"always return true"*
mutant is then *equivalent* to the original. Both are fixed by asserting **every** mapped field and
**both** outcomes of each branch — one assertion per outcome, not per method.

## Consequences

- **Positive:** AI-generated tests are graded on whether they'd catch a real fault; weak assertions
  surface as surviving mutants with per-mutant, inline feedback, in the nightly report.
- **Negative / trade-offs:** a weak test reaches `main` and shows up the next night at the earliest, and
  weak tests of adapters (an unasserted DTO field, one branch of a mapper) are no longer graded. The
  domain is small, so one surviving mutant moves the score by several points. The threshold is capped
  below 100 by unavoidable JVM noise.
- **Neutral:** raising the bar toward 100 is a documented upgrade path — the commercial
  [arcmutate Kotlin plugin](https://docs.arcmutate.com/docs/kotlin.html) filters most of the
  equivalent-mutant noise (`+KOTLIN_NO_NULLS` and Kotlin-aware mutators) and would let the threshold rise.

## Implementation notes

- The scope is set in the build file of the module that holds the domain (`targetClasses`).
