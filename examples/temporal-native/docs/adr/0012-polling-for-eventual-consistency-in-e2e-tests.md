# 0012 — Poll for eventual consistency in end-to-end tests

- **Status:** Accepted
- **Date:** 2026-08-21

## Context

Every write endpoint in this API is **asynchronous**. A command controller hands a signal to
Temporal and returns **`202 Accepted`** immediately (the client only waits for the server to accept it,
not for the workflow to handle it). The observable effect lands some unbounded time later: the
process reaches its next wait state, an activity runs and writes the read model
(`leasing_application`), and Temporal's **visibility store**, which backs the inbox, updates with its
own lag. The gap between "the command returned" and "its effect is visible" is real, and it is
**environment-dependent** — a loaded CI runner is slower than a laptop.

The Bruno end-to-end suite (`bruno/`) originally bridged that gap with **fixed sleeps** — 18
hand-tuned `setTimeout`s. That is the classic
flaky-test anti-pattern: a sleep tuned to pass locally loses the race under CI load (the
`07-inbox-empty` scenario did exactly this), while the safe-side sleeps waste minutes on every run.
The same async shape is inherent to **every process blueprint** in this family, so the fix has to be a
reusable pattern, not a per-test number.

## Decision

We assert eventual state by **polling until the real condition holds, capped by a generous timeout** —
never by sleeping a guessed duration.

- **Shared helpers** live in `bruno/collection.bru` (a collection-level `script:pre-request`, so they
  are in scope for every request): `pollUntil(config, predicate, opts)` and the convenience wrapper
  `pollApp(path, predicate)` (GET the app read model). They return the instant the predicate is met and only wait the full budget when
  something is genuinely wrong — at which point the request's own assertions report the real,
  still-wrong state instead of a bare timeout.
- **Budgets are env-driven** (`pollTimeoutMs` / `pollIntervalMs` in the environment file), so a sibling
  blueprint with different propagation characteristics tunes them **once**, in one place. A scenario
  that needs a wider window passes `{ timeoutMs }` at the call site.
- **Each scenario polls for its own precondition or assertion**, mirroring the read model's observable
  fields — the `status` enum (`RECEIVED → ORDERED → HANDED_OVER → ACTIVE`, plus `WITHDRAWN`,
  `REJECTED`, `CANCELLED`) and activity-set fields (`contractId`, `orderId`). Command steps gate on the
  precondition that makes the command valid (e.g. `contractId != null` before `sign-contract`; the
  inbox showing the item before completing the `clarify-alternative` user task).
- **Division of labour holds:** Bruno asserts the **synchronous request/response contract** (status
  codes, DTO shape); genuinely engine-level, deterministic checks (timer fast-forward, full token
  flow) stay in the JVM `BikeLeasingProcessTest` layer on the test server. See ADR-0004 for the test
  layering.
- **The Bruno CLI is pinned** (`@usebruno/cli@4.0.0`): the script sandbox's capabilities (available
  globals, the `require` whitelist the helpers depend on) can change between majors, so an unpinned
  `latest` is a correctness risk, not just a supply-chain one.

## Consequences

- **Positive:** the suite is robust under CI load and *faster* in the common case — it waits exactly as
  long as the engine needs.
  One env-tunable budget replaces 18 magic numbers, and the pattern ports to every sibling blueprint.
- **Negative / trade-offs:** each polled step issues an extra read before the "official" request; a
  predicate must be kept honest (it should mirror what the request asserts, or it silently waits out
  the whole budget). Two read-model writes — `report-handover` and `withdraw` — set their status
  (`HANDED_OVER` / `WITHDRAWN`) *unconditionally*, independent of the signal, so tests must
  assert the **downstream** activity-driven state (`ACTIVE`, `CANCELLED`) as proof the process actually
  advanced, not those intermediate flips.
- **Neutral:** the "submit → poll an observable status" shape becomes the documented client contract
  for these async APIs, for real consumers as much as for tests.

## Implementation notes

- The Bruno script sandbox exposes **no global `fetch`**; `require('axios')` works and is what the
  helpers use. Helpers are attached to `globalThis` in the collection script so request-level scripts
  can call them.
- Signals go to the workflow with the application id as workflow id and stay until it handles them, so
  gating on the read-model precondition (rather than exact timing) is sufficient. `clarify-alternative`
  is an **update** and returns **409** if the workflow has no open clarification yet, hence gating on the
  inbox.
- **Deferred, not adopted:** a test-only endpoint exposing the last-processed workflow history event
  (letting a test assert "read model ≥ my command's position" deterministically) would be the strongest
  guarantee, but it leaks engine internals into the API surface for test convenience. Revisit only if
  predicate-based polling proves insufficient.
