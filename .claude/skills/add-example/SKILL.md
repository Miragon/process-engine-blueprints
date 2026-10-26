---
name: add-example
description: Add a new example to this monorepo, or import an existing standalone repository as one, so that CI, Dependabot, the README table and the starter CLI pick it up. Use when the user wants to add, import or migrate an example or blueprint into examples/.
---

# Add an example

1. **Name it** `<engine>-<integration>` (engine: `cibseven`, `operaton`, `fluxnova`, `zeebe`; integration:
   `embedded`, `remote`, `process-engine-api`, `native`, `vanillabp`, or a new one the user names).
2. **Bring the files.**
   - Importing a repository keeps its history:
     `git filter-repo --to-subdirectory-filter examples/<name>` on a fresh clone, then
     `git merge --allow-unrelated-histories` into the branch. The `main` ruleset allows only signed squash
     merges, so such a PR is merged by a repository admin with *Merge commit* and the bypass on the pull request;
     squashing would drop the history.
   - A new example starts as a copy of the closest existing one.
3. **Shape it like the others:** variants in `kotlin-gradle/` and `java-maven/`, its own
   `.github/workflows/pre-merge*.yml` and `nightly*.yml`, `.github/dependabot.yml`, `package.json` with
   `lint:bpmn`, `stack/`, `bruno/`, `openapi/`, and `scripts/create-starter.sh` when it has two variants.
4. **Describe it** in `examples/<name>/blueprint.yaml` (copy one): `engine`, `integration`, `topology`
   (`embedded` or `remote`), `summary`, `stack`, `variants` with their paths, `identicalResources`, and
   `missingVariants` with a reason when it has only one. New engine or integration values also need a label in
   `scripts/lib/readme.mjs`.
5. **Regenerate and check:** `npm run generate`, then `node scripts/check-drift.mjs --propose` to see which
   files it shares with the others and add the rules that should hold to `drift.yml`; `npm run drift`,
   `npm test`, and `tooling/starter/export.sh <name> <variant> /tmp/<name>` plus its build.
