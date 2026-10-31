# REST scenarios

A [Bruno](https://www.usebruno.com) collection that drives the bike-leasing process end to end against
a **running** service. Everything runs through the application's own API; CI runs the collection on every
pull request.

```bash
npx --yes @usebruno/cli@4.0.0 run . --env local -r --exclude-tags=demo-data
```

| Folder | Scenario |
|---|---|
| `00-demo-data` | six cases left open for a demo, one per state: waiting for the signature, waiting for the handover, in the withdrawal period, rejected, an alternative waiting in the inbox, withdrawn with the return clarification open. ACTIVE is left out: Zeebe timers cannot be fast-forwarded over REST, see `01-happy-path`. CI skips it (tag `demo-data`) |
| `01-happy-path` | submit → sign the contract → report the handover |
| `03-abort` | the customer withdraws after signing; the case stays `WITHDRAWN` until the return is clarified in Temporal |
| `04-not-solvent` | the credit rule rejects the applicant |
| `05-bike-unavailable` | the bike is out of stock and the customer picks an alternative |
| `07-list-and-inbox` | the list and task-inbox endpoints |
| `08-alternative-declined` | the bike is out of stock and no alternative is found |
| `09-invalid-request` | invalid input is refused with a 400: a request without income, an accepted alternative without a bike |

Fill an empty stack with demo cases:

```bash
npx --yes @usebruno/cli@4.0.0 run 00-demo-data --env local -r
```

A running Temporal server cannot fast-forward timers, so the timer-gated steps — the signature deadline
and the 14-day withdrawal period that ends in an active lease — and the compensations are covered by the
process test, not by this collection.

## 📮 Start a case by hand

`POST http://localhost:8081/api/bike-leasing`

```json
{ "customerName": "…", "email": "…", "age": 35, "monthlyNetIncome": 3500, "bikeId": "BIKE-900", "bikeModel": "Gravel Explorer 900" }
```

`age` and `monthlyNetIncome` feed the credit rule. `bikeId` is the *only* bike attribute the
engine ever carries: the descriptive `bikeModel` lives in a separate **bike portfolio** aggregate (its
own `bike_portfolio` table) — never in a workflow payload — and `GET /api/bike-leasing/{id}` resolves it
back from there.

The API also exposes a few read models: `GET /api/bikes` (the seeded catalogue with live dealer
availability), `GET /api/bike-leasing?status=&page=&size=` (the paged application list) and
`GET /api/tasks/clarify-alternative` (the back-office inbox of cases waiting on the
alternative clarification). The full contract is
[`../openapi/openapi.json`](../openapi/openapi.json).

## 🚧 Dealer outage

Submit a request for the bike `BIKE-FAIL`: the simulated dealer outage fails the `orderBike` activity, which
retries every 10 seconds without limit. See it as a pending activity in the Temporal UI on
<http://localhost:8233>; the application can still be withdrawn.
