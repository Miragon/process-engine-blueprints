# The API contract

The frontend and backend meet at exactly one artefact: `openapi/openapi.json`. It is **generated
from the Kotlin controllers, committed, and drift-gated on both sides**. See
[ADR-0004](adr/0004-openapi-as-the-checked-in-contract.md).

## How it flows

```
Kotlin controllers ──springdoc──▶ /v3/api-docs ──OpenApiSpecExportTest──▶ openapi/openapi.json
                                                                                 │
                                                                    orval ◀──────┘
                                                                      │
                            src/shared/api/generated/ (TanStack Query hooks + zod + MSW handlers)
```

1. **springdoc** (`springdoc-openapi-starter-webmvc-ui:3.1.0` — the Spring Boot 4 line) serves the
   live spec at `/v3/api-docs`, limited to `/api/**` so the engine-internal `/engine-rest` stays out
   of the generated client. `OpenApiConfiguration` supplies the metadata bean.
2. **`OpenApiSpecExportTest`** (`@SpringBootTest(RANDOM_PORT)`) fetches the spec and writes
   `openapi/openapi.json` **deterministically**: keys sorted (`ORDER_MAP_ENTRIES_BY_KEYS`), a fixed
   two-space LF indenter, a trailing newline, and the volatile `servers` block (random test port)
   stripped — otherwise the drift gate would flap. It runs inside `./gradlew build`.
3. **orval** (`orval.config.ts`) reads the committed spec and emits, into
   `frontend/src/shared/api/generated/`: TanStack Query hooks (through the hand-written `httpClient`
   mutator), zod body schemas (reused by the submit form's resolver), and MSW handlers (which make
   the vitest integration tests nearly free).

## The two drift gates

- **Backend:** `git diff --exit-code openapi/openapi.json` after `./gradlew build`. If a controller
  change didn't update the committed spec, CI fails.
- **Frontend:** `npm --prefix frontend run api:check` regenerates the client and `git diff`s it. If
  the committed client is stale, CI fails.

To propagate a backend change, use the **`sync-api-client`** skill (regenerate spec → orval → fix
TypeScript across the FSD slices → both gates green).

## Two gotchas worth knowing

- **Swagger UI vs. the CIB seven webclient.** Both register resource handlers, and the CIB seven
  webapp also defines its own `OpenAPI` bean. This repo resolves it: our bean is marked `@Primary`
  so springdoc uses ours for `/api/**`, and `/camunda`, `/swagger-ui.html` and `/v3/api-docs` all
  coexist (verified at build time). If a future upgrade breaks that, swap to
  `springdoc-openapi-starter-webmvc-api` (spec only, no UI) — the frontend only needs `/v3/api-docs`.
- **Jackson 3 date-time.** Spring Boot 4 ships Jackson 3, which defaults `WRITE_DATES_AS_TIMESTAMPS`
  on (rendering `LocalDateTime` as a numeric array), and the CIB seven webapp serves `/api` with its
  own mapper that ignores global Jackson config. springdoc types those fields as `string/date-time`,
  so the DTO date fields are pinned with `@JsonFormat(shape = STRING)` to keep the payload and the
  contract in sync. Operation ids are set explicitly (`@Operation(operationId = …)`) so the generated
  hook names are clean and stable (e.g. `useListLeasingApplications`).
