# 0009 — Two test layers: Bruno API scenarios and Playwright browser journeys

- **Status:** Accepted
- **Date:** 2026-08-20

## Context

The bike-leasing journey — happy path, escalation, abort, not-solvent, bike-unavailable, list/inbox —
can be exercised at two altitudes against a *running* stack: at the REST boundary, and through the
browser. (Below that, slice-level frontend behaviour is covered by MSW-backed vitest and the engine by
CIB seven JGiven process tests; those are not in question here.)

Two tools cover the running-stack layer, and they overlap on the same journeys:

- **Bruno** (`bruno/`, `@usebruno/cli` in the `bruno` CI job) — declarative HTTP scenario collections
  against the backend + `engine-rest`.
- **Playwright** (`frontend/e2e/`, the `e2e` CI job) — real browser journeys against Vite + backend.

That overlap raises a fair question: is maintaining the same flows twice worth it, or should one layer
own the API and the other stay a thin smoke test?

## Decision

We keep **both**, and let them deliberately cover the **same business journeys** at two layers:

- **Bruno owns the contract in motion.** It proves the backend + engine behave correctly with *no UI
  involved* — status transitions, escalation, the eventual-consistency polling — and doubles as an
  interactive tool for anyone exploring the API. It leans on the committed contract ([ADR-0004](0004-openapi-as-the-checked-in-contract.md)).
- **Playwright owns the browser journey.** It proves the React app wires those same flows through to
  what a user actually sees and clicks.

Both run on every PR (`pre-merge.yml`); Playwright's cross-browser matrix runs nightly.

## Consequences

- **Positive:** a failure's *layer* localizes the bug — Bruno red + Playwright green points at the
  frontend, both red points at the backend/engine. As a blueprint, it also demonstrates two rungs of
  the testing pyramid instead of asserting one and hand-waving the other.
- **Negative / trade-offs:** the core journeys are maintained twice, so a process change can touch both
  suites. We accept that cost because showing a complete testing story is part of this template's job —
  a real project may legitimately thin one layer.
- **Neutral:** neither layer is the single source of truth; they assert different *surfaces* of the same
  behaviour. Bruno stays the lightweight, human-runnable entry point; Playwright is the heavier,
  browser-bound gate.
