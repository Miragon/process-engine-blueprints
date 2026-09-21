# 0004 — Mutation testing as a blocking PR gate

- **Status:** Accepted
- **Date:** 2026-08-20

## Context

Line coverage answers "was this line executed?", not "would a test notice if the code broke?". That
gap matters most for **AI-assisted tests**, which reliably chase coverage while writing weak assertions
(assert-not-null instead of assert-a-value). A blueprint that invites agents to generate tests needs a
gate that grades *assertion strength*, not just execution. Mutation testing is inherently slower than
unit tests, though, so the gate must stay a guardrail, not a tollbooth — it must not become the thing
that stalls merges.

## Decision

We run **PIT (pitest)** as a **blocking gate** with `mutationThreshold = 80`, configured in
`service/example-service/pom.xml` and run via
`mvn -pl service/example-service -am test-compile org.pitest:pitest-maven:mutationCoverage`.

- **On PRs it runs diff-scoped.** `.github/workflows/pre-merge.yml` computes the worker `*.java` files
  the PR changed (from `pull_request.base.sha`), maps them to `io.miragon.blueprint.<pkg>.<File>*`, and
  passes them to pitest via `-Ppit-diff -DtargetClasses=…`. It blocks the PR on the changed classes' score
  but stays off the critical path, and is skipped when a PR touches no worker code.
- **Nightly runs the full sweep.** `.github/workflows/nightly.yml` mutates the whole
  `io.miragon.blueprint.*` module with no diff scoping — the authoritative gate-80 run — and
  uploads the HTML report as an artifact.
- Both runs **exclude noise**: the generated `*ProcessApi`, the Spring bootstrap and demo seeding, and
  the `adapter.inbound.operaton.*` external-task workers (thin glue exercised only by the slow engine
  tests).
- The **kill-set** is the fast Mockito / `@WebMvcTest` / `@DataJpaTest` unit tests; the engine
  integration tests and the ArchUnit tests (`architecture.*`) are excluded from the kill-set —
  they'd make every run slow and non-deterministic without adding mutation signal.

**Why 80 and not 100:** PIT mutates JVM bytecode, and some of that bytecode is synthetic or defensive —
generated `record` accessors and `equals`/`hashCode`/`toString`, boilerplate null/precondition checks —
where a surviving mutant is *equivalent* and no test can kill it. Chasing the final points means
fighting those equivalents rather than closing real gaps, so 80 is the honest bar.

**What a weak test looks like:** a test that asserts too few of a DTO's fields — PIT blanks the
unasserted ones (`return ""`) and every test stays green; and a test that exercises only one branch of a
boolean — the *"always return true"* mutant is then *equivalent* to the original. Both are fixed by
asserting **every** mapped field and **both** outcomes of each branch — one assertion per outcome, not
per method.

## Consequences

- **Positive:** AI-generated tests are graded on whether they'd catch a real fault; weak assertions
  surface as surviving mutants with per-mutant, inline feedback — at PR time, on the code the PR
  changed.
- **Negative / trade-offs:** mutation testing is slower than unit tests, hence the diff-scoped PR run
  and the separate nightly full sweep. Gate-80 over a single changed class is stricter granularity than
  over the module, so a PR touching only an equivalent-mutant-heavy record can dip below
  80 (fix: assert every field and both branches, or exclude the class). The threshold is capped below
  100 by unavoidable synthetic-bytecode noise.
- **Neutral:** raising the bar toward 100 is a documented upgrade path — pruning the residual
  equivalent mutants (excluding generated accessors and boilerplate, or narrowing the active mutator
  set) would let the threshold rise, at diminishing returns.

## Implementation notes

- `service/example-service/pom.xml` configures the `pitest-maven` plugin; the `pit-diff` profile takes
  an optional `targetClasses` (`-DtargetClasses=…`, no override → full-module scope, used by the
  nightly sweep and local runs) and sets `failWhenNoMutations` to `false` so a PR whose changed classes
  are all excluded/non-mutable doesn't fail the build.
- Do **not** rename the `Mutation testing (PIT, gate 80)` job in `pre-merge.yml` — it is the
  branch-protection required check.
