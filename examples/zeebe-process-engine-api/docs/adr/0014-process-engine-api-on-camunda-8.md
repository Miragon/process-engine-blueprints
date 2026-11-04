# 0014 — The process-engine-api on Camunda 8

- **Status:** Accepted
- **Date:** 2026-11-04

## Context

This example automates the same process behind the same REST contract as `zeebe-native`, which uses the
Camunda Spring SDK directly. Its purpose is to show what changes when the service reaches Zeebe through
the bpm-crafters [process-engine-api](https://github.com/bpm-crafters/process-engine-api) instead, so that
a later change of the engine touches less code. The API needs an adapter, the worker library and a few
choices that the embedded examples (`cibseven-process-engine-api`, `operaton-process-engine-api`) do not
have to make, because here the engine is remote and its read side is eventually consistent.

## Decision

1. **Nothing in the app module uses the Camunda client.** Workers are `@ProcessEngineWorker` beans, the
   process adapter uses `StartProcessApi`, `CorrelationApi` and `UserTaskCompletionApi`, and an architecture
   test fails on any dependency on `io.camunda..`. `common-zeebe` still holds the connection defaults and
   the deployment. The embedded examples read their inbox from the native `TaskService`; here the inbox
   reads the same `UserTaskSupport` pool as the completion, so the two cannot disagree.
2. **User tasks use the `SCHEDULED` delivery.** It is the only strategy that delivers `creationDate`, which
   the inbox reports as `waitingSince`. `LISTENER` would also need a listener in the BPMN or a global
   registration, and `SUBSCRIPTION_REFRESHING` works on job-based user tasks, not on the native
   `zeebe:userTask` of these models. The models stay identical to `zeebe-native`.
3. **Models are deployed by `@Deployment`, not by the worker library.** The library deploys BPMN and DMN
   only, and the models reference deployed forms.
4. **Camunda 8.10.7 with adapter `2026.09.1`.** The adapter's compatibility table lists 8.9.18 for this
   release. We stay on the version of `zeebe-native` so the comparison stays fair: the adapter's `develop`
   branch moved to 8.10.x changing `pom.xml` only, and the process tests and the Bruno collection pass on
   this combination. The adapter BOM is not imported, because it would pin the older Camunda line.
5. **Business behaviour stays as in `zeebe-native`.** That includes its open issues, see below.

Two settings keep the behaviour in line with `zeebe-native`, because the API has no per-call option:

- `camunda.client.message-time-to-live: PT10S`, as the API publishes without a TTL of its own.
- `service-tasks.retry-timeout-in-seconds: 10`, the backoff of a failed job.

## Consequences

**Good:** the service shows no engine types above the adapter's API, the workers are plain methods that are
tested without a job client, and the inbox no longer fails on a task whose variables are already gone
(`zeebe-native` issue #32).

**What this costs, compared to `zeebe-native`:**

- **The pool lags.** A task reaches the pool after the polling interval plus the delay of the secondary
  storage, and leaves it the same way. The process adapter therefore waits up to 10 s for a task before it
  answers 409, inside the surrounding transaction.
- **The polling grows with the open tasks.** `PullUserTaskDelivery` searches all open user tasks without a
  filter or pagination, so tasks beyond the search's first page are not delivered, and it reads form and
  variables per task on every poll. That is fine for this example. Check it before using `SCHEDULED` with
  many open tasks.
- **The backoff applies to every service task**, while `zeebe-native` backs off on `orderBike` only. A worker
  can set its own by throwing the library's `FailJobException`. The retry count itself still comes from the
  job, so `retries` in the model and a reset in Operate work as usual, and `service-tasks.retries` has no effect
  on `@ProcessEngineWorker` beans.
- **A start message has no correlation key.** The adapter publishes it with an empty key, so Zeebe's check for
  an active instance with the same key does not apply. The application id still is the key of all later
  messages.
- **Spring Boot 4 needs `spring-boot-jackson2`.** The worker library requires a Jackson 2 `ObjectMapper`. The
  embedded examples get the module through their engine starters, here it is declared explicitly.
- **Process tests open the job workers themselves.** The adapter subscribes on `ApplicationStartedEvent`, but
  the test client of `@CamundaSpringProcessTest` exists only inside a test method. `BikeLeasingProcessTest`
  calls `SubscribingServiceTaskDelivery.subscribe()` in `@BeforeEach`.

**Inherited, not solved here:**

- Workers are at-least-once and their side effects are not idempotent (#33).
- A message is published whether or not a process waits for it, so withdraw and handover set their status
  regardless (#34).
- Engine commands are sent inside the database transaction, so a worker can read the state before the commit
  (#35). A local Bruno run showed it after a submit: the `sendContract` worker failed with
  `Unknown application` and succeeded on its retry, which here happens only after the 10 s backoff.
- The Bruno collection does not cover the timer paths (#46).

**When to pick it:** choose this example over `zeebe-native` when leaving the engine open is worth more to you
than direct access to Zeebe's features. If you rely on search filters, per-job control or user task listeners,
the native example is the simpler start.
