# Migrating the CIB seven embedded example to Fluxnova

This blueprint is a port of
[`miragon-blueprints/cibseven-embedded-example`](https://github.com/miragon-blueprints/cibseven-embedded-example).
This document records what was changed, what was deliberately left alone, and what is still open — so
the next engine update, or the next port, starts from facts instead of archaeology.

**Result:** the complete bike-leasing process runs on an embedded Fluxnova 3.0.1 engine with the same
REST contract, the same process tests and the same end-to-end scenarios as the CIB seven baseline. One
upstream defect needed a workaround ([section 8](#8-blockers-workarounds-and-open-fluxnova-issues)).

<!-- variant:blueprint -->
The migration was done on the Kotlin + Gradle build, so the build files and Gradle commands named here
are those of `kotlin-gradle/`, and paths such as `service/app/…` are relative to a variant directory.
The Java + Maven variant carries the same decisions in its `pom.xml` files: the Jackson exclusion on
the two Fluxnova starters and a `maven-enforcer-plugin` rule in place of `verifyFluxnovaIsTheOnlyEngine`.
<!-- /variant:blueprint -->

## 1. Source and target versions

| | CIB seven baseline | Fluxnova blueprint |
|---|---|---|
| Source commit | [`9a55373`](https://github.com/miragon-blueprints/cibseven-embedded-example/commit/9a553739187d759de7b5c0eb97c672cf777934c2) (`main`, 2026-10-01) | — |
| Engine | CIB seven 2.2.0 | **Fluxnova 3.0.1** |
| Spring Boot | 4.1.1 | 4.1.1 |
| Kotlin / Java | 2.4.0 / 21 | 2.4.0 / 21 |
| Build | Gradle 9.7.1 | Gradle 9.7.1 |
| Database | PostgreSQL 18.6 (H2 in tests) | PostgreSQL 18.6 (H2 in tests) |

**Why Fluxnova 3.0.1.** It is the newest release on Maven Central and the first line built for Spring
Boot 4, Spring Framework 7 and Jackson 3 — the stack the baseline already runs on. Fluxnova 2.0.x
targets Spring Boot 3.5 and would have meant downgrading the whole blueprint. No snapshot and no
self-built artifact is used.

Two things to know about that choice:

- Fluxnova 3.0.1 is built upstream against **Spring Boot 4.0.5**. The blueprint keeps the baseline's
  Spring Boot 4.1.1; that combination is covered by this repository's test and Bruno suites, not by
  upstream's.
- A `v3.1.0` tag was created upstream on 2026-10-01 but was **not yet published** to Maven Central when
  this migration was done. It contains the fix for the workaround in section 8.

## 2. Dependency and package mapping

Maven coordinates (all verified on Maven Central):

| CIB seven | Fluxnova |
|---|---|
| `org.cibseven.bpm.springboot:cibseven-bpm-spring-boot-starter-webapp-4:2.2.0` | `org.finos.fluxnova.bpm.springboot:fluxnova-bpm-spring-boot-starter-webapp:3.0.1` |
| `org.cibseven.bpm.springboot:cibseven-bpm-spring-boot-starter-rest-4:2.2.0` | `org.finos.fluxnova.bpm.springboot:fluxnova-bpm-spring-boot-starter-rest:3.0.1` |
| `org.cibseven.bpm:cibseven-bpm-assert:2.2.0` | `org.finos.fluxnova.bpm:fluxnova-bpm-assert:3.0.1` |
| `org.cibseven.bpm:cibseven-bpm-junit5:2.2.0` | `org.finos.fluxnova.bpm:fluxnova-bpm-junit5:3.0.1` |
| `org.cibseven.community:cibseven-bpm-jgiven`, `jgiven-kotlin`, `jgiven-junit5` | removed — no test used them, and Fluxnova publishes no JGiven module |
| exclusion of `org.cibseven.webapp:cibseven-webclient-web` | removed — Fluxnova has no such module |
| — | exclusion of `tools.jackson.datatype:jackson-datatype-jsr310` (section 8) |

Fluxnova needs no extra Maven repository; everything resolves from Maven Central.

Java packages — a pure prefix swap, no API change was needed in any class:

| CIB seven | Fluxnova |
|---|---|
| `org.cibseven.bpm.engine.*` (`RuntimeService`, `TaskService`, `ProcessEngine`, `delegate.*`, `impl.cfg.*`) | `org.finos.fluxnova.bpm.engine.*` |
| `org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests` | `org.finos.fluxnova.bpm.engine.test.assertions.bpmn.BpmnAwareTests` |
| `org.cibseven.bpm.spring.boot.starter.rest.CamundaBpmRestJerseyAutoConfiguration` | `org.finos.fluxnova.bpm.spring.boot.starter.rest.FluxnovaBpmRestJerseyAutoConfiguration` |

Project names:

| CIB seven | Fluxnova |
|---|---|
| `rootProject.name = "cibseven-embedded-example"` | `fluxnova-embedded-example` |
| `CibsevenBikeLeasingApplication` | `FluxnovaBikeLeasingApplication` |
| `adapter.inbound.cibseven`, `adapter.outbound.cibseven` | `adapter.inbound.fluxnova`, `adapter.outbound.fluxnova` |
| OCI image `miravelo/cibseven-embedded-example` | `miravelo/fluxnova-embedded-example` |
| Bruno collection `cibseven-bike-leasing` | `fluxnova-bike-leasing` |

## 3. Configuration properties

| CIB seven | Fluxnova | Note |
|---|---|---|
| `camunda.bpm.*` | `fluxnova.bpm.*` | same keys below the prefix: `admin-user`, `deployment-resource-pattern`, `database.type`, `database.schema-update`, `job-execution.*`, `webapp.*` |
| `camunda.bpm.webapp.application-path: /camunda` | `fluxnova.bpm.webapp.application-path: /fluxnova` | `/fluxnova` is also Fluxnova's default |
| `cibseven.webclient.authentication.jwtSecret` | removed | belonged to the CIB seven webclient |
| auto-config exclusion in `application-test.yaml` | class renamed (section 2) | still excluded, because process tests start without a servlet context |

Environment variables are unchanged (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`).

URLs:

| | CIB seven | Fluxnova |
|---|---|---|
| Engine REST API | `/engine-rest` | `/engine-rest` (unchanged) |
| Webapp root | `/camunda` | `/fluxnova` |
| Cockpit | below `/camunda` | **`/fluxnova/app/monitoring/`** — Fluxnova renamed Cockpit to *Monitoring* |
| Tasklist · Admin | below `/camunda` | `/fluxnova/app/tasklist/` · `/fluxnova/app/admin/` |

## 4. BPMN, DMN and form changes

**No executable element changed.** Flow, element ids, messages, timers, error and escalation codes,
delegate expressions, listeners, the call activity, the DMN table and the form fields are byte-identical
to the baseline. Only model metadata and prose moved:

| File | Change |
|---|---|
| `bike-leasing.bpmn`, `cancel-bike-order.bpmn` | added `xmlns:fluxnova="http://fluxnova.finos.org/schema/1.0/bpmn"`; `xmlns:modeler` → `http://fluxnova.finos.org/schema/modeler/1.0`; `modeler:executionPlatform` → `Fluxnova Platform` `3.0.0`; `exporter` → `Fluxnova Modeler` `1.3.2` |
| `bike-leasing.bpmn`, `cancel-bike-order.bpmn` | prose only, in XML comments and `bpmn:documentation`: "Cockpit" → "Monitoring app", "Camunda Form" → "Fluxnova Form" |
| `check-credit-rating.dmn` | added the same modeler metadata; the decision's own `namespace` is kept |
| `clarify-alternative.form`, `clarify-return.form` | `executionPlatform` → `Fluxnova Platform`, `executionPlatformVersion` → `3.0.0` |

These are the values the official migrator (`migrator/src/main/resources/recipes/07_xmlBpmnMigration.yml`,
`08_xmlDmnMigration.yml`, `17_formMigration.yml` in `finos/fluxnova-bpm-platform`) and the Fluxnova
Modeler's own conversion write. `3.0.0` is the newest platform version Fluxnova Modeler 1.3.2 knows.

The `camunda:` extension attributes stay — see section 7.

## 5. Test changes

| Area | Change |
|---|---|
| All 140 baseline tests | kept; only engine imports and the adapter package name changed |
| `BikeLeasingProcessTest` (happy path, escalation, DMN rejection, abort with compensation, bike unavailable) | unchanged assertions, now on embedded Fluxnova |
| `ProcessDeploymentTest` | **new** — asserts the engine is Fluxnova and that both BPMN models, the DMN and the two forms are deployed |
| `verifyFluxnovaIsTheOnlyEngine` | **new** Gradle check, part of `./gradlew build` — fails when an `org.cibseven`, `org.camunda` or `org.operaton` artifact reaches the runtime or test classpath |
| `openapi/openapi.json` | re-exported; semantically identical, only the key order differs — it is now the order the Operaton blueprint has, too |
| Bruno collection | requests and assertions unchanged; only names and doc texts mention Fluxnova |

No test was disabled, deleted or weakened, and no wait condition was changed.

Verification runs for this migration (2026-10-01, JDK 21, macOS, Postgres 18.6):

| Check | CIB seven baseline | Fluxnova |
|---|---|---|
| `./gradlew build` | green, 140 tests | green, 144 tests |
| Bruno end-to-end (`bruno/`, 7 folders) | 45/45 requests, 80/80 assertions | 45/45 requests, 80/80 assertions |
| `./gradlew :service:app:pitest` | — (not re-run) | 86 % mutation score (gate 80) |
| `npm run lint:bpmn` | — (not re-run) | green |
| Restart with a waiting instance | — (not run) | instance survives the restart and completes |
| Webapps in a browser | — (not run) | Monitoring lists both definitions; Tasklist renders the `clarify-alternative` form |
| OCI image (`bootBuildImage`, via Podman) | — (not run) | image builds; the container starts against Postgres, reports engine 3.0.1 and health `UP` |
| GitHub Actions workflows | — | **not run** — the repository was not pushed; every command the workflows execute was run locally |

## 6. Database and transaction differences

- **Schema.** Both engines create 49 `ACT_*` tables on an empty Postgres database. Fluxnova 3.0.1 logs
  schema version `3.0.0` in `ACT_GE_SCHEMA_LOG` (CIB seven 2.2.0: `2.2.0`) and has three additional
  columns: `ACT_HI_ATTACHMENT.CREATED_BY_`, `ACT_HI_VARINST.RESTRICTED_` and `ACT_RU_VARIABLE.RESTRICTED_`.
- **No in-place switch.** Pointing Fluxnova at an existing CIB seven database was **not tested** and is
  not a path this blueprint supports. Start Fluxnova on an empty database. A dev database that still
  holds CIB seven tables must be reset with `docker compose -f stack/docker-compose.yml down -v`.
- **Schema management** is unchanged: the engine owns `ACT_*` through `schema-update: true`, Flyway owns
  the application tables, Hibernate validates.
- **Transactions.** No difference was observed. The `asyncBefore` / `asyncAfter` boundaries, the retry
  cycle `R3/PT10S`, the incident after exhausted retries, the manual retry, compensation and the
  message correlation behave as on CIB seven — the incident, abort and happy-path Bruno scenarios pass
  unchanged.
- **Persistence across restarts** was checked by hand: an instance waiting at the signature gateway was
  still there after an application restart, the deployment was not duplicated, and the instance ran to
  `COMPLETED`.
- **Supported databases.** Fluxnova 3.0.1 ships create scripts for PostgreSQL, H2, MySQL, MS SQL, Oracle
  and Db2. Only PostgreSQL and H2 are exercised here.

## 7. Compatibility namespaces that stay

Fluxnova is a Camunda 7 fork and still reads its executable BPMN extensions from the Camunda
namespace. In Fluxnova 3.0.1, `BpmnParse` reads almost every extension through
`http://camunda.org/schema/1.0/bpmn`; the `http://fluxnova.finos.org/schema/1.0/bpmn` namespace is only
consulted for extension elements and for new, Fluxnova-only features (restricted variables, ad-hoc
sub-process attributes). All BPMN files in the Fluxnova source tree use the Camunda namespace.

Moving the attributes into the Fluxnova namespace makes the models **not deployable**. Tried during
this migration: with the extension prefix bound to `http://fluxnova.finos.org/schema/1.0/bpmn`, the
engine rejects both models at start-up with `ENGINE-09005 Could not parse BPMN process` — it no longer
sees `historyTimeToLive` (`ENGINE-12018`) or the service tasks' `delegateExpression`.

Deliberately kept:

| Reference | Where | Why |
|---|---|---|
| `xmlns:camunda="http://camunda.org/schema/1.0/bpmn"` and all `camunda:*` attributes | `*.bpmn` | the namespace the engine parses |
| `xmlns:camunda="http://camunda.org/schema/1.0/dmn"`, `camunda:historyTimeToLive` | `check-credit-rating.dmn` | same, for DMN |
| `ProcessEngine.CAMUNDA_7` | `service/app/build.gradle.kts`, `BikeLeasingModelValidationTest` | `bpmn-to-code` 6.6.1 knows `ZEEBE`, `CAMUNDA_7` and `OPERATON`; `CAMUNDA_7` is its dialect for `camunda:` models |
| `BpmnEngine.CAMUNDA_7` | generated `*ProcessApi.kt` | written by `bpmn-to-code` |
| `plugin:camunda-compat/camunda-platform-7-24`, `camunda-bpmn-moddle` | `.bpmnlintrc` | lint rules and moddle descriptor for the same namespace |
| `org.camunda.bpm.model:camunda-bpmn-model`, `camunda-xml-model` 7.24.0 | test classpath only | transitive dependency of `bpmn-to-code-testing`; allow-listed in `verifyFluxnovaIsTheOnlyEngine` |
| "Camunda 7", "CIB seven" in prose | `README.md`, this file | origin and comparison |

## 8. Blockers, workarounds and open Fluxnova issues

**No blocker remained.** One upstream defect needed a workaround, and three smaller things are worth
knowing.

### Workaround: `jackson-datatype-jsr310:3.0.0-rc2`

- **Symptom.** `GET /api/bike-leasing/{id}` answered `500` with
  `java.lang.NoSuchFieldError: Class tools.jackson.databind.SerializationFeature does not have member
  field 'WRITE_DATES_AS_TIMESTAMPS'` as soon as a `LocalDateTime` without `@JsonFormat` was serialised.
- **Cause.** `org.finos.fluxnova.bpm:fluxnova-engine-rest-core-jakarta:3.0.1` depends on
  `tools.jackson.datatype:jackson-datatype-jsr310:3.0.0-rc2`. Spring's JSON converter discovers that
  module through the `ServiceLoader`; the pre-release references fields that Jackson 3.1 removed.
- **Upstream.** [finos/fluxnova-bpm-platform#244](https://github.com/finos/fluxnova-bpm-platform/issues/244),
  closed on 2026-08-28. The dependency is gone on `main` and in the `v3.1.0` tag; the published 3.0.1
  still has it.
- **Workaround.** The same change as upstream — the module is excluded in
  `service/app/build.gradle.kts`. Jackson 3 handles `java.time` natively, and the engine REST API does
  not use the module.
- **Remove when** the blueprint moves to Fluxnova 3.1.0 or later.

The baseline hid the same class of problem behind `@JsonFormat(shape = STRING)` on each date field.
Those annotations are kept, so the wire format stays pinned independent of the mapper.

### Worth knowing

- **Cockpit is called Monitoring.** `/fluxnova/app/cockpit/` answers `500` (no such app in the webjar);
  the app lives at `/fluxnova/app/monitoring/`. Fluxnova additionally develops a separate
  [Control Center](https://github.com/finos/fluxnova-control-center) (Angular + NestJS, talks to
  `/engine-rest`). It is not embedded and not part of this blueprint.
- **Webapp version label.** The footer of the Fluxnova 3.0.1 webapps shows `v2.0.0-SNAPSHOT`. That is a
  label in the upstream webjar; `/engine-rest/version` correctly reports `3.0.1`.
- **`OpenAPI` bean.** The baseline marked its `OpenAPI` bean `@Primary` because the CIB seven webapp
  registers one of its own. Fluxnova registers none; the annotation was removed.
- **Migrator recipe vs. Modeler.** The migrator's BPMN recipe writes the modeler namespace with a
  trailing slash (`…/modeler/1.0/`), the Fluxnova Modeler uses it without. This blueprint uses the
  Modeler's value.
- **Model metadata was written by hand.** The headers follow the migrator and Modeler sources, but the
  files were not opened and saved in the Fluxnova Modeler GUI.

## 9. Comparison with CIB seven and Operaton

Taken from the three blueprints as they are today (`cibseven-embedded-example@9a55373`,
`operaton-embedded-example@09b5928`, this repository):

| | CIB seven | Operaton | Fluxnova |
|---|---|---|---|
| Engine version | 2.2.0 | 2.1.5 | 3.0.1 |
| Governance | CIB | community (Operaton) | FINOS / Linux Foundation |
| Starter group | `org.cibseven.bpm.springboot` (`…-webapp-4`, `…-rest-4`) | `org.operaton.bpm.springboot` | `org.finos.fluxnova.bpm.springboot` |
| Java package | `org.cibseven.bpm` | `org.operaton.bpm` | `org.finos.fluxnova.bpm` |
| Property prefix | `camunda.bpm` | `operaton.bpm` | `fluxnova.bpm` |
| Webapp path | `/camunda` | `/operaton` | `/fluxnova` |
| Process monitoring UI | Cockpit | Cockpit | Monitoring |
| BPMN extension namespace in the blueprint | `camunda:` | `camunda:` | `camunda:` |
| `bpmn-to-code` dialect | `CAMUNDA_7` | `CAMUNDA_7` | `CAMUNDA_7` |
| Model metadata | Camunda Platform 7.24 | Camunda Platform 7.24 | Fluxnova Platform 3.0 |
| Build workaround | exclude `cibseven-webclient-web`, JWT secret required | none | exclude `jackson-datatype-jsr310` |
| Extra dev stack | — | EnterpriseGlue "The Bridge" | — |
| Java + Maven variant | yes (`java-maven` tag) | yes (`java-maven` tag) | yes (`java-maven/` on `main`) |

The domain, the application services, the REST controllers, the process models' flow, the Flyway
schema and the Bruno scenarios are the same in all three. What the comparison does **not** cover:
performance, clustering and upgrade paths between engine versions — none of the blueprints measures
those.

## 10. Maintaining and updating this blueprint

1. **Take Fluxnova 3.1.0 as soon as it is on Maven Central.** Bump `fluxnova_version`, delete the
   `jackson-datatype-jsr310` exclusion and its comment, run the gates below. Dependabot will open the
   bump; the exclusion has to be removed by hand.
2. **Run the full gate set on every engine or Spring Boot bump:**
   `./gradlew build`, `git diff --exit-code openapi/openapi.json`, `./gradlew :service:app:pitest`,
   `npm run lint:bpmn`, and the Bruno collection against a running app. The Bruno run is the one that
   catches webapp, Jackson and job-executor regressions — the JVM tests start without the webapp.
3. **Watch the Spring Boot gap.** Fluxnova 3.0.x is built against Spring Boot 4.0; this blueprint is on
   4.1. If a Spring Boot bump breaks the engine, pin `spring_version` to the line the current Fluxnova
   release is built against instead of patching around it.
4. **Keep `camunda:` in the models** until Fluxnova's parser reads the Fluxnova namespace for
   executable attributes. Then switch models, `.bpmnlintrc` and — once it has a Fluxnova dialect —
   the `bpmn-to-code` engine setting in one change.
5. **Never widen the allow-list of `verifyFluxnovaIsTheOnlyEngine`** to get a build green. A new
   foreign-engine artifact is a finding, not noise.
6. **Port improvements from the sibling blueprints.** The three embedded examples differ only in the
   places listed in section 9; a fix to the domain, the tests or the tooling in one of them usually
   applies here unchanged.
7. **Open point:** the model files should be opened and saved once in the Fluxnova Modeler to confirm
   the hand-written metadata.
