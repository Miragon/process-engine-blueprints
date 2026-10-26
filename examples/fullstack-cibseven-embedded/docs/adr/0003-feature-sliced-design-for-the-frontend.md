# 0003 — Feature-Sliced Design for the frontend

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

The frontend (`frontend/`, React 19 + TanStack Router/Query) renders several views of one aggregate —
a submit form, an application list, a detail page, a task inbox. Without an explicit structure these
grow into a tangle of shared components importing each other in both directions. As on the backend
([ADR-0002](0002-hexagonal-architecture-for-the-backend.md)), the blueprint needs a layering that is
*enforced*, not just recommended.

## Decision

We organise `frontend/src` with **Feature-Sliced Design (FSD)**. Layers, top (most app-specific) to
bottom (most reusable), may only import **downward**:

`app/` → `pages/` → `widgets/` → `features/` → `entities/` → `shared/`

- **`entities/`** (`bike`, `leasing-application`, `user-task`) — read models and their query hooks.
- **`features/`** (`submit-leasing-request`, `sign-contract`, `withdraw-application`, …) — one user
  intention each.
- **`widgets/`** compose features/entities into page sections; **`pages/`** are routed screens;
  **`app/`** holds routing, styles, providers.
- Every slice exposes a **public `index.ts`**; cross-slice imports go through it, never into a slice's
  internals (`no-public-api-sidestep`).
- **Generated code is off-limits as a direct import target** except through a slice's own `api` segment
  — slices re-export from `@/shared/api/generated` behind their `index.ts` (see the `bike` entity).

**There is no `processes/` layer.** FSD's optional top layer is named `processes`, which collides
head-on with *BPMN processes* — the core domain concept here. To keep "process" unambiguous we ban the
FSD layer entirely; multi-step flows live in `widgets/` or `pages/`.

Enforcement (all in `npm run verify`, blocking in CI):

- **steiger** (`steiger.config.ts`, `lint:fsd --fail-on-warnings`) with the FSD recommended rule set:
  `no-processes`, `forbidden-imports`, `public-api`, `no-public-api-sidestep`, `no-segmentless-slices`,
  `inconsistent-naming`, `segments-by-purpose` all ON. Three rules are off *with written reasons*
  (`insignificant-slice`, `repetitive-naming`, `shared-lib-grouping`).
- **eslint** for general lint, **knip** for dead code, **tsc** for types.

The [`create-feature-slice`](../../.claude/skills/create-feature-slice/SKILL.md) skill scaffolds slices
that already honour layering, the public `index.ts`, the generated-import restriction, and the
`processes` ban.

## Consequences

- **Positive:** import direction is machine-checked; a new view has an obvious home; "process" always
  means BPMN.
- **Negative / trade-offs:** the public-API discipline adds an `index.ts` per slice; the three disabled
  steiger rules are a standing judgement call (documented in the config, guarded by the
  [`review-vertical-slice`](../../.claude/agents/review-vertical-slice.md) agent).
- **Neutral:** the generated client is a first-class seam here exactly as `adapter/process` is on the
  backend.
