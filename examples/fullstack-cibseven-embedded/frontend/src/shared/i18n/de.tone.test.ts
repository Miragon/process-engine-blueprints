import { describe, expect, it } from "vitest";
import { de } from "./de";

/**
 * The tone of voice is a fitness function, not a style guide nobody reads. Every copy leaf must obey
 * the MiraVelo brand voice, or the build fails: lowercase starts, no formal "Sie", proper umlauts
 * (never ae/oe/ue/ss transliterations), and no em-dashes. Brand/technical tokens are allowlisted.
 */

const ALLOWLIST = ["MiraVelo", "BPMN", "BIKE-"];

function leaves(value: unknown, path = ""): Array<{ path: string; text: string }> {
  if (typeof value === "string") return [{ path, text: value }];
  if (value && typeof value === "object") {
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
      leaves(child, path ? `${path}.${key}` : key),
    );
  }
  return [];
}

const allLeaves = leaves(de);

describe("de copy tone of voice", () => {
  it("has copy to check", () => {
    expect(allLeaves.length).toBeGreaterThan(50);
  });

  it("starts every string lowercase (allowlisted tokens excepted)", () => {
    const offenders = allLeaves.filter(({ text }) => {
      if (ALLOWLIST.some((token) => text.startsWith(token))) return false;
      const firstLetter = [...text].find((char) => /\p{L}/u.test(char));
      return firstLetter !== undefined && firstLetter !== firstLetter.toLowerCase();
    });
    expect(offenders, `uppercase starts: ${offenders.map((o) => o.path).join(", ")}`).toEqual([]);
  });

  it("never addresses the reader with the formal Sie", () => {
    const offenders = allLeaves.filter(({ text }) => /\bSie\b/.test(text));
    expect(offenders, `formal Sie in: ${offenders.map((o) => o.path).join(", ")}`).toEqual([]);
  });

  it("uses proper umlauts, not ae/oe/ue transliterations", () => {
    // A dictionary-free heuristic would flag legitimate words like "neuer"; instead deny the common
    // transliterations of words that carry an umlaut in this copy.
    const FORBIDDEN = [
      "fuer",
      "ueber",
      "zurueck",
      "waehl",
      "gueltig",
      "genueg",
      "uebernimm",
      "uebergabe",
      "koennen",
      "muessen",
      "groesse",
      "antraege",
      "loeschen",
      "moechte",
    ];
    const offenders = allLeaves.filter(({ text }) =>
      FORBIDDEN.some((bad) => text.toLowerCase().includes(bad)),
    );
    expect(offenders, `transliterations in: ${offenders.map((o) => o.path).join(", ")}`).toEqual(
      [],
    );
  });

  it("uses no em-dashes", () => {
    const offenders = allLeaves.filter(({ text }) => text.includes("—"));
    expect(offenders, `em-dash in: ${offenders.map((o) => o.path).join(", ")}`).toEqual([]);
  });
});
