# 0007 — Two architecture-test tools: ArchUnit (bytecode) and Konsist (source)

- **Status:** Accepted
- **Date:** 2026-08-20

> **Update (2026-09-15) — Java/Maven port.** When the worker moved from Kotlin/Gradle to Java 21 + Maven,
> **Konsist** was dropped: it inspects the Kotlin PSI tree and has no Java source model, so it cannot run
> against a Java codebase. Its two source-shape rules did not go away — they moved to
> **`maven-checkstyle-plugin`** (`config/checkstyle/checkstyle.xml`), which reads Java source and fails
> `mvn verify` exactly as Konsist failed `./gradlew build`: `OneTopLevelClass` (one top-level type per
> file) and `AvoidStarImport` (no wildcard imports, `java.util` excepted). The reasoning below still
> holds — ArchUnit reads bytecode and cannot see source shape; a source-level tool must — only the
> source-level tool is now Checkstyle rather than Konsist. `KotlinSourceGuidelinesTest` is gone; the
> ArchUnit half of the suite (below) was ported to Java verbatim.

## Context

The hexagonal rules are machine-enforced ([ADR-0002](0002-hexagonal-architecture-for-the-backend.md)),
and the suite that enforces them runs **two** tools — ArchUnit *and* Konsist. Running two frameworks
for "architecture tests" looks redundant, so the choice needs a reason.

It comes down to what each tool can *see*:

- **ArchUnit** inspects compiled **bytecode**. It reasons about the resolved type and dependency graph
  — which package depends on which, whether a class is an interface, how many constructor parameters of
  a given type a class has. It cannot see facts that compilation erases: how source is split into files,
  import style, declaration order.
- **Konsist** inspects Kotlin **source** (the PSI tree). It sees exactly those source-level facts — files,
  imports, top-level declarations — that ArchUnit has lost. It is not the right tool for reasoning about
  the full resolved dependency graph.

Each is blind to the other's domain; neither alone covers both.

## Decision

We use **both**, each for the rules only it can express, in `service/common-architecture-tests` (all
fail `./gradlew build`):

- **ArchUnit (bytecode)** — the layered-dependency graph and naming/suffix rules
  (`HexagonalArchitectureTest`, `NamingConventionArchitectureTest`, `ServiceArchitectureTest`) plus
  basic coding guidelines (`BasicCodingGuidelinesTest`: no `println`/`System.out`, no package cycles).
- **Konsist (source)** — conventions the bytecode no longer carries (`KotlinSourceGuidelinesTest`):
  at most one top-level class/interface/object per file, and no wildcard imports (except `java.util`).

## Consequences

- **Positive:** each rule is written against the representation where it is natural and cheap — no
  contorting a source-shape rule through bytecode, or a dependency rule through text.
- **Negative / trade-offs:** two DSLs to learn, two dependencies to keep current, and a contributor has
  to know which tool owns which kind of rule.
- **Neutral:** this is the deliberate **ceiling**, not a starting point. New structural rules go into
  whichever of these two fits — we do **not** add a third architecture/guardrail framework on top.
