---
name: sync-api-client
description: Propagate a backend REST change all the way to a clean frontend. Regenerate the OpenAPI contract from the Kotlin controllers, run orval to regenerate the TanStack Query hooks / MSW handlers / zod schemas, fix the resulting TypeScript across the FSD slices, and leave both drift gates (git diff on openapi.json and api:check) green. Use after adding or changing a controller / request DTO / response DTO, or when the frontend types are stale, api:check fails, or the user asks to "sync the api client" or "regenerate the client".
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Skill: sync-api-client

The backend is the single source of truth; the contract is **generated but committed** and **drift-
gated on both sides** ([ADR-0004](../../../docs/adr/0004-openapi-as-the-checked-in-contract.md)). This
skill runs the full loop so a backend change reaches the browser without drift. Do the hops **in
order** — each gate protects the next.

## The chain

`Kotlin controllers → openapi/openapi.json → orval → src/shared/api/generated/ → FSD slices`

## Instructions

### Step 1 — Regenerate the committed OpenAPI spec (backend side)

The spec is written by a JUnit-costumed generator, not hand-edited. Run it:

```bash
./gradlew :service:app:test --tests "io.miragon.blueprint.openapi.OpenApiSpecExportTest"
```

This boots the app, reads springdoc's live `/v3/api-docs`, and writes `openapi/openapi.json`
deterministically (sorted keys, fixed indenter, no `servers` block). Then check what moved:

```bash
git diff --stat -- openapi/openapi.json
```

If nothing changed, the backend change didn't alter the contract — you may still need Steps 2–4 if the
generated client was already stale. **Never hand-edit `openapi/openapi.json`.** If the endpoint you
expected is missing, the controller likely lacks `@Operation(operationId = …)` — fix the controller
(see [`create-rest-controller`](../create-rest-controller/SKILL.md)) and rerun.

### Step 2 — Regenerate the TypeScript client (frontend side)

```bash
npm --prefix frontend run api:generate
```

orval reads `../openapi/openapi.json` and regenerates, under `frontend/src/shared/api/generated/`:
`endpoints.ts` (TanStack Query hooks + query keys, through the `httpClient` mutator), the MSW handlers,
the `model/` types, and `zod.ts`. This tree is **generated and committed** — never hand-edit it.

### Step 3 — Fix the TypeScript across the FSD slices

Regeneration can rename a hook, change a model field, or alter a request body. Find and fix every
consumer — respecting FSD layering
([ADR-0003](../../../docs/adr/0003-feature-sliced-design-for-the-frontend.md)):

```bash
npm --prefix frontend run typecheck
```

Consumers live behind slice `api` segments, not by importing generated code everywhere. Typical touch
points:

- **entities** (`bike`, `leasing-application`, `user-task`) re-export hooks from `generated/endpoints`
  through their own `api/` segment and public `index.ts` — update the re-export, not the call sites.
- **features** (e.g. `submit-leasing-request`) may reuse a generated **zod** schema as a form resolver
  (`SubmitLeasingRequestBody`). If a request field changed, the schema changes automatically — adjust
  the German presence messages layered on top, but **do not** re-add business thresholds (age/income are
  DMN decisions — the schema comment says why).
- **widgets/pages** consume entity/feature hooks; fix field access there.

Keep imports flowing downward and going through each slice's `index.ts` (never sidestep the public API,
never import `generated/**` directly outside a slice's `api` segment).

### Step 4 — Prove both drift gates are green

```bash
npm --prefix frontend run api:check   # re-runs orval, then git diff --exit-code on generated/
npm --prefix frontend run verify      # format, eslint, steiger (FSD), knip, tsc, vitest (MSW-backed)
```

`api:check` is the frontend drift gate; the backend one is `git diff --exit-code -- openapi/openapi.json`
in CI. Both must be clean — a stale generated tree fails the PR.

### Step 5 — Report

Summarise: what the spec diff changed, which generated files moved, which slices you edited, and confirm
`api:check` + `verify` are green. Remind the user to commit `openapi/openapi.json` **and** the
regenerated client together with the controller change, so the contract and its mirrors travel in one
commit.
