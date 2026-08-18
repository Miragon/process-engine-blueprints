---
name: automate-process
argument-hint: "[<path-to-ProcessApi-or-BPMN-file>]"
description: Generate full hexagonal glue-code for a CIB seven (Camunda 7) BPMN process across all layers — inbound JavaDelegates/listeners, inbound/outbound ports, application services, and the process out-adapter that drives the engine. Use when the user asks to "automate a process", "generate glue-code for a BPMN process", or "scaffold delegates and adapters". Accepts a ProcessApi file, a BPMN model, or a description; skips files that already exist; offers to run review-process first.
allowed-tools: Read, Write, Glob
---

# Skill: automate-process

Generate the hexagonal glue-code that wires a **CIB seven / Camunda 7** BPMN process (embedded engine)
to the application. This is not Zeebe — service tasks are JavaDelegates invoked by expression, and the
engine is driven through `RuntimeService`/`TaskService`. See
[ADR-0002](../../../docs/adr/0002-hexagonal-architecture-for-the-backend.md).

## What this creates

| Layer                | Location (`io.miragon.blueprint.…`)  | What |
| -------------------- | ------------------------------------ | ---- |
| Inbound delegates    | `adapter.inbound.cibseven`           | one `*Delegate : BaseDelegate` per service task (+ `*Listener` per listener hook) |
| Outbound adapter     | `adapter.outbound.cibseven`          | one `*ProcessAdapter` implementing the process out-port |
| Inbound ports        | `application.port.inbound`           | one `*UseCase`/`*Query` per delegate |
| Outbound port        | `application.port.outbound`          | one `*Process` interface (start + one method per message/user-task) |
| Application services  | `application.service`                | one `*Service` per inbound port |

## IMPORTANT — string constants come from the ProcessApi, never raw literals

- Delegate variable reads → `BikeLeasingProcessProcessApi.Variables.…`
- Adapter `startProcessInstanceByMessage(...)` → `Messages.…`; user-task completion →
  `Elements.…` + `Variables.…` (see `LeasingProcessAdapter`).
- The generated `adapter/process` `*ProcessApi` is the source of truth; regenerate it with
  `./gradlew generateBpmnModels` if the model changed but the API is stale.
- Delegate handler bodies take primitives / the business key and wrap them into domain value objects
  before calling the port; adapter methods accept **domain types** and extract `.value` inside.

## Instructions

### Step 0 — Offer the review first

Ask whether to run the [`review-process`](../../agents/review-process.md) subagent before generating —
it's worth generating code only for a clean, styleguide-conforming model. If the user agrees, tell them
to run it and **stop this skill**; they restart `automate-process` afterwards. If they decline, continue.

### Step 1 — Resolve the input source

`$ARGUMENTS` may be a `*ProcessApi.kt` (read directly) or a `.bpmn` (then `Glob
**/adapter/process/*ProcessApi.kt` in the same module). No argument → `Glob` all `*ProcessApi.kt`, list,
ask. If no ProcessApi exists, tell the user to run `./gradlew generateBpmnModels` first.

Extract: package, object name, `PROCESS_ID`, `Elements.*`, `Messages.*`, `Variables.*`.

### Step 2 — Packages

Base is fixed: `io.miragon.blueprint`. Delegates → `adapter.inbound.cibseven`; process adapter →
`adapter.outbound.cibseven`; ports → `application.port.{inbound,outbound}`; services →
`application.service`. Source root `service/app/src/main/kotlin/`.

### Step 3 — Discover / create domain types

Scan `domain/{leasing,bike}/` for a value object per process variable (e.g. `APPLICATION_ID` →
`ApplicationId`, `BIKE_ID` → `BikeId`). Create a missing one (a data class wrapping `UUID`/`String`)
before generating ports or adapters. Ask if a variable name doesn't map to a sensible type.

### Step 4 — Inbound ports

One `*UseCase` (state-changing) or `*Query` (read) per service task, method params in domain types.
Skip existing.

### Step 5 — Outbound process port

One `*Process` interface: a start method (this model starts by **message** — see
`Messages.MIRAVELO_LEASING_REQUEST_RECEIVED`), one method per remaining message, and one per user-task
completion the app drives. Domain-typed params. Skip existing.

### Step 6 — Application services

One `@Service` per inbound port, implementing exactly one port (ArchUnit enforces this), body
`TODO("Add implementation")`. Skip existing.

### Step 7 — Delegates

Per [`create-delegate`](../create-delegate/SKILL.md): `*Delegate : BaseDelegate`, inject the one port,
read the business key/variables via typed constants, translate domain exceptions to `BpmnError` where a
boundary event exists. Skip existing.

### Step 8 — Process out-adapter

Per the `LeasingProcessAdapter` pattern: `@Component … : XxxProcess`, inject `RuntimeService`/
`TaskService`, `override` each method, use typed `Messages`/`Elements`/`Variables`, correlate by
business key. Skip existing.

### Step 9 — Report

List every file created/skipped. Remind the user to fill the service `TODO`s, wire each BPMN
`delegateExpression` to its bean, run [`review-process`](../../agents/review-process.md) to check the
wiring, add tests, and `./gradlew build`.
