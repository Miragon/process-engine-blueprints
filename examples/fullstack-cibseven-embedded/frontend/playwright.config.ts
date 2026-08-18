import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests live in ./e2e and drive the real React app against a running backend + engine.
 * They sit inside the frontend package so Node resolves @playwright/test from frontend/node_modules.
 * Ports here must agree with the README / AGENTS.md port table: Vite 5173, backend 8080.
 * The dev server is started by CI (or locally) before `npm run e2e`; set PLAYWRIGHT_BASE_URL to override.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["html", { open: "never" }], ["list"]] : "list",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:5173",
    trace: "on-first-retry",
  },
  // pre-merge runs chromium; the nightly cross-browser job selects firefox/webkit via --project.
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
});
