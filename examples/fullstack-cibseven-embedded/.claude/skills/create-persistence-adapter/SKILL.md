---
name: create-persistence-adapter
argument-hint: "[<path-to-Repository-port-file-or-domain-aggregate>]"
description: Scaffold or update a JPA persistence adapter (outbound adapter) in service/app for a domain aggregate — entity, Spring Data repository, mapper, and the @Component adapter implementing the outbound Repository port. Use when the user asks to "create a persistence adapter", "generate a JPA adapter", "scaffold the persistence layer", or "add an outbound db adapter". Generated code obeys the hexagonal + naming ArchUnit rules.
allowed-tools: Read, Write, Glob
---

# Skill: create-persistence-adapter

Generate or update the persistence files for one aggregate in
`service/app/src/main/kotlin/io/miragon/blueprint/adapter/outbound/db/`. See
[ADR-0002](../../../docs/adr/0002-hexagonal-architecture-for-the-backend.md).

Files per aggregate:

- `*Entity.kt` — `@Entity` data class mapping the aggregate to a table.
- `*JpaRepository.kt` — Spring Data `JpaRepository` interface.
- `*EntityMapper.kt` — entity ↔ domain conversion. Used for a **rich** aggregate (see
  `LeasingApplicationEntityMapper`); for a **trivial** two-field aggregate the existing code inlines a
  private `toDomain()` on the adapter (see `BikePortfolioPersistenceAdapter`). Match the neighbour: rich
  aggregate → dedicated mapper `object`; trivial → inline is acceptable.
- `*PersistenceAdapter.kt` — `@Component` implementing the outbound port.

## IMPORTANT — the rules ArchUnit enforces

- The adapter implements **every** method of the outbound port and **that port only**.
- Naming suffixes allowed in `adapter.outbound`: `PersistenceAdapter`, `Adapter`, `Mapper`, `Entity`,
  `Repository`. The primary aggregate adapter is `*PersistenceAdapter`.
- The outbound port itself is named `*Repository` (that suffix is required in
  `application.port.outbound`).
- Column names are `snake_case`; enums use `@Enumerated(EnumType.STRING)` (never the ordinal).
- A dedicated mapper is a Kotlin **`object`** (singleton) — never a `class` or `companion object`.
- `find*` returns nullable / throws on absence per the port contract; `save` delegates to the repo.

## Instructions

### Step 1 — Resolve the aggregate

`$ARGUMENTS` may be a `*Repository` port under `application/port/outbound/`, or a domain aggregate under
`domain/{leasing,bike}/`. Read the port (interface name, all method signatures) and the aggregate (all
fields). Strip `Repository` to get the aggregate name. If neither is given, `Glob
**/application/port/outbound/*Repository.kt`, list, and ask.

### Step 2 — Target location

Package: `io.miragon.blueprint.adapter.outbound.db`. Source root: `service/app/src/main/kotlin/`.

### Step 3 — Determine should-state per file

Map each domain value object to its primitive for the entity (`BikeId(val value: UUID)` → `UUID`,
`Email(val value: String)` → `String`). Derive `snake_case` column and table names. The JPA repository
extends `JpaRepository<Entity, IdType>` and adds the `findBy…` query the port needs. The mapper (if
used) has `toDomain`/`toEntity`; the adapter delegates every port method through it (or through an
inline `toDomain` for a trivial aggregate).

For each file that already exists, read it and add only what's missing (a field, a query method, a port
method), preserving existing logic.

### Step 4 — Write the files

### Step 5 — Report and hand off

Report created/updated/skipped, then remind the user to add a schema migration if a new table is
introduced, add a `@DataJpaTest` slice test, and run `./gradlew build` (ArchUnit + pitest kill-set run
here). Wiring into the service is automatic — Spring component-scans `io.miragon.blueprint`.
