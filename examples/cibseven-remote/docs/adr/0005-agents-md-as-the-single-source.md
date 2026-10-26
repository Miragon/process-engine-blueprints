# 0005 — AGENTS.md as the single source of agent instructions

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

Every AI coding tool wants its own instruction file — `CLAUDE.md` for Claude Code, `.cursorrules` for
Cursor, `.github/copilot-instructions.md`, and so on. Maintaining the same repo conventions in N files
guarantees they drift. Meanwhile `AGENTS.md` has emerged as a cross-tool, vendor-neutral convention for
"how to work in this repo". We want one file that every agent reads and every human maintains.

## Decision

**`AGENTS.md` is the real file** — the single source of truth for build commands, the port table, and
the architecture rules. Tool-specific files are **thin pointers** to it: `CLAUDE.md` is exactly one
line, `@AGENTS.md`.

- We use an **`@`-import, not a symlink.** A symlink breaks on Windows checkouts and inside ZIP/tarball
  exports of the repo (a blueprint gets downloaded, not just cloned); an `@`-import is plain text that
  travels everywhere and is resolved by the tool.
- The standard is **nestable**: a module can carry its own `AGENTS.md` with commands specific to it, and
  an agent working under that directory picks it up in addition to the root file.
- The port table lives in `AGENTS.md` and is referenced by the README, so **engine-service 8081 /
  example-service 8082 / Postgres 5432** have one authoritative home (see
  [ADR-0006](0006-fixed-ports-for-v1-portless-as-the-upgrade.md)).

## Consequences

- **Positive:** one file to maintain; new tools are onboarded by adding a one-line pointer; the
  convention is portable across clone, ZIP, and worktree.
- **Negative / trade-offs:** an agent whose tool does **not** resolve `@`-imports needs a one-time nudge
  to read `AGENTS.md`; nested files mean an agent must respect the nearest one.
- **Neutral:** because the root file is the one place conventions live, an agent can discover how to
  work in the repo without crawling the tree.
