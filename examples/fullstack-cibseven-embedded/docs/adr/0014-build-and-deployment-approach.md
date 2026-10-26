# 0014 — Build and deployment approach: OCI image + one-command full stack

- **Status:** Accepted
- **Date:** 2026-08-20

## Context

`stack/docker-compose.yml` started **only Postgres**. There was no artifact for the app itself, so the
"build & deployment" dimension every template in this family names was empty: a fork could run the dev
loop (`bootRun` + Vite) but had no answer to *"how do I ship this as a container?"*. The template aims
to be production-shaped ([ADR-0011](0011-track-the-latest-major-versions.md),
[ADR-0012](0012-actuator-probes-and-prometheus-metrics.md),
[ADR-0013](0013-flyway-for-database-migrations.md)), so it should hand a fork a runnable image and a
one-command stack, not just a database.

Two forces shape the choice:

- **The backend** is a Spring Boot 4 app. Spring's Gradle plugin can build an OCI image directly from
  the fat jar with Cloud Native Buildpacks — no Dockerfile to write or keep in sync with the JDK.
- **The frontend** talks to the backend over **same-origin relative URLs** (`frontend/src/shared/api/
  http-client.ts`); in dev the Vite proxy forwards `/api`, `/engine-rest`, `/camunda`, `/v3/api-docs`
  to `:8080`. Any production packaging has to reproduce that single-origin topology, and it must not
  drag the frontend into the Gradle build ([ADR-0005](0005-frontend-stays-out-of-the-gradle-build.md)).

## Decision

We produce an **OCI image for the backend with Spring's `bootBuildImage`** (buildpacks, no Dockerfile),
ship the **frontend as an nginx image** that serves the built SPA and reverse-proxies the backend
paths, and add a **full-stack compose** (`stack/docker-compose.full.yml`) that runs
**frontend + app + Postgres** with one command.

- **Backend image** — `./gradlew :service:app:bootBuildImage` builds `miravelo/app:<version>`
  (`bootBuildImage.imageName` in `service/app/build.gradle.kts`, JVM pinned via `BP_JVM_VERSION=21`).
  Buildpacks give a layered, non-root image with no Dockerfile to maintain. A hand-written Dockerfile
  would only be justified if we needed control buildpacks can't give; we don't.
- **Frontend image** — a multi-stage `frontend/Dockerfile` builds the SPA with Node and serves `dist/`
  from `nginx:alpine`. `frontend/nginx.conf` reverse-proxies `/api`, `/engine-rest`, `/camunda`,
  `/v3/api-docs` to the `app` service and falls back to `index.html` for client-side routes. This is
  the **same single-origin, no-CORS topology as the Vite dev proxy**, and it keeps the frontend out of
  Gradle — the image is built by Docker/compose, not the Gradle build.
- **Full stack** — `stack/docker-compose.full.yml` wires the three services: Postgres (named volume +
  `pg_isready` healthcheck), the app image (datasource pointed at the compose Postgres via
  `SPRING_DATASOURCE_*`), and the frontend on `:8090`. Because Flyway owns the schema and Hibernate
  only validates ([ADR-0013](0013-flyway-for-database-migrations.md)), the volume persists across
  restarts with no `ddl-auto` override. The original `stack/docker-compose.yml` stays Postgres-only for
  the dev loop.
- **Config is environment-overridable** (12-factor): `application.yaml` keeps dev defaults so local
  runs are unchanged, but every deploy-relevant value (datasource URL/credentials) is read from an env
  var that wins over the baked default.

The how-to (build the image, the podman socket note, run the stack) is in
[CONTRIBUTING.md](../../CONTRIBUTING.md).

## Consequences

- **Positive:** `bootBuildImage` + `docker compose -f stack/docker-compose.full.yml up` brings up a
  runnable system — UI, engine, and DB — with no Dockerfile to maintain for the backend and no CORS on
  the request path. The build & deployment dimension is now filled.
- **Negative / trade-offs:** two build toolchains stay in play (Gradle buildpacks for the backend, a
  Docker build for the frontend); with **podman** the buildpack step needs a Docker-API socket
  (`podman system service` + `DOCKER_HOST`). The image is **not production-hardened** — it carries the
  dev `jwtSecret` and admin/admin credentials from `application.yaml`, which a real deployment must
  override.
- **Neutral:** the frontend nginx layer is deployment glue, not application code, so it stays outside
  the FSD slices and the Gradle build. A CI job that builds the image or validates the compose is a
  natural follow-up, deferred for now.
