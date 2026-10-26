# create-miragon-blueprint

Starts a Spring Boot project from one example of this repository: one engine, one setup, Kotlin + Gradle or
Java + Maven, renamed to your project and with a fresh git history. It is not published anywhere; `npx` runs
it straight from the repository (see the [quick start](../../README.md#quick-start)).

## What it does

1. Asks for, or takes, the example, the language, the project name (default: the directory name) and the base
   package.
2. Clones the repository at `--ref` (default `main`) shallowly, over https or ssh with your own git access.
3. Exports the starter with [`tooling/starter/export.sh`](../starter/export.sh), the same script CI builds every
   starter with.
4. Moves the package `io.miragon.blueprint` to yours (sources, group id, architecture rules) and replaces the
   project name (`rootProject.name` / `artifactId`, image name).
5. Runs `git init` and makes the first commit.

`--from-checkout <dir>` skips the clone and uses a local checkout. `--list` shows the examples, `--help` all
options. No dependencies: Node 22+, `git` and `tar`.
