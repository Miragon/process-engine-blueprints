# 0011 — Build and deployment approach: OCI image + Postgres via compose

- **Status:** Accepted
- **Date:** 2026-08-20

## Context

`stack/docker-compose.yml` started **only Postgres**. There was no artifact for the app itself, so the
"build & deployment" dimension every template in this family names was empty: a fork could run the dev
loop (`bootRun`) but had no answer to *"how do I ship this as a container?"*. The template aims to be
production-shaped ([ADR-0008](0008-track-the-latest-major-versions.md),
[ADR-0009](0009-actuator-probes-and-prometheus-metrics.md),
[ADR-0010](0010-flyway-for-database-migrations.md)), so it should hand a fork a runnable image, not
just a database.

The backend is a Spring Boot 4 app. Spring's Gradle plugin can build an OCI image directly from the fat
jar with Cloud Native Buildpacks — no Dockerfile to write or keep in sync with the JDK. Because this is
a headless service, the process engine and its web tooling are served by the same app on the same port,
so there is no separate web tier to package.

## Decision

We produce an **OCI image for the backend with Spring's `bootBuildImage`** (buildpacks, no Dockerfile)
and keep **Postgres for the dev loop in `stack/docker-compose.yml`**.

- **Backend image** — `./gradlew :service:app:bootBuildImage` builds `miravelo/app:<version>`
  (`bootBuildImage.imageName` in `service/app/build.gradle.kts`, JVM pinned via `BP_JVM_VERSION=21`).
  Buildpacks give a layered, non-root image with no Dockerfile to maintain. A hand-written Dockerfile
  would only be justified if we needed control buildpacks can't give; we don't.
- **Database** — `stack/docker-compose.yml` runs Postgres (named volume + `pg_isready` healthcheck) for
  the local dev loop. Because Flyway owns the schema and Hibernate only validates
  ([ADR-0010](0010-flyway-for-database-migrations.md)), the volume persists across restarts with no
  `ddl-auto` override.
- **Config is environment-overridable** (12-factor): `application.yaml` keeps dev defaults so local
  runs are unchanged, but every deploy-relevant value (datasource URL/credentials) is read from an env
  var that wins over the baked default.

The how-to (build the image, the podman socket note, run Postgres) is in
[CONTRIBUTING.md](../../CONTRIBUTING.md).

## Consequences

- **Positive:** `bootBuildImage` produces a runnable container for the app with no Dockerfile to
  maintain, and the dev-loop database comes up with one compose command. The build & deployment
  dimension is now filled.
- **Negative / trade-offs:** with **podman** the buildpack step needs a Docker-API socket
  (`podman system service` + `DOCKER_HOST`). The image is **not production-hardened** — it carries the
  admin/admin credentials from `application.yaml`, which a real deployment must override.
- **Neutral:** a CI job that builds the image or validates the compose is a natural follow-up, deferred
  for now.
</content>
