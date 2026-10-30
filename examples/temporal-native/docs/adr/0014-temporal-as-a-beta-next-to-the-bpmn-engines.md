# 0014 — Temporal as a beta next to the BPMN engines

- **Status:** Accepted
- **Date:** 2026-10-30

## Context

Every other example of the repository runs a BPMN model. This one answers a different question: what
does the same MiraVelo flow look like when the process is code? Domain, application services, REST,
persistence and the contract stay byte-identical to `zeebe-native`, enforced by `drift.yml`, so the
only difference between the two examples is the adapter around the engine.

## Decision

We implement the flow as Temporal workflows in `adapter/process`. It is marked beta: hidden from the
repository's README table, still offered by the starter CLI, Kotlin only.

| BPMN | Temporal |
|---|---|
| process, called process | `BikeLeasingWorkflow`, child workflow `CancelBikeOrderWorkflow` |
| service task, job worker | `@ActivityInterface` in `adapter/process/<Process>Activities.kt`, one contract per workflow like the `*ProcessApi` of the siblings; `*Activity` in `adapter/inbound/temporal` |
| business key, correlation | workflow id = application id |
| message catch event | signal |
| user task `clarifyAlternative` | update with a validator that rejects a call without an open clarification |
| user task `clarifyReturn` | signal `returnClarified` to the child workflow, sent from the Temporal UI or CLI |
| timer, non-interrupting boundary timer | `Workflow.await` / `sleep`, a cancellable `Async` branch |
| event-based gateway | `Workflow.await` with a timeout |
| parallel gateway | `Async` |
| interrupting event sub-process | `CancellationScope` cancelled by the `applicationWithdrawn` signal |
| compensation | `Saga`, each compensation registered after its step completed |
| business-rule task (DMN) | the pure function `isSolvent` |
| task list | visibility query on the search attribute `ClarificationWaitingSince` |

Deadlines are constants in the workflow: reminder after 7 days, signature within 14 days, withdrawal
period of 14 days.

## Consequences

- **Positive:** the flow is compiled, unit-tested with skipped time and reviewed like any other code.
- **Negative / trade-offs:** nobody reads this process as a diagram; a business reader depends on a
  developer. The ArchUnit rule on `adapter.process` keeps the workflow code free of side effects, but
  replay safety of a changed workflow is on the developer: a running case replays the old history.
- **Neutral:** the dev server is in-memory, so its cases are gone with the container.

### Two types per step

A BPMN step costs one constant and one worker; a typed Temporal step costs one interface and one
`*Activity`, because the workflow may not see the use cases. Temporal's samples usually group several
methods in one interface. We keep one interface per step: a grouped interface needs one implementation
with several use cases, which `onlyFulfilOneUseCase` forbids, and the 1:1 mapping stays comparable with
the workers of `zeebe-native`. Each workflow bundles its interfaces in one file, so a reader sees all its
steps in one place.

### Deliberate differences to `zeebe-native`

- **Dealer outage.** `orderBike` retries every 10 seconds without limit instead of raising an incident
  after three attempts. The outage shows as a pending activity in the Temporal UI and the application
  stays withdrawable. There is no `06-incident-demo`.
- **`clarifyReturn` has no Tasklist.** The workflow waits on a signal. `cancellationPossible` is ported
  literally from the model: `true` leads to the clarification and the booked costs, `false` ends it.
  The model reads as inverted; that question belongs to the model, not to this port.
- **Early signals.** A Zeebe message expires after 10 seconds; a signal stays until the workflow
  handles it. A `contractSigned` that arrives before the contract went out is therefore not lost.
- **Withdrawal during a running step.** `issueInsurancePolicy` and `orderBike` wait for the running
  attempt, so a step that completes anyway still joins the saga and gets compensated.
- **Declining an alternative** waits for a running `issueInsurancePolicy` before compensating, so no
  policy stays behind.
- **Reversal cannot be interrupted.** Compensation runs after the cancellation scope closed, so a
  withdrawal during the reversal of a declined alternative changes nothing.

## Implementation notes

- Temporal's default mapper cannot build Kotlin data classes, and `KotlinObjectMapperFactory` from
  `temporal-kotlin` 1.40.0 does not link against the Jackson of Spring Boot 4.1. `common-temporal`
  provides a converter on `jacksonObjectMapper()`.
- Inside a cancelled scope, `Promise.handle` never ran in our tests; `orderBikeAndIssuePolicy` uses
  `Promise.get` to wait for the parallel branch.
- The test server fails a workflow task that cancels an activity completing at that moment. The
  withdrawal tests therefore wait for the parallel step first, and every test of
  `BikeLeasingProcessTest` gets its own context, because a case left open held back the time skipping
  of the next test.
