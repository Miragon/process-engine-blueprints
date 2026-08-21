# 0006 — Fixed ports for v1, portless as the upgrade path

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

This repo is developed with [Conductor](https://conductor.build), which runs each task in its own git
worktree — potentially several at once. Parallel worktrees that all bind the same ports collide. Two
answers exist: **fixed ports + serialised runs**, or **portless** (stable per-worktree `.localhost`
URLs that avoid collisions). Portless is the nicer end state, but it only wraps what it can slug — a JS
dev server — and this headless stack has no such server, only backend and database ports.

## Decision

For v1 we use **fixed ports and serialise the runs.** `.conductor/settings.toml` pins
**backend 8080 / Postgres 5432** (the same table published in `AGENTS.md` — see
[ADR-0005](0005-agents-md-as-the-single-source.md)) and sets
**`run_mode = "nonconcurrent"`**, so only one worktree runs the app at a time and the ports never
clash.

**Portless is deferred, deliberately.** Every collision source in this headless stack is outside what
portless wraps:

| Collision source        | Port | Wrapped by portless? |
| ----------------------- | ---- | -------------------- |
| Spring Boot app         | 8080 | ❌ no |
| Postgres                | 5432 | ❌ no |
| Operaton engine schema  | (shared DB schema in Postgres) | ❌ no |

Portless only slugs a JS dev server, and this stack has none. **All** collision sources — the Spring
port, the Postgres port, and the shared engine schema — are **outside what portless wraps.** Adopting
it now would buy nothing and still force `nonconcurrent`, so it is added complexity for no gain until
the backend/DB isolation story is solved.

## Consequences

- **Positive:** dead-simple, predictable URLs; the same ports in dev, tests, CI, and the docs; no
  slug/proxy layer to reason about.
- **Negative / trade-offs:** only one worktree can run the app at once (`nonconcurrent`); truly parallel
  end-to-end runs across worktrees are not possible in v1.
- **Neutral:** per-worktree Postgres/schema isolation is the recorded upgrade path — a future ADR would
  supersede this one once the collision sources are covered.
</content>
