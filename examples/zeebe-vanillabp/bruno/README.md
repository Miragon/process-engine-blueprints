# REST scenarios

A [Bruno](https://www.usebruno.com) collection that drives the bike-leasing process end to end against
a **running** service — either variant, they expose the same API. Domain endpoints trigger the business
actions; the Camunda 8 REST API (`/v2`) completes the engine-only user task and reads the engine state.
CI runs the collection against both variants on every pull request.

```bash
npx --yes @usebruno/cli@4.0.0 run . --env local -r --exclude-tags=demo-data
```

| Folder | Scenario |
|---|---|
| `00-demo-data` | six cases left open for a demo, one per state: waiting for the signature, waiting for the handover, in the withdrawal period, rejected, an alternative waiting in the inbox, withdrawn with the return clarification open. ACTIVE is left out: Zeebe timers cannot be fast-forwarded over REST, see `01-happy-path`. CI skips it (tag `demo-data`) |
| `01-happy-path` | submit → sign the contract → report the handover |
| `03-abort` | the customer withdraws after signing; the bike order is cancelled and the contract compensated |
| `04-not-solvent` | the DMN rejects the applicant |
| `05-bike-unavailable` | the bike is out of stock and the customer picks an alternative |
| `06-incident-demo` | a failing job runs out of retries and raises an incident |
| `07-list-and-inbox` | the list and task-inbox endpoints |
| `08-alternative-declined` | the bike is out of stock and no alternative is found; contract and policy are compensated, no order is cancelled |
| `09-invalid-request` | a request without income is refused with a 400 before any process starts |

Fill an empty stack with demo cases:

```bash
npx --yes @usebruno/cli@4.0.0 run 00-demo-data --env local -r
```

A running Zeebe broker cannot fast-forward timers over REST, so the timer-gated outcomes (the 14-day
signature deadline and withdrawal period) are covered by the process tests rather than by Bruno. The
requests poll instead of sleeping, because the engine's search API is eventually consistent
([ADR-0012](../docs/adr/0012-polling-for-eventual-consistency-in-e2e-tests.md)).

## 📮 Start a case by hand

`POST http://localhost:8081/api/bike-leasing`

```json
{ "customerName": "…", "email": "…", "age": 35, "monthlyNetIncome": 3500, "bikeId": "BIKE-900", "bikeModel": "Gravel Explorer 900" }
```

`age` and `monthlyNetIncome` feed the `checkCreditRating` DMN and are, next to the application id and the
gateway decisions, all the engine ever carries. The `bikeId` stays on the workflow aggregate, and the
descriptive `bikeModel` lives in a separate **bike portfolio** aggregate (its own `bike_portfolio`
table) — `GET /api/bike-leasing/{id}` resolves it back from there.

Beyond the per-case actions the API exposes three read models: `GET /api/bikes` (the seeded catalogue
with live dealer availability), `GET /api/bike-leasing?status=&page=&size=` (the paged application
list) and `GET /api/tasks/clarify-alternative` (the back-office inbox, correlated by business key and
never exposing a raw task id). The full contract is [`../openapi/openapi.json`](../openapi/openapi.json),
also served at <http://localhost:8081/swagger-ui.html>.

## 🔁 Two ways to complete a user task

If the requested bike is out of stock (`bikeId: "BIKE-OOS"`), the process parks on a user task. The two
user tasks of the model are a deliberate contrast:

- **Recommended:** a client calls `POST /api/bike-leasing/{id}/clarify-alternative`, which routes through
  the domain (persisting the chosen alternative) *before* completing the task.
- **Counter-example:** `clarify-return` in `cancel-bike-order.bpmn` is completed through the Camunda 8
  REST API only. It never touches the domain, so its data lands only in process variables (see the
  `bpmn:documentation` on each task).

## 🚨 Incident demo

To teach **transaction boundaries, retries and incidents**, submit a request for the poison bike
`BIKE-FAIL`: the simulated dealer outage fails the *Order bike from dealer* job, its retries count down
(`retries="3"`, 10s apart), and once they hit 0 Zeebe raises an **incident** to analyze and retry in
**Operate**. `06-incident-demo/` has the requests ready to run.
