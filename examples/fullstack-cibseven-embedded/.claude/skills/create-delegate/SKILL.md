---
name: create-delegate
argument-hint: "[element-id or task name] [DelegateClassName]"
description: "Create, update or test a JavaDelegate or an execution/task listener for a BPMN element on CIB seven (Camunda 7 fork), wired as a Spring @Component and invoked by expression (#{beanName}), together with its unit test and the process test of its path. Use when the user asks to create a delegate, wire a service task to code, add a JavaDelegate or listener, or test an existing delegate. A delegate extends BaseDelegate, calls exactly one inbound port, and reads process variables through the typed ProcessApi."
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Skill
---

# Skill: create-delegate

One `@Component` per piece of work in `adapter/inbound/cibseven/` (package `io.miragon.blueprint`
under `service/app/src/main/`), plus its tests; one bean may serve several elements
(`sendCancellationConfirmationDelegate`). CIB seven runs **embedded**; service tasks call
**JavaDelegates by expression**, not external task workers. The rules are in `AGENTS.md` (Process
Glue, Architecture); this skill does not repeat them.

## Production pattern

Copy the shape of the existing classes instead of inventing one:

| Case | Reference |
|---|---|
| a delegate that calls a port and nothing else | `SendContractDelegate` |
| reads a variable, writes one back, maps a domain exception to a `BpmnError` | `OrderBikeDelegate` |
| a task listener | `ClarifyAlternativeTaskListener` |
| an execution listener | `BikeOrderAuditListener` |

- A delegate extends `BaseDelegate` and overrides `executeTask`; a listener implements
  `TaskListener` / `ExecutionListener` directly.
- The BPMN references the bean by expression, `#{beanName}` (class name with a lowercase first
  letter): `camunda:delegateExpression` on a service task or message event, `delegateExpression` inside
  `camunda:taskListener` / `camunda:executionListener`. The model test
  `ServiceTaskDelegateExpressionRule` only requires service tasks to use an expression; a wrong bean
  name surfaces in the process test.
- Suffix `Delegate`, `Worker` or `Listener`; at most one inbound port (the architecture tests fail
  above one). A delegate calls exactly one; a listener may call none.

## Test it twice

**1. A unit test** in the test tree's `adapter/inbound/cibseven/<Name>Test`, one test per outcome:
the happy path (the exact port call with the id from the business key, every variable written
back), each `BpmnError` the delegate throws (the code from the generated `Errors`, never a string
literal), and for a listener the effect it has. No Spring, no engine; verify the port call and that
nothing else was called; call `execute`, not `executeTask`, so `BaseDelegate` runs as in the engine.
Same style as the service tests (`OrderBikeServiceTest`): `underTest`, given/when/then comments.

Copy the existing tests: `OrderBikeDelegateTest` (a variable read and written back, a `BpmnError`),
`SendContractDelegateTest` (a port call and nothing else). The rethrow of `BaseDelegate` is covered
once in `BaseDelegateTest`; don't repeat it per delegate.

**2. A process test** for the path through the engine in the test tree's `adapter/process/` (see
`BikeLeasingProcessTest`): it walks the path the element sits on and verifies the port call there.
The unit test proves the mapping, the process test proves the wiring (bean name, variables, boundary
events). Delegates are outside the mutation gate, which covers the domain only, so the
unit test is the only isolated check of the mapping.

## Instructions

1. **Load** the model in `service/app/src/main/resources/bpmn/` and the generated
   `BikeLeasingProcessProcessApi` (or `CancelBikeOrderProcessApi`). The BPMN is the source of truth for
   the element, its `delegateExpression`, its variables and its boundary events.
2. **Identify the element** from `$ARGUMENTS`, else list the service tasks and listeners and ask.
3. **Find the inbound port** in `application/port/inbound/` (`*UseCase`/`*Query`). If it is missing,
   create port and service first, or run skill `automate-process` when the model changes too.
4. **Write the unit test first**, then the delegate; for an existing class add only what is missing.
5. **Add or extend the process test** for the path.
6. **Run** both (`AGENTS.md` → Verify After Each Task): the unit test by name, then the
   `io.miragon.blueprint.adapter.process` tests.
