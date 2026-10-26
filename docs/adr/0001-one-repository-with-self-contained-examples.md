# 0001 — One repository with self-contained examples

- **Status:** Accepted
- **Date:** 2026-10-26

## Context

The blueprints lived in a GitHub organisation of their own, `miragon-blueprints`, with one repository
per example (ten examples plus the organisation's `.github` repository). People we pointed at them found
that hard to navigate: there was no single place to start, to compare examples or to link from a training.
Changes that applied to every example travelled as `blueprint-sync` issues from repository to repository.

At the same time every example has to stay usable on its own. Someone who picks one approach must be able to
turn it into their project without the others, and each example manages its own dependencies, per build tool.

Options considered:

- **Keep one repository per example** and fix navigation with a landing page. Leaves the organisation, the
  repository count and the cross-repository sync as they are.
- **Two repositories**, fullstack and the engine examples. Halves the problem without solving it.
- **One repository with shared code** (a common build, shared modules). Removes duplication, but no example
  is self-contained any more, and a starter would have to be assembled from parts.
- **One repository with self-contained examples**, the duplication kept on purpose.

## Decision

We keep every example in **one repository**, `process-engine-blueprints`, as a **self-contained folder**
`examples/<engine>-<integration>/` with its own build per variant (`kotlin-gradle/`, `java-maven/`), its own
lockfiles, stack, scenarios, ADRs, workflows, Dependabot configuration and agent tooling. Nothing is shared at
build or run time; there is no root build and no npm workspace.

Each example describes itself in `blueprint.yaml`. Everything the repository needs across examples (CI,
Dependabot, the README blocks, Conductor targets, the starter catalog) is generated
from those files and from the example's own configuration; see [ADR-0002](0002-root-ci-generated-from-the-examples.md).

The copies the examples deliberately share are listed in `drift.yml` and checked in CI, so duplication stays a
decision and does not become drift.

## Consequences

- **Positive:** one link to start from and to compare examples; a change across examples is one pull request;
  each example still becomes a standalone project ([ADR-0003](0003-starters-with-npx-from-the-repository.md)).
- **Negative / trade-offs:** tooling that assumes "the example is the repository root" (Claude Code project
  settings, Conductor, git hooks) works when the example folder is opened, not the root. The root carries
  generated files that must be regenerated after an example's workflows or Dependabot configuration change.
- **Neutral:** the old repositories are archived with a pointer here; their history is imported, and issue
  references in imported commit messages point to the old repository.

## Implementation notes

- Two examples carry one variant: `fullstack-cibseven-embedded` is Kotlin/Gradle only by decision,
  `operaton-process-engine-api` is Java/Maven only until a Kotlin variant is built. `blueprint.yaml` records
  both under `missingVariants`, and the README matrix shows it.
- The owner of the repository is written once, in the root `package.json`; `scripts/check-owner.sh` fails CI on
  any other hard-coded owner, so a move to another organisation is a one-line change.
