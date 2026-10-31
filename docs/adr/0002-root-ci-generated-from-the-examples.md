# 0002 — The root CI is generated from the examples' own workflows

- **Status:** Accepted
- **Date:** 2026-10-26

## Context

GitHub runs workflows and reads `dependabot.yml` only from the repository root. Each example keeps its own
`.github/` because it is what a starter built from it gets, so the monorepo needs root equivalents that do
the same checks. Writing them by hand would create a second copy of every example's CI that drifts from the
first.

Path-filtered workflows have a known pitfall: a required check that is skipped because no path matched never
reports, and the pull request waits forever.

## Decision

We **generate** the root CI from the examples (`npm run generate`, checked by `npm run generate:check`):

- Each `examples/<x>/.github/workflows/{pre-merge,nightly}*.yml` becomes a reusable workflow
  `.github/workflows/<x>.<file>.yml`, rooted in the example (working directories, cache paths, artifact names).
- `ci.yml` selects the examples and variants a pull request touches (`scripts/changed-examples.mjs`), calls
  their workflows and `_blueprint.yml`, and ends in one **`CI gate`** job. Only that job is a required check.
  - A path of a variant selects that variant, any other path of an example its first variant, and a
    Markdown-only change no variant. `_blueprint.yml` still builds every starter of a selected example.
  - A path outside `examples/`, except docs, selects `operaton-remote`, `zeebe-native` and
    `fullstack-cibseven-embedded`, which cover remote workers, Zeebe and a frontend, on their first variant.
  - The generated pre-merge workflows leave out the `backend` job, because the starter build runs the same
    lint, build and contract checks. An example's own workflow keeps it, since a starter has no other build.
- `nightly.yml` runs the nightly checks (domain mutation testing, Bruno) and `_blueprint.yml` of the examples
  changed in the last day, and of all of them on Sundays. A path outside `examples/` selects every example
  here, so what a pull request skips still runs.
- `dependabot.yml` is the union of the examples' own entries, with directories, groups and multi-ecosystem
  groups prefixed by the example, so updates stay isolated per example and build tool. GitHub Actions are
  updated once for the root and every example.

## Consequences

- **Positive:** an example's CI has one source, the example; the generated copy cannot fall behind unnoticed.
  A pull request builds only what it touches.
- **Negative / trade-offs:** a regression in a variant a pull request did not select, or in an example outside
  the root subset, shows up in the nightly run, not before the merge. A change to an example's workflow or Dependabot configuration needs a regenerate in
  the same commit. Action pins may differ between an example and its generated copy for the time of one
  Dependabot run; the check ignores pins.
- **Neutral:** about 40 Dependabot entries; each example's updates arrive as their own pull requests.
