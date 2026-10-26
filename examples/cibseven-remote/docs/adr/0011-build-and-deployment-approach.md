# 0011 — Build and deployment approach: OCI images + one-command stack

- **Status:** Accepted
- **Date:** 2026-08-20

## Context

`stack/docker-compose.yml` started **only Postgres**. There was no artifact for the apps themselves, so
the "build & deployment" dimension every template in this family names was empty: a fork could run the
dev loop (`bootRun` the engine host, then the worker) but had no answer to *"how do I ship this as
containers?"*. The template aims to be production-shaped
([ADR-0008](0008-track-the-latest-major-versions.md),
[ADR-0009](0009-actuator-probes-and-prometheus-metrics.md),
[ADR-0010](0010-flyway-for-database-migrations.md)), so it should hand a fork runnable images and a
one-command stack, not just a database.

The shaping force is simple: **both deployable units are Spring Boot 4 apps** — the model-agnostic
engine host (`engine-service`) and the worker (`example-service`). This is a **headless** blueprint, so
there is no frontend to package, no SPA to serve, and no reverse proxy to stand up. Spring's Gradle
plugin can build an OCI image directly from each fat jar with Cloud Native Buildpacks — no Dockerfile to
write or keep in sync with the JDK.

## Decision

We produce an **OCI image per Spring Boot app with `bootBuildImage`** (buildpacks, no Dockerfile) and a
**compose stack** that runs **Postgres + engine-service + example-service** with one command.

- **Images** — `./gradlew :service:engine-service:bootBuildImage` and
  `./gradlew :service:example-service:bootBuildImage` build the two images (`bootBuildImage.imageName`
  in each module's `build.gradle.kts`, JVM pinned via `BP_JVM_VERSION=21`). Buildpacks give layered,
  non-root images with no Dockerfile to maintain. A hand-written Dockerfile would only be justified if we
  needed control buildpacks can't give; we don't.
- **Stack ordering** — the worker owns the process and **deploys the model into the engine at
  start-up**, so the compose stack starts Postgres first (named volume + `pg_isready` healthcheck),
  then the engine host, then the worker (which waits for the engine's `/engine-rest` to be ready).
  Datasources are pointed at the compose Postgres via `SPRING_DATASOURCE_*`. Because Flyway owns the
  worker's schema and Hibernate only validates ([ADR-0010](0010-flyway-for-database-migrations.md)), the
  volume persists across restarts with no `ddl-auto` override. The original `stack/docker-compose.yml`
  stays Postgres-only for the dev loop.
- **Config is environment-overridable** (12-factor): each `application.yaml` keeps dev defaults so local
  runs are unchanged, but every deploy-relevant value (datasource URLs/credentials, the engine base URL
  the worker uses) is read from an env var that wins over the baked default.

## Consequences

- **Positive:** `bootBuildImage` on both modules + `docker compose … up` brings up a runnable system —
  engine, worker, and DB — with no Dockerfile to maintain and no reverse proxy on the request path. The
  build & deployment dimension is now filled.
- **Negative / trade-offs:** with **podman** the buildpack step needs a Docker-API socket
  (`podman system service` + `DOCKER_HOST`). The images are **not production-hardened** — they carry the
  dev admin/admin credentials from `application.yaml`, which a real deployment must override.
- **Neutral:** a CI job that builds the images or validates the compose is a natural follow-up, deferred
  for now.
