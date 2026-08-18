---
name: create-adr
description: Write a new Architecture Decision Record (ADR) under docs/adr/ in this repo's MADR-lite format (Status, Context, Decision, Consequences). Use when the user asks to "write an ADR", "record an architecture decision", or "document why we did X". Auto-numbers from the existing index, derives the filename slug from the title, copies docs/adr/0000-adr-template.md, and shows a full draft before writing.
allowed-tools: Read, Write, Glob
---

# Skill: create-adr

Write a new Architecture Decision Record following the format in `docs/adr/` (see
[ADR-0001](../../../docs/adr/0001-record-architecture-decisions.md)).

## Instructions

### Step 1 — Verify prerequisites

`Glob docs/adr/*.md`.

- If `docs/adr/` has no results at all, **stop** and tell the user to create the directory and add
  `docs/adr/0000-adr-template.md` first.
- If `docs/adr/0000-adr-template.md` is missing, **stop** and ask for the template. Do not invent one.

### Step 2 — Determine the next number

Take the highest four-digit prefix among the existing files and add one, zero-padded to four digits
(e.g. after `0008-…` the next is `0009`). Ignore `0000-adr-template.md` when it is the only file.

### Step 3 — Derive the slug

Kebab-case the title: lowercase, spaces/underscores → `-`, drop non-alphanumeric, collapse repeats.
Target path: `docs/adr/{NNNN}-{slug}.md`.

### Step 4 — Read the template

`Read docs/adr/0000-adr-template.md` and use its exact section structure (Status, Date, Context,
Decision, Consequences with Positive / Negative-trade-offs / Neutral).

### Step 5 — Gather content

Ask the user (skip anything the request already answers):

1. **Context** — the problem, forces, constraints, options considered.
2. **Decision** — what was decided, in active voice, plus the concrete mechanics (tool, file, gate).
3. **Consequences** — positive, negative/trade-offs, neutral.
4. **Status** — default `Accepted`; ask only if they hint otherwise.

### Step 6 — Draft, cross-link, confirm

- Title: short imperative phrase. Date: today, `YYYY-MM-DD`.
- **Cross-link** related ADRs by relative path (e.g. hexagonal → `0002-…`, contract → `0004-…`). The
  existing ADRs are heavily cross-linked; match that.
- Keep it to roughly **one screenful** — this repo's ADRs are tight on purpose.
- Show the full draft and ask: "Write this to `{path}`? (yes / edit / cancel)".

### Step 7 — Write and report

On `yes`, write the file. Report the path and remind the user to **commit the ADR in the same commit
as the code it documents**, so decision and implementation travel together in git history.
