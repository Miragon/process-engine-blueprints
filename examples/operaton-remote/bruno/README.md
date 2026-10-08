# REST scenarios

A [Bruno](https://www.usebruno.com) collection that drives the bike-leasing process end to end against
a **running** engine host and worker — either variant, they expose the same API. The worker's domain
endpoints (`:8082`) trigger the business actions; the Operaton `/engine-rest` API (`:8081`) completes
user tasks and fires timer jobs, so the whole flow runs without real 14-day waits. CI runs the
collection against both variants on every pull request.

```bash
npx --yes @usebruno/cli@4.0.0 run . --env local -r
```

| Folder | Scenario |
|---|---|
| `01-happy-path` | submit → sign the contract → report the handover → active lease |
| `02-escalation` | the signature deadline passes and the application is rejected |
| `03-abort` | the customer withdraws after signing; the bike order is cancelled and the contract compensated |
| `04-not-solvent` | the DMN rejects the applicant |
| `05-bike-unavailable` | the bike is out of stock and the customer picks an alternative |
| `06-incident-demo` | a failing external task runs out of retries and raises an incident |
| `07-list-and-inbox` | the list and task-inbox endpoints |

The suite polls for eventual consistency instead of sleeping (see
[ADR-0012](../docs/adr/0012-polling-for-eventual-consistency-in-e2e-tests.md)): the worker and the
engine commit independently, so a status in the worker's read model can be visible before the engine
has reached the matching wait state. The shared `pollApp` / `pollEngine` helpers live in
`collection.bru`; budgets are env-driven (`pollTimeoutMs` / `pollIntervalMs`). **Pin the CLI to
`@usebruno/cli@4.0.0`** — the script sandbox's capabilities can shift between majors.

## 📮 Start a case by hand

`POST http://localhost:8082/api/bike-leasing`

```json
{ "customerName": "…", "email": "…", "age": 35, "monthlyNetIncome": 3500, "bikeId": "BIKE-900", "bikeModel": "Gravel Explorer 900" }
```

`age` and `monthlyNetIncome` feed the `checkCreditRating` DMN, which the engine evaluates. `bikeId` is
the *only* bike attribute the engine ever carries: the descriptive `bikeModel` lives in a separate
**bike portfolio** aggregate in the worker's own database — never as a process variable — and
`GET /api/bike-leasing/{id}` resolves it back from there. Availability is decided by the
`BikeDealerPort` outbound adapter, whose small out-of-stock deny-list drives the branch.

Beyond starting and advancing a case, the worker exposes a small read surface: `GET /api/bikes` (the
seeded catalogue), `GET /api/bike-leasing` (a paged, status-filterable list) and
`GET /api/tasks/clarify-alternative` (the inbox of applications parked on the alternative-clarification
task, correlated by business key and never by a raw engine task id). An application moves through
`RECEIVED → ORDERED → HANDED_OVER → ACTIVE`, or ends as `WITHDRAWN → CANCELLED` or `REJECTED`.

## 🔁 Two ways to complete a user task

If the requested bike is out of stock, the `Clarify alternative with customer` user task shows a
deliberate contrast:

- **Recommended:** a client calls `POST /api/bike-leasing/{id}/clarify-alternative`, which routes through
  the domain (persisting the chosen alternative) *before* completing the task.
- **Counter-example:** `clarify-return` in `cancel-bike-order.bpmn` is completed via the Camunda Form or
  `/engine-rest` only. It never touches the domain, so its data lands only in process variables (see the
  `bpmn:documentation` on each task).

## 🚨 Incident demo

To teach **transaction boundaries, retries and incidents**, submit a request for the poison bike
`BIKE-FAIL`: the simulated dealer outage fails the *Order bike from dealer* external task, its retries
count down (3 attempts, 10 s apart), and an **incident** appears in the Cockpit to analyze and retry.
`06-incident-demo/` has the requests ready to run.
