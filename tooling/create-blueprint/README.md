# create-miragon-blueprint

Start a Spring Boot project from one of the Miragon process-engine blueprints: one engine, one integration style,
Kotlin + Gradle or Java + Maven, renamed to your project and with a fresh git history.

```bash
npm create miragon-blueprint@latest my-service
```

It asks where the engine runs (embedded or remote), which blueprint, which language, and your base package.
Without questions:

```bash
npm create miragon-blueprint@latest my-service -- \
  --example cibseven-embedded --lang kotlin --package com.acme.leasing
```

`--list` shows the blueprints, `--help` all options.

## What it does

1. Downloads `<example>-<variant>.tar.gz` from the example's latest release (or `--ref <tag>`). The archive is
   built and tested by the blueprint's CI. While the repository is private, it needs `GITHUB_TOKEN` or a logged-in
   `gh`.
2. Moves the package `io.miragon.blueprint` to yours (sources, group id, architecture rules) and replaces the
   project name (`rootProject.name` / `artifactId`, image name).
3. Runs `git init` and makes the first commit.

`--from <archive>` uses a local archive; `--from-checkout <monorepo>` builds the starter from a local checkout
with `tooling/starter/export.sh`.

Needs Node 20+, `tar` and `git`.
