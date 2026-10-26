# 0003 — Starters as release archives, created with `npm create miragon-blueprint`

- **Status:** Accepted
- **Date:** 2026-10-26

## Context

"Use this template" copies a whole repository, and cloning the monorepo gives a user ten examples, their
history and a remote they do not want. Tools that fetch a subdirectory (degit, tiged) cannot rename the package
or the project and give no guarantee that what they fetched builds on its own.

## Decision

A starter is **one example in one variant as a flat project**, built by `tooling/starter/export.sh`: it copies
the example, runs the example's own `scripts/create-starter.sh <variant> --flat`, and starts a fresh history.

- Every pull request builds each starter of the touched examples **outside the monorepo** (`_blueprint.yml`).
- Every release of an example (release-please, tag `<example>-v<version>`) attaches
  `<example>-<variant>.tar.gz` and `.zip`.
- `npm create miragon-blueprint` (`tooling/create-blueprint`, no dependencies) asks for or takes the example,
  the language, the project name and the package, downloads the archive of the latest release (or `--ref`),
  renames package, group and project, and makes the first commit.

## Consequences

- **Positive:** users get exactly one example, renamed and building, without knowing the monorepo exists. What
  they download is what CI built.
- **Negative / trade-offs:** a release is needed before a change reaches the CLI. While the repository is
  private, downloads need a token (`GITHUB_TOKEN` or `gh auth login`), and the CLI cannot be published with
  provenance.
- **Neutral:** the archives are also plain downloads for anyone who does not want to run the CLI.
