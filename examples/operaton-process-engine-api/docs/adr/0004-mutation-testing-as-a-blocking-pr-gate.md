# 0004 — Mutation testing as a nightly gate on the domain

- **Status:** Accepted
- **Date:** 2026-08-20
- **Amended:** 2026-10-31 — moved from a blocking PR gate to a nightly gate on the domain, to shrink the pre-merge pipeline.

## Context

Line coverage answers "was this line executed?", not "would a test notice if the code broke?". That
gap matters most for **AI-assisted tests**, which reliably chase coverage while writing weak assertions
(assert-not-null instead of assert-a-value). A blueprint that asks agents to generate tests needs a
gate that grades *assertion strength*, not just execution. Mutation testing is inherently slower than
unit tests, though, so the gate must stay a guardrail, not a tollbooth — it must not become the thing
that stalls merges.

## Decision

We run **PIT (pitest)** as a **nightly gate** with `mutationThreshold = 80`, configured in
`service/app/pom.xml` (`pitest-maven`) and run via
`./mvnw -pl service/app test-compile pitest:mutationCoverage` (after a one-time
`./mvnw -DskipTests install` so the sibling architecture-test module resolves).

- **It runs nightly, not on pull requests.** `.github/workflows/nightly*.yml` runs the gate and uploads
  the HTML report as an artifact. A red run does not block a merge; it asks for a follow-up.
- **It mutates the domain only** (`io.miragon.blueprint.domain.*`), the code that holds the rules.
  Adapters are mapping and wiring, which the unit, process and Bruno layers already exercise.
- The **kill-set** is the fast Mockito / `@WebMvcTest` / `@DataJpaTest` unit tests; the engine
  integration tests (`process.*`) and the ArchUnit tests (`architecture.*`) are excluded from
  the kill-set — they'd make every run slow and non-deterministic without adding mutation signal.

**Why 80 and not 100:** on the Kotlin codebase this repo was ported from, the ceiling was the compiler's
synthetic bytecode — *equivalent mutants* no test could kill. Plain Java emits almost none of that, so
the equivalent-mutant argument is largely gone. 80 is kept anyway: it preserves continuity of the gate
through the port (the bar never moved, so scores stay comparable), and the Java mutant population first
needs a few nightly full sweeps to establish a baseline. Raising the gate (e.g. to 90+) once those
sweeps show consistent headroom is the recorded follow-up.

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
- **Neutral:** the threshold is deliberately conservative for the port; the gate-raise (90+) waits on
  the nightly sweeps, not on new tooling.

## Implementation notes

- The scope is set in the build file of the module that holds the domain (`targetClasses`).
