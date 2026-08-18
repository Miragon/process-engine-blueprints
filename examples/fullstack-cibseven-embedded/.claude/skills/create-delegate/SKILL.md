---
name: create-delegate
argument-hint: "[<path-to-ProcessApi-or-BPMN-file>] [element-id-or-task-name]"
description: Create or update a CIB seven (Camunda 7) JavaDelegate — or an Execution/Task listener — in service/app for a BPMN service task or listener hook, wired as a Spring @Component and invoked by expression (#{beanName}). Use when the user asks to "create a delegate", "wire a service task to code", "add a JavaDelegate", or "add an execution/task listener". The delegate extends BaseDelegate, calls exactly one inbound port, and reads process variables via the typed ProcessApi.
allowed-tools: Read, Write, Glob
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
class ValidateApplicationDelegate(
    private val useCase: ValidateApplicationUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        try {
            useCase.validate(ApplicationId.of(execution.processBusinessKey))
        } catch (e: ApplicationInvalidException) {
            throw BpmnError("applicationInvalid", e.reason)   // maps to a BPMN error boundary event
        }
    }
}
```

- `BaseDelegate` wraps `executeTask` in a consistent log-and-rethrow; **extend it**, override
  `executeTask`, never implement `JavaDelegate.execute` directly.
- The **application id is the process business key** (`execution.processBusinessKey`), wrapped into the
  domain id (`ApplicationId.of(...)`). Other variables come via `execution.getVariable(name)`, and the
  variable **name must come from the typed ProcessApi**
  (`BikeLeasingProcessProcessApi.Variables.…`) — never a raw string literal.
- A **listener** (execution or task) is a plain `@Component` implementing `ExecutionListener` or
  `TaskListener` (see `ClarifyAlternativeTaskListener`, `BikeOrderAuditListener`) — no `BaseDelegate`.

## IMPORTANT — the rules ArchUnit enforces

- The delegate/listener injects **exactly one** inbound port (`*UseCase`/`*Query`) as its constructor
  parameter, or none for a pure audit listener.
- Naming suffixes allowed in `adapter.inbound.cibseven`: **`Delegate`**, **`Worker`**, **`Listener`** —
  nothing else. A service-task delegate is `*Delegate`; a hook is `*Listener`.
- The bean is referenced from the BPMN by expression, `camunda:delegateExpression="#{beanName}"` (or
  `camunda:taskListener`/`camunda:executionListener`). The bean name is the class name with a lowercase
  first letter (`ValidateApplicationDelegate` → `#{validateApplicationDelegate}`).
- Domain exceptions are translated to `BpmnError(errorCode, message)` where the model has an error
  boundary event; the `errorCode` **must match** the BPMN error definition.

## Instructions

### Step 1 — Load the BPMN and ProcessApi

From `$ARGUMENTS`: a `.bpmn` under `service/app/src/main/resources/bpmn/`, or a `*ProcessApi.kt` under
`adapter/process/`. Read both — the BPMN is the source of truth for the element, its
`delegateExpression`, input/output variables, and any attached error/timer boundary events; the
ProcessApi supplies the typed constants (`Variables.*`, `Elements.*`, `Messages.*`). If neither is
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

### Step 5 — Report and hand off

Report created/updated/skipped, then remind the user to:

1. Confirm the BPMN `delegateExpression`/listener expression matches the bean name — the
   [`review-process`](../../agents/review-process.md) subagent audits exactly this wiring.
2. Add a `@WebMvcTest`-style or engine (`process.*` JGiven) test; note the `cibseven.*` package is
   **excluded from the pitest kill-set** on purpose (thin glue, covered by the engine tests).
3. Run `./gradlew build`.
