# `.claude/`

Claude Code tooling for this repo. The guidance itself is in [`AGENTS.md`](../AGENTS.md), which
`CLAUDE.md` imports; this folder holds what only Claude Code uses.

## Skills — `.claude/skills/<name>/SKILL.md`

Workflows, loaded when a task matches their description.

| Skill | When |
|---|---|
| `implement-issue` | take a GitHub issue to a verified change, with a plan approval stop |
| `review-change` | review a PR or diff, findings by severity with a failure scenario |
| `create-adr` | record a decision in `docs/adr/` |
| `create-rest-controller` | one endpoint with its `@WebMvcTest`, carried through `openapi.json` to Bruno |
| `create-persistence-adapter` | entity, repository, mapper, adapter, migration and `@DataJpaTest` |

The layer skills (`create-*`) are small recipes next to the workflows: each points at the existing
classes as the production pattern and states the test pattern, so the agent writes the test first and
matches the code. `implement-issue` calls them.

## Subagents — `.claude/agents/<name>.md`

Read-only reviewers with their own context, loaded at session start.

| Agent | Does |
|---|---|
| `review-vertical-slice` | walks one feature end to end (workflow step → ports → service → controller or activity → process test → `openapi.json` → Bruno) |

## Settings

- `settings.json` denies edits to the generated `openapi/openapi.json` and allows the build commands
  and read-only `gh` commands. Personal additions go in `settings.local.json` (git-ignored).

## What goes where

| Kind | Place | Example |
|---|---|---|
| orientation, boundaries, conventions | `AGENTS.md` | "Spring Data types stop at the adapter" |
| a repeatable workflow | a skill | implementing an issue |
| an independent read-only review | an agent | a walk through one feature |
| a rule a machine can check | a test, linter or CI step | ArchUnit, drift checks |
| a hard no on a path | a deny rule in `settings.json` | editing the generated contract |

One fact, one place: a skill links to `AGENTS.md` instead of repeating it. Keep skill descriptions
quoted (an unquoted ` #` starts a YAML comment and cuts them off). `.claude/logs/` is local and git-ignored.
