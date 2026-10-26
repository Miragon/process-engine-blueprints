# Contributing

1. Pick the example you want to change and open **its folder**; its `CONTRIBUTING.md` and `AGENTS.md` explain
   how to build and test it.
2. At the root, once: `npm ci && npm run hooks:install` (generator, drift check, and the pre-commit hook that
   lints staged BPMN models with the example's own bpmnlint).
3. Changed an example's workflows, `dependabot.yml`, `.conductor/settings.toml` or `blueprint.yaml`? Run
   `npm run generate` and commit the result with it.
4. Changed a file that `drift.yml` lists? Carry the change to the other examples of the rule, or take the file out
   of the rule and say why.
5. Conventional Commits, scoped to the example: `fix(zeebe-native): …`. Pull requests target `main`; `CI gate`
   must be green. Merge with a merge commit or squash; never rewrite `main`.

See [docs/maintaining.md](docs/maintaining.md) for how CI, Dependabot and the starters work.
