# Maintaining the repository

## Generated files

`npm run generate` writes, and CI checks with `npm run generate:check`:

| File | Generated from |
|---|---|
| `.github/workflows/<example>.<file>.yml` | `examples/<example>/.github/workflows/{pre-merge,nightly}*.yml` |
| `.github/workflows/ci.yml`, `nightly.yml` | the examples and their workflows |
| `.github/dependabot.yml` | `examples/*/.github/dependabot.yml` |
| `.conductor/settings.toml` | `examples/*/.conductor/settings.toml` (targets prefixed `<example>:`) |
| `tooling/create-blueprint/catalog.json` | `examples/*/blueprint.yaml` and the root `package.json` |
| `README.md` (badges, quick start, examples table) | `examples/*/blueprint.yaml` and the root `package.json` |

The example's own files stay what a starter gets; the root copies only make them run in the monorepo.

## CI

- `CI gate` is the only required status check. It fails when any job it needs failed or was cancelled, and passes
  when examples were skipped because the pull request did not touch them.
- A change outside `examples/` (except `README.md`, `docs/`, `.claude/`) checks every example.
- `_blueprint.yml` per touched example: identical resources of both variants, and each starter exported to a
  folder outside the checkout, linted and built.
- Nightly: the nightly checks (full mutation testing, cross-browser e2e for fullstack) of the examples changed in
  the last 25 hours; on Sundays and on demand all of them.

## Dependabot

One entry per example, build tool and ecosystem; groups and multi-ecosystem groups are prefixed with the example
and pull requests are labelled `example:<name>`, so an update of one example never waits for another. GitHub
Actions are bumped once for the root and all examples. Patch and minor updates are auto-merged once `CI gate` is
green (`dependabot-automerge.yml`); a red Dependabot pull request is handed to `dependency-repair.yml` (needs the
`claude-maintenance` environment and `CLAUDE_CODE_OAUTH_TOKEN`).

## Drift between examples

`drift.yml` lists files that are identical in several examples today (scenarios, SQL fixtures, ADR template,
`CLAUDE.md`, …). Build files, wrappers, lockfiles, stacks and workflows are left out on purpose: they are bumped
per example. When a change has to differ in one example, move the file out of its rule with a reason in the pull
request. `node scripts/check-drift.mjs --propose` prints what is shared right now, as a starting point.

## Starters

Nothing is published or released. `npx --allow-git=root github:<owner>/process-engine-blueprints` installs the root package from
GitHub (only `tooling/create-blueprint`, per `files` in `package.json`), and its CLI clones the repository at
`--ref` (default `main`) and runs `tooling/starter/export.sh`. A merged change is what the next user gets, so
`_blueprint.yml` builds every starter of a touched example on each pull request. Trainings that need a fixed state
point `--ref` at a tag.

## Moving the repository

The owner appears only in the root `package.json` (`scripts/check-owner.sh` enforces it). To move:

1. Transfer the repository on GitHub (old URLs redirect).
2. Change `repository.url` in `package.json`, run `npm run generate`, commit.
3. Re-create what does not move: the `claude-maintenance` environment and its secret, and the rulesets.
