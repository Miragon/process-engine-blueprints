import { describe, expect, it } from "vitest";
import { isTerminal, LEASING_STATUSES, statusLabel, statusTone } from "./status";

describe("leasing status model", () => {
  it("treats ACTIVE, REJECTED and CANCELLED as terminal", () => {
    expect(isTerminal("ACTIVE")).toBe(true);
    expect(isTerminal("REJECTED")).toBe(true);
    expect(isTerminal("CANCELLED")).toBe(true);
  });

  it("treats RECEIVED and ORDERED as non-terminal", () => {
    expect(isTerminal("RECEIVED")).toBe(false);
    expect(isTerminal("ORDERED")).toBe(false);
  });

  it("treats missing status as non-terminal", () => {
    expect(isTerminal(undefined)).toBe(false);
    expect(isTerminal(null)).toBe(false);
  });

  it("has a German label and a badge tone for every status", () => {
    for (const status of LEASING_STATUSES) {
      expect(statusLabel(status)).toMatch(/\p{L}/u);
      expect(["neutral", "success", "warning", "danger", "info"]).toContain(statusTone(status));
    }
  });

  it("maps active to success and rejected to danger", () => {
    expect(statusTone("ACTIVE")).toBe("success");
    expect(statusTone("REJECTED")).toBe("danger");
  });
});
