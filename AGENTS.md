# AGENTS.md

Guidance for AI agents (and humans) working on this repository **as a whole**. Inside an example, that
example's own `AGENTS.md` is the authority: `examples/<example>/AGENTS.md`.

## What this is

Eleven self-contained examples of the same MiraVelo bike-leasing process under `examples/<engine>-<integration>/`,
each with a Kotlin/Gradle and/or a Java/Maven variant. Nothing is shared at build or run time; the duplication is
deliberate ([ADR-0001](docs/adr/0001-one-repository-with-self-contained-examples.md)).

## Ground rules

- **Work inside one example by opening its folder.** Its README, `AGENTS.md`, `.claude/` skills and hooks assume
  the folder is the project root.
- **Never hand-edit generated files.** `.github/workflows/ci.yml`, `nightly.yml`, `<example>.*.yml`,
  `.github/dependabot.yml`, `.conductor/settings.toml`, `tooling/create-blueprint/catalog.json` and the generated
  blocks in `README.md` come from `npm run generate`. Change the source (an example's workflows, `dependabot.yml`, `.conductor/settings.toml` or
  `blueprint.yaml`) and regenerate in the same commit.
- **Keep the copies in step.** `drift.yml` lists files that must stay identical across examples. A change to one
  of them goes to every example of the rule (skill `fan-out-change`), or the file leaves the rule with a reason.
- **Keep both variants equivalent.** `identicalResources` in `blueprint.yaml` must be identical between
  `kotlin-gradle/` and `java-maven/`.
- **Never rename or remove an example folder** without a README at the old path pointing to the new one; training
  material links to these paths.
- **No hard-coded owner.** The repository's owner is written once, in the root `package.json`
  (`scripts/check-owner.sh`).
- **Ask before deciding business logic** (gateways, deadlines, error paths, copy users read), in every example.

## Commands

| What | Command |
|---|---|
| Install the maintainer tooling | `npm ci` |
| Regenerate the root files | `npm run generate` (check: `npm run generate:check`) |
| Cross-example drift | `npm run drift` (proposal for new rules: `node scripts/check-drift.mjs --propose`) |
| Owner check | `scripts/check-owner.sh` |
| Tooling tests | `npm test` |
| Starter of one example | `tooling/starter/export.sh <example> <kotlin-gradle\|java-maven> <dir>` |
| Starter CLI from this checkout | `node tooling/create-blueprint/bin/create-miragon-blueprint.mjs <dir> --from-checkout . --example <x> --lang <kotlin\|java>` |
| Build an example | see its `AGENTS.md` (e.g. `cd examples/<x>/kotlin-gradle && ./gradlew build`) |

## CI in one paragraph

`ci.yml` picks the examples and variants a pull request touches, or a representative subset for a change outside
`examples/`, runs their own pre-merge workflows (generated as reusable workflows, without `backend`) plus
`_blueprint.yml` (variants equivalent, starter builds outside the monorepo) and ends in the `CI gate` job, the only
required check ([ADR-0002](docs/adr/0002-root-ci-generated-from-the-examples.md)).
`nightly.yml` runs what that skips. Nothing is published: users start a project with `npx --allow-git=root github:<owner>/process-engine-blueprints`, which clones
the repository and runs the same export ([ADR-0003](docs/adr/0003-starters-with-npx-from-the-repository.md)). Details: [docs/maintaining.md](docs/maintaining.md).

## Skills

| Skill | When |
|---|---|
| `fan-out-change` | carry a change from one example to the others it applies to |
| `add-example` | add a new example or import a repository as one |

## Git and GitHub

Use the `gh` CLI. Everything in English, Conventional Commits (`feat:`, `fix:`, `docs:`, `build:`, `ci:`,
`chore:`, `test:`). Scope a commit to the example when it touches one: `fix(cibseven-remote): …`.
