# 0014 — User tasks without forms, completed through the domain

- **Status:** Accepted
- **Date:** 2026-11-07

## Context

The process has two user tasks: *Clarify alternative with customer* (`userTask_clarifyAlternative`)
and *Clarify return with supplier* (`userTask_clarifyReturn`). A user task in Camunda 8 can carry a
form for the Tasklist: a Camunda Form, linked (`zeebe:formDefinition formId`) or embedded
(`zeebe:userTaskForm`), or a custom form key or external reference (`zeebe:formDefinition formKey` /
`externalReference`). A task with a form can be completed in the Tasklist or through the Camunda 8 REST
API, past the domain services: whatever is entered lands in process variables only, and the form is
one more file to keep in step with the variables the process reads. VanillaBP binds a Camunda user
task to its `@WorkflowTask` by the external reference and refuses to deploy a user task without one.

## Decision

Neither user task has a form. Each keeps only the external reference VanillaBP binds it by
(`clarifyAlternative`, `clarifyReturn`); it links no form. The application completes both through
domain endpoints, port → service → process adapter:

- *Clarify alternative*: `POST /api/bike-leasing/{id}/clarify-alternative`, for the cases listed by
  `GET /api/tasks/clarify-alternative`.
- *Clarify return*: `POST /api/bike-leasing/{id}/clarify-return`, for a withdrawn application whose
  order the dealer could not cancel.

VanillaBP reports each task to its `@WorkflowTask` when the engine creates it, which remembers the task
id on the workflow aggregate. The process adapter loads the aggregate by the application id and
completes the remembered task through `ProcessService.completeUserTask`. Bruno completes the tasks
through the same endpoints, never through the Camunda 8 REST API.

## Consequences

- **Positive:** one way to complete a task, and it always passes the domain. No `.form` files; VanillaBP
  deploys nothing but the models.
- **Negative / trade-offs:** the Camunda 8 Tasklist still lists both tasks, as empty tasks.
  Completing one there skips the domain; nothing prevents it. The workflow aggregate holds one task id
  column per user task (`clarify_alternative_task_id`, `clarify_return_task_id`).
- **Neutral:** the other form types and when to pick which are shown in the recipe
  [`zeebe-external-tasklist`](https://github.com/Miragon/process-engine-recipes/tree/main/recipes/camunda8/external-tasklist)
  of process-engine-recipes.
