# 0013 — Operaton as the target engine, on the Camunda-7 model namespace

- **Status:** Accepted
- **Date:** 2026-08-31

## Context

This blueprint automates the MiraVelo bike-leasing process on a **remote** engine driven through the
**external-task** pattern. The engine vendor is a choice: the Camunda 7 lineage now has several
community forks (CIB seven, Operaton, …) that share the BPMN model, the `/engine-rest` API and the
external-task-client contract, but ship under different Maven coordinates, Java packages and
config-property prefixes.

A **sibling blueprint** already exists for the *embedded* topology on
[Operaton](https://github.com/miragon-blueprints/operaton-embedded-example). Keeping the family on one
engine makes the four quadrants (embedded/remote × the two vendors) directly comparable, so a reader
can diff *topology* without also diffing *vendor*.

Two facts shape the decision:

- Operaton 2.x is already a **Spring Boot 4 / Spring Framework 7 / Java 17** platform — the newest
  major line, matching the rest of this stack ([ADR-0008](0008-track-the-latest-major-versions.md)).
  No framework downgrade is needed.
- Operaton 2.1.x still **parses the Camunda-7 (`camunda:`) BPMN/DMN namespace**. Operaton also
  publishes its own `operaton:` namespace, but the model, the `bpmn-to-code` generator target
  (`ProcessEngine.CAMUNDA_7`) and the "service task must be an external task with a topic" validation
  rule all key off `camunda:` today.

## Decision

We target **Operaton** (pinned to `operaton_version` in `gradle/libs.versions.toml`) as the remote
engine, driven through the external-task pattern, mirroring the embedded sibling's conventions:
`org.operaton.bpm.springboot:*` starters, `org.operaton.bpm:*` engine/test libraries, the
`org.operaton.bpm.client.*` external-task client, `operaton.bpm.*` config properties, and the
Cockpit/Tasklist at `/operaton`.

We **keep the `.bpmn`/`.dmn`/`.form` models on the Camunda-7 (`camunda:`) namespace** and
`bpmn-to-code` on `ProcessEngine.CAMUNDA_7`. Operaton parses these unchanged, so the model stays a
zero-diff asset shared with the sibling blueprints; a migration to the native `operaton:` namespace is
deferred until the toolchain (notably `bpmn-to-code`) supports it first-class.

## Consequences

- **Positive:** the model, topic names, business-key logic and the typed `*ProcessApi` are identical to
  the sibling blueprints, so only the engine wiring differs; the port is a coordinate/import/config swap
  with no behaviour change, and no Docker image swap (the engine host is the `engine-service` Spring Boot
  app).
- **Negative / trade-offs:** the models read `camunda:` in an Operaton project — a cosmetic mismatch we
  accept for zero churn and toolchain compatibility; if Operaton ever drops Camunda-namespace
  compatibility, a one-time OpenRewrite migration to `operaton:` becomes necessary.
- **Neutral:** the `/engine-rest` base path is unchanged, so the generated engine client
  ([`common-operaton-client`](../../service/common-operaton-client/README.md)) and the hand-built
  multipart deployment adapter carry over as-is.
