# `.claude/`

AI tooling for this repo. The authoritative guidance is in the root `AGENTS.md` (imported by
`CLAUDE.md`); the frontend has its own `frontend/AGENTS.md`.

## Skills — `.claude/skills/<name>/SKILL.md`

Task recipes an agent can follow. Long code templates live under each skill's `references/`.

| Skill | When |
|---|---|
| `automate-process` | model/adjust a BPMN process and wire its glue code |
| `create-rest-controller` | add a REST endpoint (inbound port → service → controller) |
| `create-delegate` | add a JavaDelegate for a BPMN service task |
| `create-persistence-adapter` | add an outbound persistence adapter |
| `sync-api-client` | propagate a backend change to a clean frontend client |
| `create-feature-slice` | scaffold a new FSD slice |
| `verify-model-visually` | inspect a BPMN model's structure |
| `create-adr` | record an architecture decision |

## Subagents — `.claude/agents/<name>.md`

| Agent | Does |
|---|---|
| `review-process` | checks BPMN model ↔ glue-code consistency |
| `review-vertical-slice` | walks a feature end to end (BPMN → port → service → controller → openapi.json → generated client → FSD slice → Bruno → Playwright) and reports gaps |

Skills and subagents are read natively by Claude Code and other AGENTS.md-aware tools. See ADR-0007
for why `AGENTS.md` is the single source and `CLAUDE.md` is a one-line import.
