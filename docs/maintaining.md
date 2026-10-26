# Maintaining the repository

## Generated files

`npm run generate` writes, and CI checks with `npm run generate:check`:

| File | Generated from |
|---|---|
| `.github/workflows/<example>.<file>.yml` | `examples/<example>/.github/workflows/{pre-merge,nightly}*.yml` |
| `.github/workflows/ci.yml`, `nightly.yml` | the examples and their workflows |
| `.github/dependabot.yml` | `examples/*/.github/dependabot.yml` |
| `.conductor/settings.toml` | `examples/*/.conductor/settings.toml` (targets prefixed `<example>:`) |
| `release-please-config.json` (packages) | `examples/*/blueprint.yaml` |
| `tooling/create-blueprint/catalog.json` | `examples/*/blueprint.yaml` and the root `package.json` |
| `README.md` (between the markers) | `examples/*/blueprint.yaml` |

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

## Releases and starters

- release-please keeps one release PR per example; merging it tags `<example>-v<version>` and attaches
  `<example>-<variant>.tar.gz` and `.zip`, built by `tooling/starter/export.sh`.
- `Release` → *Run workflow* (`examples: all` or a list) builds the starters for the versions in
  `.release-please-manifest.json` and creates missing releases. Run it once after the first merge, and whenever
  an example is added.
- The CLI `create-miragon-blueprint` is released as its own component; it is published to npm with provenance
  by trusted publishing (no `NPM_TOKEN`) once the repository is public and the package's trusted publisher is
  configured on npmjs.com.

## Moving the repository

The owner appears only in the root `package.json` (`scripts/check-owner.sh` enforces it). To move:

1. Transfer the repository on GitHub (old URLs redirect).
2. Change `repository.url` in `package.json`, run `npm run generate`, commit.
3. Re-create what does not move: the `claude-maintenance` environment and its secret, rulesets, and the npm
   trusted publisher.
