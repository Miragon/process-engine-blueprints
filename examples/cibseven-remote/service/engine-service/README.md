# engine-service — the remote CIB seven engine host

A **model-agnostic** CIB seven engine. It boots the engine, exposes the REST API at `/engine-rest` and
the Cockpit/Tasklist at `/camunda` (admin/admin, port **8081**), and provides the shared Postgres-backed
engine database. By itself it ships **no** process model — it is pure process infrastructure.

## Who deploys the model? Two patterns

Where the BPMN/DMN model lives and who deploys it is an **ownership** decision, not a technical one.
This blueprint deliberately supports both; pick the one that matches your reality.

### Pattern A — the *service* owns and deploys the model (this repo's default)

The `example-service` owns the process and deploys it into this engine over REST at start-up (see
`ProcessModelDeploymentAdapter`, idempotent via `enable-duplicate-filtering`). This engine stays generic and
knows nothing about `bike-leasing`.

- **Use when** the process is fulfilled by a **single service** — ownership, the model and the workers
  live together and are versioned as one unit ("you build it, you run it").
- **Why** the remote engine is often a shared/central platform whose classpath you don't control; the
  owning service pushes its model to it (here at start-up; in production often a CI/CD deploy step).

This is what `engine-service` is configured for: no `common-package` dependency, and
`camunda.bpm.deployment-resource-pattern: []` disables classpath auto-deployment.

### Pattern B — the *engine* owns and deploys the model

The engine host carries the model on its classpath and deploys it itself; the services only *contribute*
handlers/workers for individual tasks.

- **Use when** a process is **fulfilled by several services** together (no single owner), so the model
  is a shared asset that belongs with the central engine/platform rather than any one service.

To switch to Pattern B:

1. In `build.gradle.kts`, add the contract module so its models land on the classpath:
   ```kotlin
   implementation(project(":service:common-package"))
   ```
2. In `application.yaml`, restore the classpath deployment pattern:
   ```yaml
   camunda:
     bpm:
       deployment-resource-pattern:
         - classpath*:**/*.bpmn
         - classpath*:**/*.dmn
         - classpath*:**/*.form
   ```
3. Remove `ProcessModelDeploymentAdapter` from `example-service` so the model is not deployed twice.

## Tests

Because this module is model-agnostic, it holds **no** process-specific tests. The process-model
behaviour test lives with the process owner, in `example-service` (and the structural model-validation
test in `common-package`).
