# Contributing

1. Pick the example you want to change and open **its folder**; its `CONTRIBUTING.md` and `AGENTS.md` explain
   how to build and test it.
2. At the root, once: `npm ci && npm run hooks:install` (generator, drift check, and the pre-commit hook that
   lints staged BPMN models with the example's own bpmnlint).
3. Changed an example's workflows, `dependabot.yml`, `.conductor/settings.toml` or `blueprint.yaml`? Run
   `npm run generate` and commit the result with it.
4. Changed a file that `drift.yml` lists? Carry the change to the other examples of the rule, or take the file out
   of the rule and say why.
5. Conventional Commits, scoped to the example: `fix(zeebe-native): …`. Pull requests target `main` and are
   squash-merged: the PR title and body become the commit. Commits must be signed, and `CI gate` must be green.

See [docs/maintaining.md](docs/maintaining.md) for how CI, Dependabot and the starters work.

## External contributions

We're currently not accepting pull requests from outside the team. We don't have the resources to fully review and validate external contributions, so we can't guarantee that a merged change keeps the project free of bugs, free of security vulnerabilities or malicious code, and consistent with requirements that make sense for it. Pull requests opened by external contributors will be closed.

If you've found a bug or have an idea, please [open an issue](https://github.com/Miragon/process-engine-blueprints/issues/new) instead. We read every one, and it's the best way to get something onto our radar.
