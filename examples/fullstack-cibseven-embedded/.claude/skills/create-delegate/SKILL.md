---
name: create-delegate
argument-hint: "[<path-to-ProcessApi-or-BPMN-file>] [element-id-or-task-name]"
description: Create or update a CIB seven (Camunda 7) JavaDelegate — or an Execution/Task listener — in service/app for a BPMN service task or listener hook, wired as a Spring @Component and invoked by expression (#{beanName}). Use when the user asks to "create a delegate", "wire a service task to code", "add a JavaDelegate", "add an execution/task listener", "test a delegate", or when the mutation gate reports a surviving mutant in a delegate. The delegate extends BaseDelegate, calls exactly one inbound port, reads process variables via the typed ProcessApi, and ships with its MockK unit test.
allowed-tools: Read, Write, Edit, Glob, Bash(./gradlew *)
---

# Skill: create-delegate

Generate one `@Component` **JavaDelegate** (or a listener) in
`service/app/src/main/kotlin/io/miragon/blueprint/adapter/inbound/cibseven/` for a BPMN element. This
is CIB seven (the community fork of Camunda 7) with an **embedded engine** — service tasks call
**JavaDelegates by expression**, they are **not** Zeebe job workers. See
[ADR-0002](../../../docs/adr/0002-hexagonal-architecture-for-the-backend.md).

## Pattern (this repo)

```kotlin
@Component
class OrderBikeDelegate(
    private val useCase: OrderBikeUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        val orderId = try {
            useCase.orderBike(ApplicationId.of(execution.processBusinessKey))
        } catch (e: BikeUnavailableException) {
            throw BpmnError(Errors.BIKE_UNAVAILABLE.code, e.message)   // maps to a BPMN error boundary event
        }
        execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value, orderId.value)
    }
}
```

- `BaseDelegate` wraps `executeTask` in a consistent log-and-rethrow; **extend it**, override
  `executeTask`, never implement `JavaDelegate.execute` directly.
- The **application id is the process business key** (`execution.processBusinessKey`), wrapped into the
  domain id (`ApplicationId.of(...)`). Other variables come via `execution.getVariable(name)`, and the
  variable **name must come from the typed ProcessApi**
  (`BikeLeasingProcessProcessApi.FlowNodes.<Node>.Variables.…`) — never a raw string literal.
- A **listener** (execution or task) is a plain `@Component` implementing `ExecutionListener` or
  `TaskListener` (see `ClarifyAlternativeTaskListener`, `BikeOrderAuditListener`) — no `BaseDelegate`.

## Test pattern

Every delegate gets a MockK unit test in
`service/app/src/test/kotlin/io/miragon/blueprint/adapter/inbound/cibseven/`. Delegates are in the
mutation gate ([ADR-0006](../../../docs/adr/0006-mutation-testing-as-a-blocking-pr-gate.md)); only
the log-only listeners are excluded.

```kotlin
class SendContractDelegateTest {

    private val useCase = mockk<SendContractUseCase>()
    private val execution = mockk<DelegateExecution>()
    private val underTest = SendContractDelegate(useCase)

    @Test
    fun `sends the contract of the application in the business key`() {
        val applicationId = ApplicationId.new()
        every { execution.processBusinessKey } returns applicationId.value.toString()
        every { useCase.sendContract(applicationId) } just Runs

        underTest.execute(execution)

        verify { useCase.sendContract(applicationId) }
        confirmVerified(useCase)
    }
}
```

- **Call `execute`, not `executeTask`**, so `BaseDelegate` runs as in the engine. Its rethrow is
  covered once in `BaseDelegateTest`; don't repeat it per delegate.
- **Strict mocks.** Stub with `every { … } just Runs` (or `returns`), then `verify` the call with the
  exact id and `confirmVerified(useCase)`. A relaxed mock lets a removed call survive the gate.
- **One test per outcome:** the happy path, plus each `BpmnError` the delegate throws — assert the code
  from the generated `Errors`, never a string literal (see `OrderBikeDelegateTest`).
- **Variables** read or written are stubbed and verified with the names from
  `<Process>ProcessApi.FlowNodes.<Node>.Variables`, never raw strings.
- **No Spring, no engine.** Plain JUnit 5 + MockK + AssertJ, backtick names, `underTest`.

## IMPORTANT — the rules ArchUnit enforces

- The delegate/listener injects **exactly one** inbound port (`*UseCase`/`*Query`) as its constructor
  parameter, or none for a pure audit listener.
- Naming suffixes allowed in `adapter.inbound.cibseven`: **`Delegate`**, **`Worker`**, **`Listener`** —
  nothing else. A service-task delegate is `*Delegate`; a hook is `*Listener`.
- The bean is referenced from the BPMN by expression, `camunda:delegateExpression="#{beanName}"` (or
  `camunda:taskListener`/`camunda:executionListener`). The bean name is the class name with a lowercase
  first letter (`OrderBikeDelegate` → `#{orderBikeDelegate}`).
- Domain exceptions are translated to `BpmnError(errorCode, message)` where the model has an error
  boundary event; the `errorCode` **must match** the BPMN error definition.

## Instructions

### Step 1 — Load the BPMN and ProcessApi

From `$ARGUMENTS`: a `.bpmn` under `service/app/src/main/resources/bpmn/`, or a `*ProcessApi.kt` under
`adapter/process/`. Read both — the BPMN is the source of truth for the element, its
`delegateExpression`, input/output variables, and any attached error/timer boundary events; the
ProcessApi supplies the typed constants (`FlowNodes.<Node>.id`, `FlowNodes.<Node>.Variables.*`, plus the
shared top-level `Messages.*` / `ServiceTasks.*` / `ProcessVariables.*`). If neither is
given, `Glob **/bpmn/*.bpmn` and `**/adapter/process/*ProcessApi.kt`, list, and ask.

### Step 2 — Identify the element

From the BPMN, pick the service task (or listener hook) by the id/name in `$ARGUMENTS` (else list and
ask). Extract its `delegateExpression` bean name, its input variables, and any error/escalation
boundary event it must throw into.

### Step 3 — Find the inbound port

`Glob **/application/port/inbound/*{UseCase,Query}.kt` for the matching operation. If missing, ask
whether to generate it (or run [`automate-process`](../automate-process/SKILL.md) for the whole
process). Class name = PascalCase of the action + `Delegate` (or `Listener`).

### Step 4 — Generate or update

New file → write the delegate extending `BaseDelegate`, reading the business key / variables via typed
constants, calling the single port, translating domain exceptions to `BpmnError` where a boundary event
exists. Listener → implement `ExecutionListener`/`TaskListener`. Existing file → add only what's
missing, preserve logic.

### Step 5 — Test it twice

1. The unit test (see Test pattern), then the mutation check for the class:

   ```bash
   ./gradlew :service:app:test --tests "*<Name>DelegateTest"
   ./gradlew :service:app:pitest -PmutationTargetClasses=io.miragon.blueprint.adapter.inbound.cibseven.<Name>Delegate
   ```

   A surviving mutant means a missing assertion: add it, don't weaken the gate.
2. The path through the engine with a process test in
   `service/app/src/test/kotlin/io/miragon/blueprint/adapter/process/`.

### Step 6 — Report

Report created/updated/skipped and confirm the BPMN `delegateExpression`/listener expression matches
the bean name — the [`review-process`](../../agents/review-process.md) subagent audits exactly this
wiring.
