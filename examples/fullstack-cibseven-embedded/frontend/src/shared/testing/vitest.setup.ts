import { afterAll, afterEach, beforeAll, vi } from "vitest";
import { server } from "./msw-server";

// jsdom lacks the pointer/scroll APIs Radix primitives (Select, Dialog) probe; polyfill them so
// component interactions work in tests.
if (typeof window !== "undefined") {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();
  window.HTMLElement.prototype.hasPointerCapture = vi.fn(() => false);
  window.HTMLElement.prototype.releasePointerCapture = vi.fn();
  window.HTMLElement.prototype.setPointerCapture = vi.fn();
  if (!window.ResizeObserver) {
    window.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  }
}

// Start the MSW server for the whole test run; reset per test so `server.use(...)` overrides don't leak.
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
