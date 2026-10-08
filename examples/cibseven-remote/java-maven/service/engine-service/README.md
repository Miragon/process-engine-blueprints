# engine-service — the remote CIB seven engine host

A near **model-agnostic** CIB seven engine. It boots the engine, exposes the REST API at `/engine-rest`
and the Cockpit/Tasklist at `/camunda` (admin/admin, port **8081**), and provides the shared
Postgres-backed engine database. It deploys **no** process model of its own — the `example-service` owns
and deploys the process. The one thing it *does* carry is the process's **execution/task-listener
beans** (see below).

## Listeners run in the engine

Service tasks in the remote topology are external tasks handled by the worker, but **execution and task
listeners have no external-task equivalent — they always run *inside* the engine**. So the two example
listeners live here, not in the worker:

- `BikeOrderAuditListener` — a `camunda:executionListener` (`event="end"`) on `serviceTask_orderBike`,
  audit-logging the order outcome from the result variables the worker returned.
- `ClarifyAlternativeTaskListener` — a `camunda:taskListener` (`event="create"`) on
  `userTask_clarifyAlternative`, audit-logging that manual clarification is required.

Both are Spring `@Component`s referenced by expression (`#{beanName}`) from the deployed model. The
engine **deliberately does not depend on the worker** — pulling in `example-service` would drag its
business code and JPA layer into a host that is meant to stay generic. The price: the engine has no
access to the worker's generated `*ProcessApi`, so `BikeOrderAuditListener` reads its variables by
**plain string name** (`"orderId"`, `"bikeAvailable"`) that must be kept in sync with the model by hand.
This is the single deliberate concession to the "model-agnostic engine" ideal — listeners are the
engine's job.

## Who deploys the model?

The `example-service` **owns the process** and deploys it into this engine over REST at start-up (see
`ProcessModelDeploymentAdapter`, idempotent via `enable-duplicate-filtering`). This engine ships no model
of its own; `camunda.bpm.deployment-resource-pattern: []` disables the starter's classpath
auto-deployment.

- **Use when** the process is fulfilled by a **single service** — the model, the workers and the
  listeners live and version together ("you build it, you run it"). That is this repo's shape: the worker
  owns the whole contract, there is no separate contract module, and the engine stays generic.
- **Why** the remote engine is often a shared/central platform whose classpath you don't control; the
  owning service pushes its model to it (here at start-up; in production often a CI/CD deploy step).

**If a process were fulfilled by *several* services** (no single owner), the model becomes a shared asset
that belongs with the central engine rather than any one service. You would then re-extract the models
and generated `*ProcessApi` into a **shared contract module**, have this engine depend on it and restore
`deployment-resource-pattern` to deploy from the classpath, and drop `ProcessModelDeploymentAdapter` from
the worker so the model is not deployed twice. (That shared module is exactly what this blueprint folded
into the worker to keep a single-owner setup simple.)

## Tests

Because this module is nearly model-agnostic, it holds **no** process-specific tests. The process-model
behaviour test and the structural model-validation test both live with the process owner, in
`example-service`.
