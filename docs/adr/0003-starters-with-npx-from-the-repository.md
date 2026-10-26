# 0003 — Starters with `npx` straight from the repository

- **Status:** Accepted
- **Date:** 2026-10-26

## Context

"Use this template" copies a whole repository, and cloning the monorepo gives a user ten examples, their
history and a remote they do not want. Tools that fetch a subdirectory (degit, tiged) cannot rename the package
or the project and give no guarantee that what they fetched builds on its own. Publishing a CLI to npm, or
starter archives to releases, would add a registry account, release automation and tokens to maintain, and we
do not want to publish anything.

## Decision

A starter is **one example in one variant as a flat project**, built by `tooling/starter/export.sh`: it copies
the example, runs the example's own `scripts/create-starter.sh <variant> --flat`, and starts a fresh history.

Users run it with **`npx github:<owner>/process-engine-blueprints`**. npm installs the root package from GitHub
(only `tooling/create-blueprint`, see `files` in `package.json`) and runs its `bin`, a CLI without dependencies.
The CLI clones the repository at `--ref` (default `main`) shallowly, exports the starter, renames package, group
and project, and makes the first commit.

Every pull request builds each starter of the touched examples **outside the monorepo** (`_blueprint.yml`), so
what users get is what CI built.

## Consequences

- **Positive:** nothing to publish or release; a merged change reaches users right away. Access follows git: a
  private repository works for everyone who can clone it. `--ref` gives trainings a fixed state (a tag).
- **Negative / trade-offs:** users need Node, git and access to the repository. There are no versioned releases
  of the starters; a tag is the way to pin one.
- **Neutral:** without the CLI, `git clone` plus `tooling/starter/export.sh` gives the same starter, unrenamed.
