# VanillaBP spike

A working rebuild of the bike-leasing blueprint on [VanillaBP](https://www.vanillabp.io), kept on the
branch `experiment/vanillabp`. Its purpose is to evaluate VanillaBP on a real process and to make the
differences to the plain Zeebe integration on `main` and to the bpm-crafters process-engine-api
visible on the same example — not to be merged as is.

Every statement below is marked with where it comes from:

- **[C]** observed in the code, the tests or a run of this repository (or of the sibling blueprint
  named next to it),
- **[D]** stated by the official documentation or readable in the published source of the library,
- **[I]** our own inference.

## 1. Starting point

### What the baseline actually is

The repository name says Zeebe, and the code agrees **[C]**: `main` at `df72483` integrates
**directly** with Camunda 8. There is no process-engine-api and no VanillaBP on the classpath.

| | Baseline on `main` |
|---|---|
| Modules | `service/app` (the service), `service/common-zeebe` (engine glue), `service/common-zeebe-test` (process-test helpers), `service/common-architecture-tests` (ArchUnit + Konsist rules) |
| Engine | Camunda 8.9 / Zeebe, self-managed and remote (`stack/docker-compose.yml`), client `io.camunda:camunda-spring-boot-starter:8.9.17` |
| Integration style | Plain: 12 `@JobWorker` classes, two outbound adapters on `CamundaClient`, a thin `ProcessEngineApi` wrapper and Camunda's `@Deployment` |
| Process start | `POST /api/bike-leasing` → `SubmitLeasingRequestService` → `LeasingProcess.submitRequest` → message `miravelo.leasingRequestReceived` published to a message start event |
| Persistence | One JPA entity `leasing_application` (Flyway-owned schema), mapped to an immutable domain aggregate by `LeasingApplicationEntityMapper` |
| Transactions | `@Transactional` on the application services; job completion and message publishing are separate, unsynchronised calls to Zeebe |

### Engine API dependencies in application code

`io.camunda` imports on `main` **[C]**: all 12 workers (`@JobWorker`, `@Variable`, and `JobClient` /
`ActivatedJob` in `OrderBikeWorker` and `ValidateApplicationWorker`), `LeasingProcessAdapter` and
`TaskInboxAdapter` (`CamundaClient`, user-task search and completion, variable search), and in
`common-zeebe` the `ProcessEngineApi` (create instance, publish message, search instances) and
`EngineAutoConfiguration` (`@Deployment`).

### BPMN-bound implementations

| Job type / element | Baseline worker | Use case |
|---|---|---|
| `miravelo.validateApplication` | `ValidateApplicationWorker` (throws BPMN error via `JobClient`) | `ValidateApplicationUseCase` |
| `miravelo.sendRejection` | `SendRejectionWorker` | `RejectApplicationUseCase` |
| `miravelo.sendContract` | `SendContractWorker` | `SendContractUseCase` |
| `miravelo.sendReminderMail` | `SendReminderMailWorker` | `SendSignatureReminderUseCase` |
| `miravelo.issueInsurancePolicy` | `IssueInsurancePolicyWorker` | `IssueInsurancePolicyUseCase` |
| `miravelo.orderBike` | `OrderBikeWorker` (manual complete / fail with backoff) | `OrderBikeUseCase` |
| `miravelo.activateLeasing` | `ActivateLeasingWorker` | `ActivateLeasingUseCase` |
| `miravelo.cancelContract` (compensation) | `CancelContractWorker` | `CancelContractUseCase` |
| `miravelo.cancelPolicy` (compensation) | `CancelPolicyWorker` | `CancelInsurancePolicyUseCase` |
| `miravelo.sendCancellationConfirmation` | `SendCancellationConfirmationWorker` | `SendCancellationConfirmationUseCase` |
| `miravelo.requestCancellation` (called process) | `RequestCancellationWorker` | `RequestOrderCancellationUseCase` |
| `miravelo.bookCosts` (called process) | `BookCostsWorker` | `BookCancellationCostsUseCase` |
| `userTask_clarifyAlternative` | none; searched and completed by `LeasingProcessAdapter`, listed by `TaskInboxAdapter` | `SelectAlternativeUseCase`, `GetPendingClarificationsQuery` |
| `userTask_clarifyReturn` (called process) | none; completed through Tasklist or the Camunda REST API | — |

The DMN `checkCreditRating`, the timers, the escalation, the compensation events and the call activity
are executed by the engine alone.

### Engine-specific model elements

`zeebe:taskDefinition`, `zeebe:ioMapping`, `zeebe:calledDecision`, `zeebe:calledElement`,
`zeebe:subscription correlationKey="=applicationId"`, `zeebe:userTask`, `zeebe:formDefinition` and the
FEEL conditions `=not(solvent)`, `=not(bikeAvailable)`, `=not(alternativeFound)`,
`=cancellationPossible` **[C]**.

### Tests on `main`

Domain unit tests, MockK tests per application service, `@WebMvcTest` per controller, `@DataJpaTest`
for persistence, a unit test per worker, `ProcessEngineApiTest`, the model validation, the
architecture suite, the OpenAPI export, the actuator smoke test and `BikeLeasingProcessTest` with five
scenarios on an in-container engine; Bruno collections for the running stack **[C]**.

### Blockers found before and during the migration

| Blocker | Evidence |
|---|---|
| The released adapter does not run with the Camunda 8.9 client the project uses | **[C]** startup fails with `ClassCastException: AnyPageImpl cannot be cast to SearchRequestPage` in `Camunda8DeploymentAdapter.doDeployment`; **[D]** the adapter's POM pins `camunda-spring-boot-4-starter:8.8.33` "deliberately" |
| The aggregate must be a Spring Data entity with a string id | **[D]** `SpringDataUtil`: "A string identifier is expected for workflow entities"; **[C]** `Camunda8TaskHandler` passes the JSON variable straight to `findById` |
| Camunda user tasks are bound by `externalReference`, not by a linked Camunda Form | **[D]** `Camunda8TaskWiring` throws "having no external reference set!" |
| The aggregate has to be visible to inbound task bindings and to outbound persistence, which the hexagonal rules keep apart | **[C]** `HexagonalArchitectureTest`: adapters may not access each other |
| No test support for `@CamundaSpringProcessTest` | **[D]** BPMN deployment hangs on `ApplicationReadyEvent`; the adapter's README and blueprint do not mention process tests |
| VanillaBP only retries a task when the BPMN says so | **[D]** adapter README, section "Retries" |

## 2. Target architecture

```
REST controller ──▶ use case ──▶ LeasingProcess port
                                      │
                                      ▼
                       adapter.outbound.workflow.LeasingProcessAdapter
                                      │  ProcessService<LeasingApplicationAggregate>
                                      ▼
                    VanillaBP SPI ◀── Camunda 8 adapter (runtime only) ──▶ Zeebe
                                      │  job for a BPMN task
                                      ▼
                       adapter.inbound.workflow.*Task  (@WorkflowTask)
                                      │  aggregate.toDomain() … aggregate.adopt(result)
                                      ▼
                                   use case
```

### Design decisions

1. **The leasing application row is the workflow aggregate.** `LeasingApplicationAggregate` replaces
   `LeasingApplicationEntity`. It keeps the table, is keyed by the application id as a string, and
   additionally holds what used to live in Zeebe only: the open clarification user task, when it
   appeared, and the two gateway decisions the application itself produces.
2. **The domain stays pure, the aggregate lives in a seam.** VanillaBP needs the aggregate class in the
   `@WorkflowService` annotation, as the parameter type of every `@WorkflowTask` and as the entity of
   a Spring Data repository. Inbound task bindings and outbound persistence therefore share one class,
   which the hexagonal rules forbid for adapter packages. The aggregate sits in `adapter.process`, the
   seam the rules already exempt for process-engine concerns; it maps to and from the immutable domain
   `LeasingApplication` (`toDomain()` / `adopt()`), so JPA and Jackson annotations never reach the
   domain. The alternative — making the domain class the entity, as VanillaBP's own examples do — would
   have meant weakening "the domain is technology neutral" and rewriting the domain as a mutable class.
3. **One `@WorkflowTask` class per BPMN task.** The rule "an inbound adapter fulfils one use case"
   stays intact. VanillaBP allows several `@WorkflowService` beans for one process **[D]**; the
   annotation is `@Inherited`, so the abstract `BikeLeasingWorkflow` declares aggregate, primary
   process (`bikeLeasingProcess`), secondary process (`cancelBikeOrder`) and the transaction once.
4. **Process-driven use cases receive the application instead of an id.** A task hands
   `aggregate.toDomain()` to its use case and adopts the returned application. These services no
   longer load or save: VanillaBP loads the aggregate before the task and saves it afterwards. The
   REST-driven use cases keep their id-based shape and their repository.
5. **The transaction boundary of a task is the binding.** `BikeLeasingWorkflow` is
   `@Transactional(noRollbackFor = TaskException)`, as the adapter README prescribes: VanillaBP loads
   the aggregate, runs the task and saves inside that transaction and sends the job completion after
   the commit; a failure rolls back and fails the job, a `TaskException` commits and throws the BPMN
   error **[D]**.
6. **Only a whitelist reaches the engine.** VanillaBP serialises the whole aggregate with Jackson on
   every task completion, message and user-task completion **[D]**. The aggregate switches Jackson's
   auto-detection off and releases six properties explicitly; nulls are omitted.
7. **The task inbox is a database query.** `ClarifyAlternativeTask` records the user task VanillaBP
   reports on the aggregate; `TaskInboxAdapter` lists aggregates with an open clarification and
   `LeasingProcessAdapter` completes the remembered task. The Zeebe user-task and variable searches are
   gone.
8. **Idempotent where a redelivery can repeat a side effect.** VanillaBP completes a job after the
   database commit, so a lost completion redelivers a task whose result is already stored.
   `SendContractService` and `OrderBikeService` return early when the contract or the order is already
   on the application. The remaining tasks either only set a status or delegate to an id-based port.
9. **The application compiles against the SPI only.** `io.vanillabp:spi-for-java` is the only
   process dependency of `service/app`; the Camunda client and the adapter are on the runtime
   classpath through `common-zeebe`. Two ArchUnit rules back that up (section 6).

## 3. Build, run, test

```bash
docker compose -f stack/docker-compose.yml up -d   # Camunda 8.9 + Postgres + Elasticsearch
./gradlew :service:app:bootRun                      # Flyway V2 migrates the table, VanillaBP deploys the models
./gradlew build                                     # all tests incl. the in-container process test
npm run lint:bpmn
./gradlew :service:app:pitest                       # mutation gate
cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r
```

`WORKER_ID` names the instance towards Zeebe and defaults to `local`; every instance of a scaled
deployment needs its own **[D]**.

Results on this branch **[C]**: `./gradlew build` is green (architecture, unit, slice, model and
process tests, OpenAPI export without drift), `npm run lint:bpmn` is clean, and the full mutation run
kills 207 of 232 mutants (89 %, gate 80). Every workflow task class is at 100 %; the four surviving
mutants in the aggregate are null checks on Kotlin value classes that compile to no-ops.

Live run **[C]**: against the compose stack (Camunda 8.9.18 with Elasticsearch, PostgreSQL 18.6) the
application applied both Flyway migrations, VanillaBP deployed one DMN and two BPMN models, and the
Bruno collection passed completely — 31 requests, 47 assertions, in two consecutive runs — covering the
happy path, withdrawal with compensation and the called process, the DMN rejection, the alternative
clarification, the incident demo and the inbox. The first run failed on the two requests that complete
the clarification immediately; see limitation 4. The variables found in the cluster afterwards were
exactly `applicationId`, `age`, `monthlyNetIncome`, `solvent`, `bikeAvailable`, `alternativeFound`,
`cancellationPossible` and the `returnClarified` Bruno sends to the engine-only task.

## 4. Process data: from variables to the aggregate

Categories: **1** business state of the aggregate · **2** technical reference / aggregate id ·
**3** temporary task parameter · **4** routing or correlation value the engine must see ·
**5** superfluous, or only caused by the previous implementation.

| Variable on `main` | Type | Written by | Read by | Category | After the migration |
|---|---|---|---|---|---|
| `applicationId` | String (UUID) | start message | every worker (`@Variable`), correlation key of three messages, user-task lookup | 2 + 4 | Aggregate id. Still engine-visible: it is the business key the adapter reads from every job and the correlation key. |
| `age` | Int | start message | DMN `checkCreditRating` | 4 | Aggregate property, engine-visible. |
| `monthlyNetIncome` | Double | start message | DMN `checkCreditRating` | 4 | Aggregate property, engine-visible. |
| `solvent` | Boolean | DMN result | `gateway_isSolvent` | 4 | Unchanged. Produced and consumed inside the engine, never part of the aggregate. |
| `bikeId` | String | start message, overwritten when the clarification completes | call activity input; no worker of the called process reads it | 1 (5 in the engine) | Aggregate only. No longer engine-visible. |
| `orderId` | String? | `OrderBikeWorker` | call activity input → `RequestCancellationWorker`, `BookCostsWorker` | 1 | Aggregate only (it already was a column). The tasks of the called process read it from the aggregate. |
| `bikeAvailable` | Boolean | `OrderBikeWorker` | `gateway_bikeAvailable` | 4 | Derived getter on the aggregate (`orderId != null`), engine-visible. |
| `alternativeFound` | Boolean | user-task completion (Camunda Form or `LeasingProcessAdapter`) | `gateway_alternativeFound` | 3 + 4 | Persisted aggregate property, engine-visible. `ProcessService.completeUserTask` takes no ad-hoc variables, so a decision made at completion has to be aggregate state **[D]**. |
| `clarificationNote` | String | Camunda Form | nobody | 5 | Gone with the form. |
| `cancellationPossible` | Boolean | `RequestCancellationWorker` (called process) | `gateway_cancellationPossible` | 4 | Persisted aggregate property, engine-visible. |
| `returnClarified` | Boolean | Camunda Form / REST completion of the engine-only task | nobody | 5 | Still whatever the REST caller sends; never part of the aggregate. |

New on the aggregate and never engine-visible: `clarifyAlternativeTaskId` and
`clarificationRequestedAt`, which the baseline read from Zeebe's user-task search.

**What the engine sees.** Before: `applicationId`, `age`, `monthlyNetIncome`, `solvent`, `bikeId`,
`orderId`, `bikeAvailable`, `alternativeFound`, `cancellationPossible` and the form fields. After:
`applicationId`, `age`, `monthlyNetIncome`, `solvent`, `bikeAvailable`, `alternativeFound`,
`cancellationPossible`. Customer name, e-mail, bike, order, contract and status never leave the
service. `LeasingApplicationAggregateTest.EngineVisibleState` pins the released properties, and
`BikeLeasingProcessTest` asserts the variable names of a real instance **[C]**.

## 5. Changed BPMN bindings

| Change | Where | Why |
|---|---|---|
| `retries="3"` on every `zeebe:taskDefinition` | 11 service tasks in both models | VanillaBP fails a job with 0 retries unless the attribute is present **[D]**; the attribute keeps Zeebe's former default of three attempts. |
| `zeebe:taskHeaders` → `retryBackoff = PT10S` | `serviceTask_orderBike` | Replaces the backoff `OrderBikeWorker` set in code; the adapter reads this header **[D]**. |
| `formDefinition formId` → `externalReference` | both user tasks | The adapter binds a Camunda user task by its external reference and adds its own `creating` / `canceling` task listeners at deployment **[D]**. |
| Start event output mappings without `bikeId` | `startEvent_leasingRequestReceived` | The start message now carries the aggregate; with output mappings present only the mapped values reach the process scope **[I]**. |
| Output mapping without `orderId` | `serviceTask_orderBike` | The order id is aggregate state, not a routing value. |
| Call activity inputs without `orderId`, `bikeId`; start event mappings of the called process removed | `callActivity_cancelBikeOrder`, `startEvent_cancellationRequired` | The called process works on the same aggregate and needs only `applicationId` **[D]** (call activity as decomposition). |
| `bpmn:documentation` and XML comments | both user tasks, start event, order task | They described the Camunda Form path and the worker. |
| DMN moved to `resources/bpmn/` | `check-credit-rating.dmn` | VanillaBP deploys `*.bpmn` and `*.dmn` from one `resources-location` per workflow module **[D]**. |
| Camunda Forms deleted | `resources/forms/*.form` | Nothing references or deploys them any more. |

Unchanged: process flow, job types, message names, correlation keys, timers, the escalation, the error
code, the compensation handlers, the terminate end event and all FEEL conditions. `bpmnlint` and the
bpmn-to-code model validation pass **[C]**.

## 6. Remaining engine-specific places

The application code has **no** direct engine dependency: `service/app` has no `io.camunda` import, the
client is not on its compile classpath, and `HexagonalArchitectureTest.ProcessEngineTests` fails the
build if a production class depends on a Camunda, CIB seven, Operaton or bpm-crafters API or on the
VanillaBP adapter internals, or if the VanillaBP SPI is used outside the two workflow adapter packages.
Both rules were checked against deliberate violations **[C]**.

What still knows the engine:

| Place | Coupling |
|---|---|
| The BPMN and DMN models | Zeebe extension elements, FEEL, correlation keys, external form references, retries and the `retryBackoff` header. VanillaBP expects one model per engine **[D]**. |
| `service/common-zeebe` | Connection defaults, `camunda.client.message-time-to-live`, the adapter and client versions. |
| `application.yaml` | `vanillabp.workflow-modules.bike-leasing.adapters.camunda8` (`resources-location`, `use-tenants: false`) and `workerId`. |
| `service/common-zeebe-test` | `WorkflowDeploymentTestConfiguration` calls the adapter class `Camunda8DeploymentAdapter`. |
| `BikeLeasingProcessTest` | `CamundaClient`, `CamundaAssert`, `CamundaProcessTestContext`. |
| Bruno | The Camunda REST API completes the engine-only user task and drives the incident demo. |
| `generateBpmnModels` | bpmn-to-code generates for `ProcessEngine.ZEEBE`. |
| The engine-only user task `clarify-return` | Completed outside the service by design; VanillaBP still demands a `@WorkflowTask` for it (`ClarifyReturnTask`, which only logs). |

## 7. Limitations and open questions

1. **Client version pin [C].** Adapter 1.10.0 is the only release for Spring Boot 4 and is built
   against the Camunda 8.8 client. The project had to move from client 8.9.17 to 8.8.33 (artifacts
   `camunda-spring-boot-4-starter`, `camunda-process-test-spring-4`). The cluster stays on 8.9, which
   is the upgrade order Camunda supports **[D]**; the process test pins its engine image to 8.9.18 so
   it matches the stack. This contradicts ADR-0008 ("track the latest major versions"), and
   Dependabot will keep proposing the 8.9 client. VanillaBP 2 has release lines per Camunda minor but
   was only available as a snapshot when this was written **[D]**.
2. **No CIB seven adapter [D].** Released adapters exist for Camunda 7 and Camunda 8. The spike could
   not have been done on the CIB seven siblings with released artifacts. VanillaBP 2 develops an
   adapter on top of the process-engine-api, which would reach CIB seven through the bpm-crafters
   adapters; it is unreleased as well.
3. **The Tasklist form path is gone [C].** Both user tasks lost their Camunda Form. The clarification
   is completed through the REST endpoint only; the engine-only task through the Camunda REST API only.
4. **A user task cannot be completed the moment it appears [C].** Before the commit, VanillaBP checks
   the task through Camunda's search API, which on an Elasticsearch-backed cluster trails the engine
   by a moment; when the task is not indexed yet, the adapter falls back to a job command that does
   not apply to a Camunda user task, the transaction rolls back and the caller gets a 500
   (`Command 'UPDATE_TIMEOUT' rejected with code 'NOT_FOUND'`). The inbox, now a database query, shows
   the case before that. Nothing is lost and a retry succeeds, but the service cannot turn the failure
   into a "try again" answer without catching a Camunda exception type. The two Bruno steps wait for
   the engine to list the task; a human working the inbox will not notice, an automated client will.
5. **Engine commands run after the commit [D].** If Zeebe rejects a command then, the database change
   stays and the adapter logs "Manual action required". The baseline had the opposite gap (engine
   first, database second). VanillaBP 1 has no outbox.
6. **Every completion sends a snapshot of the whole released aggregate [D].** In parallel branches a
   task that finishes later can overwrite a process variable with the value it read earlier. In this
   model no gateway reads a value that way — each routing value is consumed right after the task that
   sets it and guarded by an output mapping — but it is a property of the approach, not of this
   process **[I]**. The same reasoning applies to the row: JPA writes the whole row **[D]**; the
   parallel insurance task does not change the aggregate, so nothing is lost here.
7. **Message time-to-live is global [C].** `ProcessService.correlateMessage` cannot set it per message.
   The baseline's 10 seconds are now `camunda.client.message-time-to-live` for every message of the
   service.
8. **Process tests need a bridge [C].** The adapter deploys on `ApplicationReadyEvent`, which precedes
   the client `@CamundaSpringProcessTest` creates per test. `WorkflowDeploymentTestConfiguration`
   repeats the deployment and depends on an adapter class to do so.
9. **Defaults to switch off [D].** Multi-tenancy is on by default (tenant = workflow module id) and
   must be disabled for a cluster without tenants.
10. **Architecture decision records are untouched.** ADR-0002 and ADR-0004 still describe the job
   workers, ADR-0008 the latest-versions policy. A decision to adopt VanillaBP would need its own ADR.
11. **Seen, not fixed: compensation without an order.** When the bike was never ordered (out of
    stock, no alternative), the compensation still calls `cancelBikeOrder`. On `main` the worker then
    receives a null `orderId`; here `placedOrderId()` fails with a clear message. Both end in an
    incident.

Open questions for a decision: Is losing Camunda Forms acceptable for a blueprint that showcases
them? Is the aggregate in a seam package the shape we want to teach, or should the domain class be the
entity? Do we wait for VanillaBP 2 before considering it for the 8.9 line?

## 8. Effort and migration problems

Size of the change against `main` **[C]**: 133 files, roughly 2,600 lines added and 1,700 removed (this
document and the regenerated process API included), most
of it tests. Engine-facing production code shrank: the twelve workers and two engine adapters (396
non-blank lines including their explanatory comments) became fifteen small task classes and one
adapter (285 lines), and `common-zeebe` went from 127 to 36 lines. The aggregate (122 lines) replaced
entity and mapper.

What took the time, in the order it surfaced:

1. Understanding what the adapter does. The README covers the happy path; binary compatibility, the
   user-task binding, the id type, the transaction mechanics and test behaviour had to be read from the
   adapter source.
2. The Camunda client incompatibility, which only shows at startup against an engine.
3. Fitting an entity-centred framework into the enforced hexagon (decision 2).
4. Process tests: no deployment without the bridge.
5. Rewriting the tests of the eight use cases whose signature changed.

## 9. Test concept before and after

| On `main` | On this branch | What it still proves |
|---|---|---|
| 11 `*WorkerTest` (mock use case, call `handle`) | 14 `*TaskTest` and `BikeLeasingWorkflowTest` | A task calls exactly its use case and the result reaches the aggregate; the BPMN error carries code and reason; every task inherits process binding and transaction semantics. |
| `OrderBikeWorkerTest`: "fails the job with a backoff … retries count down to an incident" against a mocked `JobClient` | `OrderBikeTaskTest`: the outage escapes the task; `BikeLeasingProcessTest`: "dealer outage" on a real engine | Three attempts, then an incident — now asserted on the engine instead of on a mock. |
| `ProcessEngineApiTest` against mocked `CamundaClient` command chains | `LeasingProcessAdapterTest` against a mocked `ProcessService`; the process test | Start by message and correlation by application id. |
| `LeasingApplicationEntityMapperTest` | `LeasingApplicationAggregateTest` | Field-by-field mapping both ways, plus the aggregate's own transitions and its engine-visible state. |
| Service tests mocking the repository | Service tests on input and output | The same business assertions without repository mocks. `cancelContract fails when the application is unknown` is gone because the application is handed in; the unknown-application case moved to `LeasingProcessAdapterTest`. |
| `BikeLeasingProcessTest`, five scenarios | Eight scenarios | The five originals, plus the BPMN error path, the incident and the engine-visible data; each asserts the aggregate reloaded from the database. |
| `TestProcessEngineConfiguration` | `WorkflowDeploymentTestConfiguration` | Test wiring of the engine. |

## 10. Comparison

Plain column: this repository on `main`. Process-engine-api column: the sibling blueprint
[`cibseven-process-engine-api-example`](https://github.com/miragon-blueprints/cibseven-process-engine-api-example)
(same process on CIB seven) and the bpm-crafters documentation. VanillaBP column: this branch.

| Aspect | Plain (direct engine) | Process engine API | VanillaBP | Observed effect in the example |
|---|---|---|---|---|
| Process start | **[C]** `ProcessEngineApi.sendMessage` builds a publish-message command with a variable map. | **[C]** `StartProcessApi.startProcess(StartProcessByMessageCmd(messageName, payload))`, a payload map plus business-key and correlation-key entries; returns a `CompletableFuture`. **[D]** A business key is only supported on Camunda-7-style engines. | **[C]** `ProcessService.startWorkflowByMessage(aggregate, messageName)`. | No variable map left; the start also persists the aggregate. |
| Service task binding | **[C]** `@JobWorker(type = …)` with `@Variable` parameters. | **[C]** `@ProcessEngineWorker(topic = …)` with `@Variable` parameters; **[D]** a returned map completes the task with those variables. | **[C]** `@WorkflowTask(taskDefinition = …)` with the aggregate as parameter, `void`. | Same number of classes; the id lookup at the start of every use case disappeared. |
| Access to process data | **[C]** By variable name, per worker. | **[D]** `Map<String, Any?>` in and out at API level; the worker converts single variables with Jackson. | **[C]** Properties of the loaded aggregate; **[D]** no API to read or write a single variable. | A decision made at completion (`alternativeFound`) became a persisted column. |
| Typing and schema | **[C]** Names typed by bpmn-to-code, values untyped. | **[C]** Same; **[D]** `@Variable` can bind a typed object per variable. | **[C]** The aggregate class is the schema; the engine-visible subset is declared with Jackson annotations. | The released data is testable (`EngineVisibleState`). |
| Persistence and ownership of state | **[C]** The service owns its table; order id and bike id also live in the engine. | **[D]** Not addressed by the library; **[C]** same split as plain. | **[C]** The aggregate owns everything, the engine holds routing values only. **[D]** Requires a Spring Data entity with a string id. | A Flyway migration for the id type and four columns; the aggregate needed a seam package. |
| Message and signal correlation | **[C]** Publish message with correlation key and TTL. | **[C]** `CorrelationApi.correlateMessage` with `Correlation.withKey` and a free-form restriction; **[D]** the supported restrictions differ per adapter, signals go through `SignalApi`. | **[C]** `ProcessService.correlateMessage(aggregate, name)`, key = aggregate id, no TTL parameter. No signals in this process. | Correlation became a one-liner; the TTL moved to client configuration. |
| User tasks | **[C]** Eventually consistent search by element id and variable, then complete by key. | **[D]** Tasks are delivered by subscription into the in-memory `UserTaskSupport`; there is no server-side task query. **[C]** The sibling polls that pool before completing and reads the native `TaskService` for the inbox. | **[C]** VanillaBP reports create and cancel to a `@WorkflowTask`; completion by the remembered id. | The inbox became a database query; Camunda Forms were lost; a task is completable only once the engine's search lists it. |
| BPMN errors and technical failures | **[C]** `newThrowErrorCommand` / `newFailCommand` written by hand. | **[C]** `throw BpmnErrorOccurred(…)`; **[D]** any other exception fails the task with one retry less. | **[C]** `throw TaskException(…)`; any other exception fails the job with the modelled retries and backoff. | `OrderBikeWorker`'s complete/fail code is gone; retries had to be written into the BPMN. |
| Transactions and retries | **[C]** Service transaction and job completion are independent. | **[D]** A `@Transactional` worker completes its task after the commit by default, before it on request; an optional idempotency registry (in memory or JPA) skips a redelivered task. | **[D]** Aggregate change and save in one transaction, engine command after the commit, rollback fails the job. | The boundary moved from the application service to the task binding; redelivery made two use cases need a guard. |
| Testability without an engine | **[C]** Workers with manual completion need a mocked `JobClient` and `ActivatedJob`. | **[C]** Workers are plain methods, BPMN errors are exceptions, the APIs are mockable interfaces; **[D]** no in-memory engine is documented. | **[C]** Tasks are plain methods on an aggregate; use cases are functions of the application. | Mutation testing now covers the task bindings instead of excluding them. |
| Required BPMN changes | — | **[D]** None of its own; the model stays engine-native (external tasks on a Camunda-7-style engine, job types on Camunda 8). | **[C]** Retries, backoff header, external form references, one resource directory. | Section 5. |
| Engine coupling | **[C]** 16 production files import `io.camunda`. | **[C]** The API in the adapters, plus native `TaskService` / `RuntimeService` for the inbox, an engine exception type in the REST error mapping and an adapter-specific executor override. | **[C]** None in application code; models, configuration and tests remain engine-specific. | Enforced by ArchUnit and by the compile classpath. |
| Switching engines | **[I]** Rewrite of all workers and adapters. | **[D]** Swap the adapter; adapters exist for Camunda 7, CIB seven and Camunda 8 (incubating, documented up to Camunda 8.8). Restrictions and meta keys are engine-specific. | **[D]** Swap the adapter and provide the models for the new engine; two adapters can run side by side during a migration. Released adapters: Camunda 7 and Camunda 8. | Not exercised. Neither abstraction offered a released adapter for the 8.9 client; VanillaBP has none for CIB seven. |
| Learning curve, boilerplate, conventions | **[C]** Low; the client API is what is used. | **[C]** Low to medium: commands, futures, restrictions, a manually wired task pool. | **[C]** Medium to high: wiring by name, startup validation, implicit transaction mechanics, per-test deployment, id and form rules that are only in the source. | Less code, more to know. |

## 11. Assessment

**It works.** The process runs unchanged in behaviour on Camunda 8.9, all former scenarios pass on a
real engine, and the application no longer contains a single engine call.

**What VanillaBP is good at in this example [C]:**

- The engine stops being a second data store. Personal data never reaches it, and what does is a
  tested whitelist.
- Service tasks, BPMN errors, retries and message correlation lose their plumbing.
- The user-task inbox is a database query instead of an eventually consistent search — although
  completing a task still waits for that search (limitation 4).
- Task bindings and use cases became plain, unit-testable code.

**What it costs [C]:**

- A dependency that dictates the Camunda client line and, for now, keeps the project off 8.9.
- A framework that wants to own the aggregate. In a hexagon that is a seam and a mapping layer, or a
  domain that is no longer free of persistence annotations.
- Platform features given up (Camunda Forms) and engine behaviour that has to be modelled explicitly
  (retries).
- Behaviour that is not in the documentation and has to be read from the source.

**Conclusion [I].** For this blueprint VanillaBP is suitable but not a clear win. The plain integration
is easier to explain and tracks Camunda releases immediately; the process-engine-api keeps the model
of working with variables and adds a thin abstraction. VanillaBP is the only one of the three that
changes where process state lives — that is its value and its price. It fits a service that already
treats one persistent record as the business case and does not need Tasklist forms. As a default for
the Miragon blueprints it would need a released adapter for the engine line in use; for CIB seven that
does not exist today.
