# REST scenarios

A [Bruno](https://www.usebruno.com) collection that drives the bike-leasing process end to end against
a **running** service — either variant, they expose the same API. Domain endpoints trigger the business
actions; the Camunda 8 REST API (`/v2`) reads the engine's state and resolves the incident. CI
runs the collection against both variants on every pull request.

```bash
npx --yes @usebruno/cli@4.0.0 run . --env local -r --exclude-tags=demo-data
```

| Folder | Scenario |
|---|---|
| `00-demo-data` | six cases left open for a demo, one per state: waiting for the signature, waiting for the handover, in the withdrawal period, rejected, an alternative waiting in the inbox, withdrawn with the return clarification open. ACTIVE is left out: Zeebe timers cannot be fast-forwarded over REST, see `01-happy-path`. CI skips it (tag `demo-data`) |
| `01-happy-path` | submit → sign the contract → report the handover |
| `03-abort` | the customer withdraws after signing for a custom build the dealer cannot cancel; the return is clarified, the costs booked and the contract compensated |
| `04-not-solvent` | the DMN rejects the applicant |
| `05-bike-unavailable` | the bike is out of stock and the customer picks an alternative |
| `06-incident-demo` | a failing job runs out of retries and raises an incident |
| `07-list-and-inbox` | the list and task-inbox endpoints |
| `08-alternative-declined` | the bike is out of stock and no alternative is found; contract and policy are compensated, no order is cancelled |
| `09-invalid-request` | invalid input is refused with a 400: a request without income, an accepted alternative without a bike |
| `11-abort-order-cancelled` | the customer withdraws after signing; the dealer cancels the order, so no return has to be clarified |

Fill an empty stack with demo cases:

```bash
npx --yes @usebruno/cli@4.0.0 run 00-demo-data --env local -r
```

A running Zeebe broker cannot fast-forward timers over REST, so the timer-gated steps — the signature
deadline and the 14-day withdrawal period that ends in an active lease — are covered by the process
tests, not by this collection.

## 📮 Start a case by hand

`POST http://localhost:8081/api/bike-leasing`

```json
{ "customerName": "…", "email": "…", "age": 35, "monthlyNetIncome": 3500, "bikeId": "BIKE-900", "bikeModel": "Gravel Explorer 900" }
```

`age` and `monthlyNetIncome` feed the `checkCreditRating` DMN. `bikeId` is the *only* bike attribute the
engine ever carries: the descriptive `bikeModel` lives in a separate **bike portfolio** aggregate (its
own `bike_portfolio` table) — never as a process variable — and `GET /api/bike-leasing/{id}` resolves it
back from there.

The API also exposes a few read models: `GET /api/bikes` (the seeded catalogue with live dealer
availability), `GET /api/bike-leasing?status=&page=&size=` (the paged application list) and
`GET /api/tasks/clarify-alternative` (the back-office inbox of cases waiting on the
alternative-clarification task, never exposing a raw task id). The full contract is
[`../openapi/openapi.json`](../openapi/openapi.json).

## 🔁 Completing the user tasks

Both user tasks have no form and are completed through a domain endpoint only:

- **Clarify alternative with customer:** if the requested bike is out of stock (`bikeId: "BIKE-OOS"`),
  `POST /api/bike-leasing/{id}/clarify-alternative` hands the chosen `bikeId` to the process; the re-order
  reads it and persists it on the application, and the endpoint registers the bike's model in the
  portfolio.
- **Clarify return with supplier:** `POST /api/bike-leasing/{id}/clarify-return` confirms the return, and
  the process books the costs.
- **Which withdrawal clarifies a return:** the simulated dealer refuses to cancel the order of the custom
  build `BIKE-CUSTOM` (`03-abort`), so the return is clarified and the costs booked; it cancels every other
  order right away (`11-abort-order-cancelled`).

## 🚨 Incident demo

To teach **transaction boundaries, retries and incidents**, submit a request for the poison bike
`BIKE-FAIL`: the simulated dealer outage fails the *Order bike from dealer* job, its retries count down
(`retries="3"`, 10s apart), and once they hit 0 Zeebe raises an **incident** to analyze and retry in
**Operate**. `06-incident-demo/` has the requests ready to run.
