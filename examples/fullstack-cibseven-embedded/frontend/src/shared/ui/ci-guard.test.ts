import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Design-system guard: GREEN IS ACCENT-ONLY. `text-gruen` (and the generic
 * `text-accent`) fail WCAG AA as text colors and must never appear as a text
 * utility in any .tsx source file. This test greps the entire src tree and
 * fails if either forbidden substring is found.
 */
const SRC_DIR = join(__dirname, "..", "..");
const FORBIDDEN = ["text-gruen", "text-accent"];

function collectTsxFiles(dir: string): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === "node_modules" || entry.name.startsWith(".")) continue;
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...collectTsxFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith(".tsx")) {
      results.push(fullPath);
    }
  }
  return results;
}

describe("design-system CI guard", () => {
  it("never uses green as a text color in any .tsx file", () => {
    const files = collectTsxFiles(SRC_DIR);
    expect(files.length).toBeGreaterThan(0);

    const violations: string[] = [];
    for (const file of files) {
      const content = readFileSync(file, "utf8");
      for (const token of FORBIDDEN) {
        if (content.includes(token)) {
          violations.push(`${file} contains forbidden token "${token}"`);
        }
      }
    }

    expect(violations, violations.join("\n")).toEqual([]);
  });
});
