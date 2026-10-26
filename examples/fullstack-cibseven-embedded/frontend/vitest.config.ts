import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { "@": "/src" } },
  test: {
    globals: true,
    environment: "jsdom",
    // Unit tests are `*.test.*` under src/; the Playwright e2e `*.spec.ts` live in e2e/ and must not
    // be picked up by vitest.
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["src/shared/testing/setup.ts", "src/shared/testing/vitest.setup.ts"],
    css: true,
  },
});
