# 0007 — AGENTS.md as the single source of agent instructions

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

Every AI coding tool wants its own instruction file — `CLAUDE.md` for Claude Code, `.cursorrules` for
Cursor, `.github/copilot-instructions.md`, and so on. Maintaining the same repo conventions in N files
guarantees they drift. Meanwhile `AGENTS.md` has emerged as a cross-tool, vendor-neutral convention for
"how to work in this repo". We want one file that every agent reads and every human maintains.

## Decision

**`AGENTS.md` is the real file** — the single source of truth for build commands, the port table,
architecture rules, and the skills/subagents index. Tool-specific files are **thin pointers** to it:
`CLAUDE.md` is exactly one line, `@AGENTS.md`.

- We use an **`@`-import, not a symlink.** A symlink breaks on Windows checkouts and inside ZIP/tarball
  exports of the repo (a blueprint gets downloaded, not just cloned); an `@`-import is plain text that
  travels everywhere and is resolved by the tool.
- The standard is nestable: there is **one nested `frontend/AGENTS.md`** for the npm-only frontend
  (its own `verify`/`e2e` commands and the FSD rules from
  [ADR-0003](0003-feature-sliced-design-for-the-frontend.md)), which an agent working under `frontend/`
  picks up in addition to the root file.
- The port table lives in `AGENTS.md` and is referenced by `playwright.config.ts` and the README, so
  Vite 5173 / backend 8080 / Postgres 5432 have one authoritative home (see
  [ADR-0008](0008-fixed-ports-for-v1-portless-as-the-upgrade.md)).

## Consequences

- **Positive:** one file to maintain; new tools are onboarded by adding a one-line pointer; the
  convention is portable across clone, ZIP, and worktree.
- **Negative / trade-offs:** an agent whose tool does **not** resolve `@`-imports needs a one-time nudge
  to read `AGENTS.md`; nested files mean an agent must respect the nearest one.
- **Neutral:** the skills and subagents in `.claude/` are indexed from `AGENTS.md` so their existence is
  discoverable without crawling the tree.
