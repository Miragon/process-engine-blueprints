import { DriftRule } from '../drift-rule.mjs';
import { positivePatterns } from '../drift-context.mjs';

export class ManifestPatternsMatchFiles extends DriftRule {
  check(context) {
    return context.manifestSections().flatMap(([section, entries]) => entries.flatMap((entry) => positivePatterns(entry)
      .filter((pattern) => context.filesMatching(entry, pattern).length === 0)
      .map((pattern) => `${section} in drift.yml: ${pattern} matches no file, remove it`)));
  }
}
