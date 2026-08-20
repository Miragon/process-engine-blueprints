---
name: verify-model-visually
description: Review a BPMN model you just edited by running both quality nets — the deterministic bpmnlint geometry linter and a visual review of the rendered image — and reporting a combined verdict. Catches invisible/overlapping elements, crossing flows, and flows routed through a shape (lint) plus messy, hard-to-read layout that needs human judgement (image). Use after editing a .bpmn model, or when asked to review the diagram, check the layout, or verify the model looks right.
allowed-tools: Read, Bash, Glob
---

# Skill: verify-model-visually

BPMN bugs at the *visual* (BPMNDI) layer are invisible in the XML and in a code diff: a task can
execute but never render; a shape can sit at guessed coordinates that overlap a neighbour or make a
flow cross a label. Guard a model with **two complementary nets, and run both**:

- **bpmnlint** — the **deterministic** net: invisible elements, overlaps, crossing flows, flows routed
  through a shape. Fast, exact, no judgement.
- **the rendered image** — the **judgement** net: spacing, alignment, label quality, left-to-right
  readability — what no formula can settle.

Run lint first, then look at the picture. A green lint with an ugly picture still fails; a clean
picture with a red lint still fails. A geometric defect the image catches that lint missed is itself a
finding — call it out.

## Key rules

- **Run both nets and report both.**
- **Review the rendered image, not the XML.** Never reason about layout from `<dc:Bounds>` or waypoints.
- **Report concrete, located issues.** Name the element/flow and the rule it breaks — never a vague
  "looks a bit off".
- **If everything passes, say so explicitly** and list what each net checked.

## Instructions

1. **Resolve the `.bpmn` file.** Use the path given. Otherwise `Glob` for
   `service/app/src/main/resources/bpmn/*.bpmn` (the production models — `bike-leasing.bpmn` and the
   `cancel-bike-order` call-activity model live here). If ambiguous, ask which to review.

2. **Run the deterministic linter first (geometry net):**

   ```bash
   npm run lint:bpmn
   ```

   It globs every production model. Clean = silent, exit 0; non-zero = real geometry problems —
   **capture each finding** (every line names the element/flow + rule) but don't stop; still do the
   visual pass. Missing binary → `npm ci` once, then re-run. bpmnlint also runs in the
   pre-commit hook and the `build` job of `.github/workflows/pre-merge.yml`; **skip this step only when
   geometry was already gated upstream this run** (CI's lint step or the pre-commit hook) and note that.

3. **Render the model to PNG** with bpmn-to-image, into a scratch path under `.context/`:

   ```bash
   npx bpmn-to-image <path-to-bpmn>:.context/verify/<name>.png
   ```

   If the render fails, stop and report it — a model that won't render is itself a finding.

4. **Load the inputs.** `Read` the rendered PNG (it loads as an image). If labels are too small (the
   Read tool downsamples wide diagrams), re-render larger and read that instead:

   ```bash
   npx bpmn-to-image --scale 2 <path-to-bpmn>:.context/verify/<name>.png
   sips -g pixelWidth .context/verify/<name>.png   # inspect width; crop into sections if still tiny
   ```

5. **Review the image for the judgement residue**, in order:
   - **Visibility** — is every element actually drawn? A flow ending in empty space or a node with no
     arrows signals an element that executes but has no shape. *Not* a defect: compensation handlers (a
     task on a dotted association to a boundary compensation event) and event-sub-process starts sit off
     the main flow **by design** — this model has both (contract/insurance/order compensation, and a
     message event sub-process for withdrawal). Never flag them as orphaned.
   - **No overlaps** — no two shapes, or a shape and a label, on top of each other.
   - **No crossing flows** — flows don't cross each other or run through a shape/label.
   - **Spacing & alignment** — consistent gaps; shared lanes line up; flow reads left-to-right. *(judgement)*
   - **Readable labels** — legible, near their element, not clipped. *(judgement)*

6. **Report a combined verdict.** Lead with the overall result, then one section per net: **Geometry
   (bpmnlint)** — clean, or a table of findings (rule + element/flow); **Judgement (image)** — a table,
   one row per issue, each concretely located (element/flow · what's wrong · rule · severity). If both
   are clean, say so and name what each checked.

7. **Clean up.** Delete the rendered PNGs from `.context/verify/` when done.

If anything is unclear (which file, what "done" means), ask before proceeding.
