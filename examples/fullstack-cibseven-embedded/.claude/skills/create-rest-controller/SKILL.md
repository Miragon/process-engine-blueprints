---
name: create-rest-controller
argument-hint: "[<path-to-UseCase-or-Query-port-file>] [http-verb] [path]"
description: Scaffold or update a Spring @RestController (inbound adapter) in service/app for one inbound port, with nested request/response DTOs and a toCommand() mapping. Use when the user asks to "create a REST controller", "add an endpoint", "expose a use-case over REST", or "scaffold an inbound adapter". Accepts a UseCase/Query port file or a description; the generated controller obeys the hexagonal + naming ArchUnit rules and carries an @Operation(operationId) so the OpenAPI client method is stable.
allowed-tools: Read, Write, Glob
---

# Skill: create-rest-controller

Generate or update **one** `@RestController` (inbound REST adapter) for a specific inbound port in
`service/app/src/main/kotlin/io/miragon/blueprint/adapter/inbound/rest/`. One endpoint per class. See
[ADR-0002](../../../docs/adr/0002-hexagonal-architecture-for-the-backend.md) for the rules this obeys
and [ADR-0004](../../../docs/adr/0004-openapi-as-the-checked-in-contract.md) for why `operationId`
matters.

## Pattern (this repo)

```kotlin
@RestController
@RequestMapping("/api/bike-leasing")
class SubmitLeasingRequestController(
    private val useCase: SubmitLeasingRequestUseCase,
) {
    @Operation(operationId = "submitLeasingRequest")
    @PostMapping
    fun submit(@RequestBody input: LeasingRequestInput): ResponseEntity<LeasingApplicationCreatedDto> {
        val id = useCase.submit(input.toCommand())
        return ResponseEntity.ok(LeasingApplicationCreatedDto(id.value.toString()))
    }

    data class LeasingRequestInput(val customerName: String, /* … */)
    data class LeasingApplicationCreatedDto(val applicationId: String)

    private fun LeasingRequestInput.toCommand() =
        SubmitLeasingRequestUseCase.Command(customerName = CustomerName(customerName), /* … */)
}
```

A **read** controller injects a `*Query` and maps to a `*Dto` inline (see `ListBikesController`).

## IMPORTANT — the rules ArchUnit enforces

- **One endpoint (method) per controller class**, and the port interface is the **only** constructor
  parameter (`in adapters should only offer one use-case or query`).
- Class name = port name with `UseCase`/`Query` → `Controller`. Suffixes allowed in
  `adapter.inbound.rest`: `Controller`, `Dto`, `Input`, `Mapper`, `Configuration` — nothing else.
- **Wrap every request field in its domain value object inside `toCommand()`** before calling the port.
  Scan `domain/{leasing,bike}/` first; if a value object is missing, ask before creating it.
- Request/response types are `data class` **nested inside** the controller. Request → `*Input`,
  response → `*Dto`.
- Always return `ResponseEntity<T>` — `ResponseEntity<Void>` when the port returns `Unit`.
- **Always add `@Operation(operationId = "…")`** (camelCase, matching the port action) — orval derives
  the generated client's hook name from it, so a stable id keeps the frontend stable.

## Instructions

### Step 1 — Resolve the input port

From `$ARGUMENTS`: if a port file under `application/port/inbound/` is given, read it — extract package,
interface name, method signature(s), the nested `Command` fields (if any), and the return type. If no
argument, `Glob **/application/port/inbound/*{UseCase,Query}.kt`, list the results, and ask which one.

### Step 2 — Locate the target

Base package is fixed: `io.miragon.blueprint`. Controller package:
`io.miragon.blueprint.adapter.inbound.rest`. Source root:
`service/app/src/main/kotlin/`.

### Step 3 — Verb and path

Use `$ARGUMENTS` if given. Otherwise infer: `Submit`/`Create`/`Report`/`Sign` → `POST`;
`Withdraw`/`Cancel`/`Select` → `POST` (state change via body, matching the existing controllers);
`Get`/`List` (a `*Query`) → `GET`. Derive a path under `/api/…` consistent with the existing endpoints
(`/api/bike-leasing`, `/api/bikes`, `/api/leasing-applications`). Confirm verb + path with the user.

### Step 4 — Generate or update

New file → write the controller per the pattern, creating any missing domain value object first (never
leave a TODO for a domain type). Existing file → read it, add only what's missing (a field, the return
type, a `toCommand()` correction), preserve existing logic.

### Step 5 — Report and hand off

Report created/updated/skipped, then remind the user to:

1. Run the OpenAPI + client loop with the [`sync-api-client`](../sync-api-client/SKILL.md) skill so the
   new endpoint reaches `openapi/openapi.json` and the frontend client — **the spec is drift-gated in
   CI**, so a new endpoint that isn't regenerated will fail the build.
2. Add a `@WebMvcTest` slice test and run `./gradlew build` (runs the ArchUnit suite too).
