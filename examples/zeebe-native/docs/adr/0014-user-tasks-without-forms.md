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
one more file to keep in step with the variables the process reads.

## Decision

Neither user task has a form (the Modeler's form type `<none>`). The application completes both
through domain endpoints, port → service → process adapter:

- *Clarify alternative*: `POST /api/bike-leasing/{id}/clarify-alternative`, for the cases listed by
  `GET /api/tasks/clarify-alternative`.
- *Clarify return*: `POST /api/bike-leasing/{id}/clarify-return`, for a withdrawn application whose
  order the dealer could not cancel.

The process adapter finds the task by its element id and the instance's `applicationId` variable.
Bruno completes the tasks through the same endpoints, never through the Camunda 8 REST API.

## Consequences

- **Positive:** one way to complete a task, and it always passes the domain. No `.form` files, no
  form resources in the `@Deployment` of `EngineAutoConfiguration`.
- **Negative / trade-offs:** the Camunda 8 Tasklist still lists both tasks, as empty tasks.
  Completing one there skips the domain; nothing prevents it. The camunda-compat rule
  `user-task-definition`, which asks every user task for a form, is switched off in `.bpmnlintrc`.
- **Neutral:** the other form types and when to pick which are shown in the recipe
  [`zeebe-external-tasklist`](https://github.com/Miragon/process-engine-recipes/tree/main/recipes/camunda8/external-tasklist)
  of process-engine-recipes.
