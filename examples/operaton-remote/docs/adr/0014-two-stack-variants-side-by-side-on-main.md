# 0014 — Two stack variants side by side on `main`

- **Status:** Accepted
- **Date:** 2026-10-08

## Context

The blueprint serves two audiences. New projects that are free to choose should start from the stack we
consider modern: **Kotlin + Gradle**. Many enterprise teams — and our developer trainings —
are bound to **Java + Maven** and must be able to use the blueprint without knowing Kotlin or Gradle.

The repository started in Kotlin + Gradle and was then ported to Java + Maven in one step, which
replaced the Kotlin build on `main`. That served the second audience at the expense of the first, and
left the stack we recommend only in the git history.

Options considered for offering both:

- **A movable git tag or a maintenance branch** for the second stack. Workflows and Dependabot act on
  the default branch, so a tag gets no CI and no dependency updates, and a branch needs a second
  maintenance process. The sibling
  [cibseven-embedded-example](https://github.com/miragon-blueprints/cibseven-embedded-example) tried the
  tag first; it fell behind `main` silently within three weeks.
- **A separate repository per stack.** Doubles the repository count of the blueprint family and makes
  comparing the two implementations harder.
- **Both variants in one tree on `main`.**

Every option costs the same double implementation effort. They differ in *when* a difference between
the variants becomes visible.

## Decision

We keep **both variants on `main`**, each in its own self-contained directory, and **recommend
Kotlin + Gradle** as the default for new projects.

- `kotlin-gradle/` and `java-maven/` each hold a complete build (wrapper included) of the same four
  modules: the engine host, the hexagonal worker, the generated engine client and the architecture
  tests. Neither depends on the other, so a fork deletes the one it does not need.
- Each variant carries its **own copy** of the language-neutral resources — BPMN, DMN, forms, Flyway
  migrations and `application.yaml` — in `service/example-service/src/main/resources` and
  `service/engine-service/src/main/resources`, where a Spring Boot developer expects them. A shared
  directory mounted by both builds was rejected: it would make neither variant usable on its own.
- `openapi/openapi.json`, `bruno/` and `stack/` exist once and apply to both.
- A change to the service is made in **both variants in the same pull request**.

Four gates make drift visible on every pull request:

| Gate | What it proves |
|---|---|
| Build, architecture tests and PIT (gate 80) per variant | each variant is correct and tested on its own |
| One OpenAPI contract, drift-gated against both | both workers expose exactly the same REST API |
| The Bruno collection against both running variants | both behave the same in the end-to-end scenarios |
| `diff -r` over the `src/main/resources` trees | both deploy the same models, schema and configuration |

Dependabot updates Gradle and Maven in the same `backend` group, so both builds move to the same
versions in one pull request.

There is deliberately **no gate that compares which files changed**. The two languages use different
patterns, so a class may exist in one variant only and a legitimate change often touches one side
alone; such a gate would mostly produce noise. The gates compare observable behaviour instead.

## Consequences

- **Positive:** both variants have CI and dependency updates like everything else on `main`. Each is a
  complete project on its own. Trainings use a plain directory instead of a tag checkout.
- **Negative / trade-offs:** every functional change is implemented and reviewed twice, and pull
  requests grow. A model change has to be copied to the other variant. `main` is no longer a
  single-language tree.
- **Neutral:** the gates compare the contract and the scripted scenarios. Behaviour covered by neither
  can still differ, which is why the unit and process tests of both variants are kept case-for-case
  equivalent by review.

## Implementation notes

- Idiomatic differences are intended and not drift: records and `Optional` instead of `data` classes
  and nullable types, Mockito instead of MockK, SLF4J instead of kotlin-logging, and Checkstyle instead
  of Konsist for the two source rules of
  [ADR-0007](0007-two-architecture-test-tools-archunit-and-konsist.md) (one top-level type per file, no
  wildcard imports).
- Java records carry no nullability, so the Java DTOs declare it with annotations to produce the same
  `required` arrays and nullable types the Kotlin types yield.
- The generated engine client differs in shape — the OpenAPI Generator's `jvm-spring-restclient`
  library for Kotlin, `restclient` for Java — so the three engine adapters call it differently while
  sending the same requests.
- Earlier ADRs name commands and paths as they were when written (`./gradlew …`,
  `service/example-service/…`); they now live under `kotlin-gradle/`, with Maven equivalents in
  `java-maven/README.md`.
