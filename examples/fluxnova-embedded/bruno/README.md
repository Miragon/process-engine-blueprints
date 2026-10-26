# REST scenarios

A [Bruno](https://www.usebruno.com) collection that drives the bike-leasing process end to end against
a **running** service — either variant, they expose the same API. Domain endpoints trigger the business
actions; the Fluxnova `/engine-rest` API completes user tasks and fires timer jobs, so the whole flow
runs without real 14-day waits. CI runs the collection against both variants on every pull request.

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
| `06-incident-demo` | a failing job runs out of retries and raises an incident |
| `07-list-and-inbox` | the list and task-inbox endpoints |
| `08-alternative-declined` | the bike is out of stock and no alternative is found; contract and policy are compensated, no order is cancelled |
| `09-invalid-request` | invalid input is refused with a 400: a request without income, an accepted alternative without a bike |
| `10-alternative-via-tasklist` | the bike is out of stock and the alternative is chosen the way the Tasklist form does it |

## 🚶 Walk through the complete process

The Bruno collection runs every scenario for you. To drive the happy path by hand (the snippet uses
`jq`):

```bash
# 1. submit a leasing request — starts the process by message, returns the applicationId
curl -s -X POST http://localhost:8080/api/bike-leasing -H 'Content-Type: application/json' \
  -d '{"customerName":"Alice Rider","email":"alice@example.com","age":35,"monthlyNetIncome":3500.0,"bikeId":"BIKE-900","bikeModel":"Gravel Explorer 900"}'

ID=<applicationId from the response>

# 2. read the domain view — wait until contractId is set (validation + DMN + contract sent)
curl -s http://localhost:8080/api/bike-leasing/$ID

# 3. sign the contract — forks into insurance + bike order; wait for status ORDERED
curl -s -X POST http://localhost:8080/api/bike-leasing/$ID/sign-contract

# 4. report the handover — the process now waits for the 14-day withdrawal period
curl -s -X POST http://localhost:8080/api/bike-leasing/$ID/report-handover

# 5. skip the wait: find the timer job of that instance and execute it through the engine REST API
PI=$(curl -s "http://localhost:8080/engine-rest/process-instance?businessKey=$ID" | jq -r '.[0].id')
JOB=$(curl -s "http://localhost:8080/engine-rest/job?processInstanceId=$PI&activityId=event_withdrawalPeriodElapsed&timers=true" | jq -r '.[0].id')
curl -s -X POST http://localhost:8080/engine-rest/job/$JOB/execute

# 6. the lease is active
curl -s http://localhost:8080/api/bike-leasing/$ID      # "status":"ACTIVE"
```

The other paths use the same API:

| Scenario | How to trigger it | Bruno folder |
|---|---|---|
| Not solvent (DMN rejects) | submit with `"age": 15` | `04-not-solvent` |
| Escalation (contract not signed) | fire the `event_signatureDeadline` timer job instead of signing | `02-escalation` |
| Abort with compensation | `POST /api/bike-leasing/{id}/withdraw` after signing, then complete `userTask_clarifyReturn` | `03-abort` |
| Bike unavailable (user task) | submit `"bikeId": "BIKE-OOS"`, then `POST …/clarify-alternative` | `05-bike-unavailable` |
| Incident and retry | submit `"bikeId": "BIKE-FAIL"` | `06-incident-demo` |
| Lists and task inbox | `GET /api/bikes`, `GET /api/bike-leasing`, `GET /api/tasks/clarify-alternative` | `07-list-and-inbox` |
| Alternative declined | submit `"bikeId": "BIKE-OOS"`, sign the contract, then `POST …/clarify-alternative` with `"alternativeFound": false` | `08-alternative-declined` |
| Invalid request (400) | submit with `"monthlyNetIncome": 0` | `09-invalid-request` |

## 📮 Start a case by hand

`POST http://localhost:8080/api/bike-leasing`

```json
{ "customerName": "…", "email": "…", "age": 35, "monthlyNetIncome": 3500, "bikeId": "BIKE-900", "bikeModel": "Gravel Explorer 900" }
```

`age` and `monthlyNetIncome` feed the `checkCreditRating` DMN. `bikeId` is the *only* bike attribute the
engine ever carries: the descriptive `bikeModel` lives in a separate **bike portfolio** aggregate (its
own `bike_portfolio` table) — never as a process variable — and `GET /api/bike-leasing/{id}` resolves it
back from there. Availability is decided by the `BikeDealerPort` outbound adapter, whose small
out-of-stock deny-list drives the branch.

## 🔁 Two ways to complete a user task

If the requested bike is out of stock, the `Clarify alternative with customer` user task shows a
deliberate contrast:

- **Reaches the domain either way:** a client calls `POST /api/bike-leasing/{id}/clarify-alternative`, or
  a human completes the Fluxnova Form in the Tasklist. Both hand the chosen `bikeId` to the process, and
  the re-order reads it and persists it on the application. The REST call additionally registers the
  bike's model in the portfolio.
- **Counter-example:** `clarify-return` in `cancel-bike-order.bpmn` is completed via the Fluxnova Form or
  `/engine-rest` only. It never touches the domain, so its data lands only in process variables (see the
  `bpmn:documentation` on each task).

## 🚨 Incident demo

To teach **transaction boundaries, retries and incidents**, submit a request for the poison bike
`BIKE-FAIL`: the simulated dealer outage fails the *Order bike from dealer* job, its retries count down
(`R3/PT10S`), and an **incident** appears in the Monitoring app to analyze and retry. `06-incident-demo/` has the
requests ready to run.
