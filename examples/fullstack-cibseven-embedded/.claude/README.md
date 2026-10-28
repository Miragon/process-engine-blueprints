# `.claude/`

AI tooling for this repo. The authoritative guidance is in the root `AGENTS.md` (imported by
`CLAUDE.md`); the frontend has its own `frontend/AGENTS.md`.

## Skills — `.claude/skills/<name>/SKILL.md`

Workflows and layer recipes, loaded when a task matches their description. Long material lives under
each skill's `references/`.

| Skill | When |
|---|---|
| `implement-issue` | take a GitHub issue to a verified change, with a plan approval stop |
| `automate-process` | change an executable BPMN model, wire its glue code, validate it (three nets) |
| `create-rest-controller` | add a REST endpoint (inbound port → service → controller) |
| `create-delegate` | add a JavaDelegate for a BPMN service task, with its unit test |
| `create-persistence-adapter` | add an outbound persistence adapter |
| `sync-api-client` | propagate a backend change to a clean frontend client |
| `create-feature-slice` | scaffold a new FSD slice |
| `review-change` | review a PR or diff, findings by severity with a failure scenario |
| `test-in-browser` | check a flow in the running app with Playwright MCP, screenshots as evidence |
| `create-adr` | record an architecture decision |

`implement-issue` and `automate-process` call the layer skills.

## Subagents — `.claude/agents/<name>.md`

| Agent | Does |
|---|---|
| `review-process` | checks the models against their glue code, tests and modelling conventions |
| `review-vertical-slice` | walks a feature end to end (BPMN → port → service → controller → openapi.json → generated client → FSD slice → Bruno → Playwright) and reports gaps |

## Settings and hooks

- `settings.json` denies edits to generated files (`adapter/process`, `openapi.json`, the orval
  client). The process-API patterns name no service path (`/**/adapter/process/Errors.*`), so they
  also hold for further services under `service/`; `HistoryCleanupConfiguration` stays editable. It
  allows the build, lint and read-only `gh` commands, and wires one hook. Personal additions
  go in `settings.local.json` (git-ignored).
- `settings.json` also enables four pinned plugins from marketplaces fixed to a release tag:
  `bpmn-cli@miragon-bpmn` (skill `bpmn-cli:bpmn`), `pulse-cli@pulse` (skill `pulse-cli:pulse-cli`,
  `/pulse-cli:status`), `bpmn-to-code@bpmn-to-code` (skills `bpmn-to-code:*`) and
  `bpmnlint-rules@miragon-bpmnlint` (skills `bpmnlint-rules:*`). Claude Code installs them once you
  trust the folder; cloud sessions do not load them. Its `env` sets `BPMN_KEEP_FLOW_IDS=1`, so bpmn-cli
  keeps flow ids stable. `../.pulse-cli.json` points pulse-cli at the local engine on :8080.
- `../.mcp.json` provides the Playwright MCP server (pinned) for `test-in-browser`.
- `hooks/lint-bpmn.sh` (PostToolUse) runs bpmnlint after every edit of a model under
  `service/app/src/main/resources/bpmn/` and hands the findings back to Claude (exit 2). It changes
  nothing; a missing bpmnlint (`npm ci` in the repo root) is reported, not ignored.

## What goes where

| Kind | Place | Example |
|---|---|---|
| orientation, boundaries, conventions | `AGENTS.md` | "Spring Data types stop at the adapter" |
| a repeatable workflow | a skill | implementing an issue |
| an independent read-only review | an agent | model ↔ glue consistency |
| a rule a machine can check | a test, linter or CI step | ArchUnit, bpmnlint, drift checks |
| feedback right after an edit | a hook (small, no side effects) | bpmnlint on `.bpmn` |
| a hard no on a path | a deny rule in `settings.json` | editing generated files |

One fact, one place: a skill links to `AGENTS.md` instead of repeating it. Keep skill descriptions
quoted (an unquoted ` #` starts a YAML comment and cuts them off).

Skills and subagents are read natively by Claude Code and other AGENTS.md-aware tools. See ADR-0007
for why `AGENTS.md` is the single source and `CLAUDE.md` is a one-line import.
