# 0012 — Poll for eventual consistency in end-to-end tests

- **Status:** Accepted
- **Date:** 2026-08-21

## Context

Every write endpoint in this API is **asynchronous**. A command controller hands a message or a
task completion to the embedded Operaton engine and returns **`202 Accepted`** immediately
(`SignContractController`, `ReportHandoverController`, `WithdrawController`, `ClarifyAlternativeController`
all `return ResponseEntity.accepted()`). The observable effect lands some unbounded time later: the
message correlates, the engine's **async job executor** picks up the next `asyncBefore`/`asyncAfter`
continuation, a JavaDelegate runs and writes the read model (`leasing_application`), and only then is
the new state visible over `{{baseUrl}}/api/...` or the Operaton engine REST API
(`{{engineRest}}/...`). The gap between "the command returned" and "its effect is visible" is real,
and it is **environment-dependent** — a loaded CI runner is slower than a laptop.

The Bruno end-to-end suite (`bruno/`) originally bridged that gap with **fixed sleeps** — 18
hand-tuned `setTimeout`s from 1.5 s to a 40 s wait for the incident-retry window. That is the classic
flaky-test anti-pattern: a sleep tuned to pass locally loses the race under CI load (the
`07-inbox-empty` scenario did exactly this), while the safe-side sleeps waste minutes on every run.
The same async shape is inherent to **every process blueprint** in this family, so the fix has to be a
reusable pattern, not a per-test number.

## Decision

We assert eventual state by **polling until the real condition holds, capped by a generous timeout** —
never by sleeping a guessed duration.

- **Shared helpers** live in `bruno/collection.bru` (a collection-level `script:pre-request`, so they
  are in scope for every request): `pollUntil(config, predicate, opts)` and the convenience wrappers
  `pollApp(path, predicate)` (GET the domain read model / inbox at `{{baseUrl}}`) and
  `pollEngine(path, predicate)` (GET the Operaton engine REST API at `{{engineRest}}` — process
  instances, jobs, tasks, incidents, history). They return the instant the predicate is met and only
  wait the full budget when something is genuinely wrong — at which point the request's own assertions
  report the real, still-wrong state instead of a bare timeout.
- **Budgets are env-driven** (`pollTimeoutMs` / `pollIntervalMs` in the environment file), so a sibling
  blueprint with different propagation characteristics tunes them **once**, in one place. A scenario
  that needs a wider window (the incident-retry demo) passes `{ timeoutMs }` at the call site.
- **Each scenario polls for its own precondition or assertion**, mirroring the read model's observable
  fields — the `status` enum (`RECEIVED → ORDERED → HANDED_OVER → ACTIVE`, plus `WITHDRAWN`,
  `REJECTED`, `CANCELLED`) and worker-set fields (`contractId`, `orderId`). Command steps gate on the
  precondition that makes the command valid (e.g. `contractId != null` before `sign-contract`;
  `status == "ORDERED" && orderId != null` before `report-handover`/`withdraw`; the inbox showing the
  item before completing the `clarify-alternative` user task). Engine-level read steps (timer jobs,
  incidents, history activity instances) poll `pollEngine` for the exact row they then read and assert.
- **Division of labour holds:** Bruno asserts the **synchronous request/response contract** (status
  codes, DTO shape) and the observable end-to-end outcome; genuinely engine-internal, deterministic
  checks (full token flow, timer semantics) stay in the JVM process-test layer (`BikeLeasingProcessTest`,
  JGiven). See ADR-0004 for the test layering.
- **The Bruno CLI is pinned** (`@usebruno/cli@4.0.0`): the script sandbox's capabilities (available
  globals, the `require` whitelist the helpers depend on) can change between majors, so an unpinned
  `latest` is a correctness risk, not just a supply-chain one.

## Consequences

- **Positive:** the suite is robust under CI load and *faster* in the common case — it waits exactly as
  long as the engine needs, turning the 40 s incident sleep into "as soon as the incident appears".
  One env-tunable budget replaces 18 magic numbers, and the pattern ports to every sibling blueprint.
- **Negative / trade-offs:** each polled step issues an extra read before the "official" request; a
  predicate must be kept honest (it should mirror what the request asserts, or it silently waits out
  the whole budget). Two read-model writes — `report-handover` and `withdraw` — set their status
  (`HANDED_OVER` / `WITHDRAWN`) *unconditionally* once the message correlates, so tests that need proof
  the process actually advanced assert the **downstream** worker-driven state (`ACTIVE`, `CANCELLED`,
  or a history end event) rather than those intermediate flips.
- **Neutral:** the "submit → poll an observable status" shape becomes the documented client contract
  for these async APIs, for real consumers as much as for tests.

## Implementation notes

- The Bruno script sandbox exposes **no global `fetch`**; `require('axios')` works and is what the
  helpers use. Helpers are attached to `globalThis` in the collection script so request-level scripts
  can call them.
- Message commands correlate **by business key** (the application id). `clarify-alternative` is a
  user-task **completion** and fails if the task is not yet present, hence gating on the inbox before
  sending it.
- Firing a timer over the engine REST API (`POST /job/{id}/execute`) runs the timer catch, but the
  continuation to the next end event still flows through the async job executor — so the following
  assert step polls history for the end event rather than assuming synchronous completion.
- **Deferred, not adopted:** a test-only endpoint exposing engine bookkeeping (e.g. the last-processed
  job position) to let a test assert deterministic progress would be the strongest guarantee, but it
  leaks engine internals into the API surface for test convenience. Revisit only if predicate-based
  polling proves insufficient.
