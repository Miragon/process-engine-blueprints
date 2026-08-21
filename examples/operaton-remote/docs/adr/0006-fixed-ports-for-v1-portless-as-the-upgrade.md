# 0006 — Fixed ports for v1, portless as the upgrade path

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

This repo is developed with [Conductor](https://conductor.build), which runs each task in its own git
worktree — potentially several at once. Parallel worktrees that all bind the same ports collide. Two
answers exist: **fixed ports + serialised runs**, or **portless** (stable per-worktree `.localhost`
URLs that avoid collisions). Portless is the nicer end state, but it only wraps what it can slug — a JS
dev server — and this is a **headless** stack: there is no frontend and no JS dev server for it to wrap.
Everything that wants a port here is a JVM process or a database.

## Decision

For v1 we use **fixed ports and serialise the runs.** `.conductor/settings.toml` sets
**`run_mode = "nonconcurrent"`**, so only one worktree runs the app at a time and the ports never
clash. The port set is published in `AGENTS.md` (see
[ADR-0005](0005-agents-md-as-the-single-source.md)):

| Collision source        | Port | Wrapped by portless? |
| ----------------------- | ---- | -------------------- |
| engine-service (engine host) | 8081 | ❌ no |
| example-service (worker)     | 8082 | ❌ no |
| Postgres                     | 5432 | ❌ no |
| CIB seven engine schema      | (shared DB schema in Postgres) | ❌ no |

**Portless buys nothing here.** It only slugs a JS dev server, and this headless stack has none — all
four collision sources (both Spring Boot apps, Postgres, and the engine's DB schema) are **outside what
portless wraps.** Adopting it now would add a slug/proxy layer that covers *none* of the actual
collisions, so we stay on fixed ports and `nonconcurrent` until the JVM/DB isolation story is solved.

## Consequences

- **Positive:** dead-simple, predictable URLs; the same ports in dev, tests, CI, and the docs; no
  slug/proxy layer to reason about.
- **Negative / trade-offs:** only one worktree can run the app at once (`nonconcurrent`); truly parallel
  end-to-end runs across worktrees are not possible in v1.
- **Neutral:** per-worktree Postgres/schema and per-app port isolation is the recorded upgrade path — a
  future ADR would supersede this one once all collision sources are covered.
