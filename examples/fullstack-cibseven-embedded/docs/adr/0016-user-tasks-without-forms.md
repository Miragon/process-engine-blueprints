# 0016 — User tasks without forms, completed through the domain

- **Status:** Accepted
- **Date:** 2026-11-07

## Context

The process has two user tasks: *Clarify alternative with customer* (`userTask_clarifyAlternative`)
and *Clarify return with supplier* (`userTask_clarifyReturn`). A user task in CIB seven can carry a
form for the Tasklist: a Camunda Form (`camunda:formRef`), an embedded or external form
(`camunda:formKey`) or a generated form (`camunda:formData`). A task with a form can be completed in
the Tasklist or through `/engine-rest`, past the domain services: whatever is entered lands in process
variables only, and the form is one more file to keep in step with the variables the process reads.

## Decision

Neither user task has a form (the Modeler's form type `<none>`). The back office works both in the
React app, and the app completes them through domain endpoints, port → service → process adapter:

- *Clarify alternative*: `POST /api/bike-leasing/{id}/clarify-alternative`, offered in the inbox
  `/aufgaben`.
- *Clarify return*: `POST /api/bike-leasing/{id}/clarify-return`, offered on the detail page of a
  withdrawn application.

The process adapter finds the task by its id and the business key. Bruno and Playwright complete the
tasks through the same endpoints, never through `/engine-rest`.

## Consequences

- **Positive:** one way to complete a task, and it always passes the domain. No `.form` files, no
  form resources in `deployment-resource-pattern`.
- **Negative / trade-offs:** the CIB seven Tasklist still lists both tasks, as empty tasks. Completing
  one there skips the domain; nothing prevents it.
- **Neutral:** the other form types and when to pick which are shown in the recipe
  [`cibseven-user-task-forms`](https://github.com/Miragon/process-engine-recipes/tree/main/recipes/cibseven/user-task-forms)
  of process-engine-recipes.
