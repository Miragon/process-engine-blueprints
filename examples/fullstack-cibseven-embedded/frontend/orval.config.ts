import { defineConfig } from "orval";

/**
 * Generates the frontend's typed client from the committed backend contract (`../openapi/openapi.json`).
 * Everything under `src/shared/api/generated/` is generated and committed; `npm run api:check`
 * regenerates it and `git diff --exit-code`s the result, so the client can never drift from the spec.
 *
 * Three artefacts are produced:
 *  - `endpoints`  — TanStack Query hooks + query-key helpers, every request routed through the
 *                   hand-written `httpClient` mutator (shared/api/http-client.ts).
 *  - `endpoints.msw` — MSW handlers, which make the integration tests nearly free.
 *  - `zod`        — zod schemas per operation; the submit form reuses the request-body schema as its
 *                   resolver, so the form and the wire format cannot drift.
 */
export default defineConfig({
  endpoints: {
    input: { target: "../openapi/openapi.json" },
    output: {
      target: "src/shared/api/generated/endpoints.ts",
      schemas: "src/shared/api/generated/model",
      client: "react-query",
      mode: "single",
      mock: true,
      clean: true,
      prettier: false,
      override: {
        mutator: { path: "src/shared/api/http-client.ts", name: "httpClient" },
        // Return the response body directly instead of an { status, data, headers } wrapper, so
        // consumers read `useListLeasingApplications().data` (the page) rather than `.data.data`.
        fetch: { includeHttpResponseReturnType: false },
      },
    },
  },
  zod: {
    input: { target: "../openapi/openapi.json" },
    output: {
      target: "src/shared/api/generated/zod.ts",
      client: "zod",
      mode: "single",
      fileExtension: ".ts",
      prettier: false,
    },
  },
});
