import { describe, expect, it } from "vitest";
import { de } from "./de";
import { en } from "./en";

/**
 * Two fitness functions for the second locale:
 *  - structural parity: every locale must expose exactly the same key paths (tsc already enforces
 *    this via the `Copy` type, but this makes the guarantee explicit and framework-agnostic), and
 *  - English brand voice: same lowercase, no-em-dash tone as German (see de.tone.test.ts).
 */

const ALLOWLIST = ["MiraVelo", "BPMN", "BIKE-"];

function paths(value: unknown, prefix = ""): string[] {
  if (value && typeof value === "object") {
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
      paths(child, prefix ? `${prefix}.${key}` : key),
    );
  }
  return [prefix];
}

function leaves(value: unknown, path = ""): Array<{ path: string; text: string }> {
  if (typeof value === "string") return [{ path, text: value }];
  if (value && typeof value === "object") {
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
      leaves(child, path ? `${path}.${key}` : key),
    );
  }
  return [];
}

describe("locale parity", () => {
  it("en has exactly the same key paths as de", () => {
    expect(paths(en).sort()).toEqual(paths(de).sort());
  });

  it("no en leaf is left empty", () => {
    const empty = leaves(en).filter(({ text }) => text.trim() === "");
    expect(empty, `empty: ${empty.map((e) => e.path).join(", ")}`).toEqual([]);
  });
});

describe("en copy tone of voice", () => {
  const allLeaves = leaves(en);

  it("starts every string lowercase (allowlisted tokens excepted)", () => {
    const offenders = allLeaves.filter(({ text }) => {
      if (ALLOWLIST.some((token) => text.startsWith(token))) return false;
      const firstLetter = [...text].find((char) => /\p{L}/u.test(char));
      return firstLetter !== undefined && firstLetter !== firstLetter.toLowerCase();
    });
    expect(offenders, `uppercase starts: ${offenders.map((o) => o.path).join(", ")}`).toEqual([]);
  });

  it("never uses an em-dash", () => {
    const offenders = allLeaves.filter(({ text }) => text.includes("—"));
    expect(offenders, `em-dash in: ${offenders.map((o) => o.path).join(", ")}`).toEqual([]);
  });
});
