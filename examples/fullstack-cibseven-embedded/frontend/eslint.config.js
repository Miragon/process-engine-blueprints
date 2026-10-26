import js from "@eslint/js";
import tseslint from "typescript-eslint";
import globals from "globals";

/** The three FSD rules steiger doesn't cover, enforced as import restrictions. */
const PROCESSES_BAN = {
  group: ["**/processes", "**/processes/**", "@/processes", "@/processes/**"],
  message:
    "the FSD `processes` layer is banned — in this codebase 'process' means the BPMN model. use widgets/leasing-progress.",
};

const DEEP_CROSS_SLICE = {
  group: ["@/entities/*/**", "@/features/*/**", "@/widgets/*/**", "@/pages/*/**"],
  message: "import slices through their public index.ts, not a deep path.",
};

const GENERATED_CLIENT = {
  group: ["@/shared/api/generated", "@/shared/api/generated/**"],
  message:
    "only entities/*/api and features/*/api may touch the generated client; expose reads/writes through a slice's public API.",
};

export default tseslint.config(
  {
    ignores: ["dist", "coverage", "src/app/route-tree.gen.ts", "src/shared/api/generated/**"],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser },
    },
  },
  {
    // Default: the generated client is off-limits (widgets, pages, app go through slice APIs).
    files: ["src/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        { patterns: [PROCESSES_BAN, DEEP_CROSS_SLICE, GENERATED_CLIENT] },
      ],
    },
  },
  {
    // entities/api, features/api and shared may import the generated client.
    files: ["src/entities/**", "src/features/**", "src/shared/**"],
    rules: {
      "no-restricted-imports": ["error", { patterns: [PROCESSES_BAN, DEEP_CROSS_SLICE] }],
    },
  },
);
