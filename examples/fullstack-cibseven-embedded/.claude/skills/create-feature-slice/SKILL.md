---
name: create-feature-slice
argument-hint: "[<layer>/<slice-name>]  e.g. features/sign-contract or entities/bike"
description: Scaffold a new Feature-Sliced Design slice under frontend/src honoring the layer rules — a public index.ts, purpose-named segments (ui/model/api), downward-only imports, the generated-import restriction, and the processes ban. Use when the user asks to "add a feature", "create an FSD slice", "add an entity/widget/page", or "scaffold a frontend feature". Leaves steiger, knip, and tsc green.
allowed-tools: Read, Write, Edit, Bash, Glob
---

# Skill: create-feature-slice

Scaffold one Feature-Sliced Design slice under `frontend/src`. FSD layering here is as load-bearing as
the backend's hexagon and is CI-enforced by **steiger `--fail-on-warnings`**
([ADR-0003](../../../docs/adr/0003-feature-sliced-design-for-the-frontend.md)).

## The layers (import only downward)

`app/` → `pages/` → `widgets/` → `features/` → `entities/` → `shared/`

- **entities/** — a read model + its query hooks for one aggregate (`bike`, `leasing-application`,
  `user-task`).
- **features/** — one user intention (`submit-leasing-request`, `sign-contract`, `withdraw-application`).
- **widgets/** — compose features/entities into a page section.
- **pages/** — a routed screen. **app/** — routing, styles, providers.

## Hard rules (steiger enforces these; do not fight them)

- **No `processes/` layer — ever.** It collides with *BPMN processes*, the core domain word. `no-processes`
  is ON. Multi-step flows live in `widgets/` or `pages/`.
- **Every slice has a public `index.ts`.** Cross-slice imports go through it; never import into another
  slice's internals (`public-api`, `no-public-api-sidestep`).
- **Segments are purpose-named:** `ui/`, `model/`, `api/` (`segments-by-purpose`, `no-segmentless-slices`).
- **Generated code is imported only through a slice's own `api` segment.** Re-export from
  `@/shared/api/generated` behind the slice's `index.ts` — see the `bike` entity:
  ```ts
  // entities/bike/api/queries.ts
  export { useListBikes } from "@/shared/api/generated/endpoints";
  // entities/bike/index.ts
  export { useListBikes } from "./api/queries";
  export type { BikeDto } from "@/shared/api/generated/model";
  ```
- Imports flow **downward only** (`forbidden-imports`): a feature may import entities/shared, never a
  widget/page.
- Three steiger rules are intentionally OFF (`insignificant-slice`, `repetitive-naming`,
  `shared-lib-grouping`) — don't try to "satisfy" them; the reasons are in `steiger.config.ts`.

## Instructions

### Step 1 — Resolve layer + name

From `$ARGUMENTS` (`<layer>/<slice-name>`, kebab-case name). If missing, ask which layer and name.
Confirm the layer is appropriate (a user intention → `features/`; a read model → `entities/`; a page
section → `widgets/`; a route → `pages/`). Reject `processes/`.

### Step 2 — Create the segments

Under `frontend/src/<layer>/<slice-name>/` create the segments the slice needs:

- `ui/` — a component (`.tsx`), for a feature/widget/page.
- `api/` — query/mutation hooks, **re-exporting** from `@/shared/api/generated/endpoints` (never a raw
  generated import elsewhere).
- `model/` — schema/types/state (a feature form reuses a generated **zod** schema as its resolver — see
  `features/submit-leasing-request/model/submit-form-schema.ts`).

### Step 3 — Write the public `index.ts`

Re-export only the slice's public surface (components, hooks, types). Nothing else may reach into the
slice.

### Step 4 — Wire it in

Consume the new slice from the layer above through its `index.ts` (e.g. a widget imports the feature).
If it needs a backend call that doesn't exist yet, run
[`sync-api-client`](../sync-api-client/SKILL.md) first so the generated hook exists.

### Step 5 — Verify green

```bash
npm --prefix frontend run lint:fsd     # steiger --fail-on-warnings
npm --prefix frontend run lint:deadcode # knip: no unreferenced slice
npm --prefix frontend run typecheck
```

Fix any steiger/knip finding before finishing (a slice referenced nowhere trips knip). Report the files
created and confirm the three checks pass.
