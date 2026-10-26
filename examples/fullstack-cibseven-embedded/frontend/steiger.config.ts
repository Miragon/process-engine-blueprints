import fsd from "@feature-sliced/steiger-plugin";
import { defineConfig } from "steiger";

/**
 * Feature-Sliced Design linting. The layering here is as load-bearing as the backend's hexagonal
 * rules, so it runs in CI with --fail-on-warnings. `no-processes`, `forbidden-imports`, `public-api`,
 * `no-public-api-sidestep`, `no-segmentless-slices`, `inconsistent-naming` and `segments-by-purpose`
 * stay ON. Three rules are disabled on purpose, each with a reason:
 */
export default defineConfig([
  ...fsd.configs.recommended,
  {
    rules: {
      // A compact blueprint references each entity/feature exactly once by design, so this rule's
      // "used more than once" heuristic yields only false positives here (and it under-counts
      // references made through a widget's ui segment). The discipline it protects — no phantom
      // slice without a backing read model — is enforced by the review-vertical-slice subagent.
      "fsd/insignificant-slice": "off",
      // The `application-*` prefix carries meaning (all views of one aggregate).
      "fsd/repetitive-naming": "off",
      // A design system is intentionally one file per component, not grouped by kind.
      "fsd/shared-lib-grouping": "off",
    },
  },
  {
    // Generated code and the router tree are not authored slices.
    ignores: ["./src/shared/api/generated/**", "./src/app/route-tree.gen.ts"],
  },
]);
